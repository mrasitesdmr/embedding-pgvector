---
name: observability-tracing
description: Spring Boot 4 servisinde OpenTelemetry + Micrometer Tracing ile distributed tracing'i Jaeger'a göndermek, loglara traceId/spanId eklemek, structured (JSON) loglama, MDC ve async/WebSocket thread'leri arasında trace context propagation. Loglama yapısı kurulurken, Jaeger veya OTLP yapılandırılırken, özel span/observation eklenirken, log formatı veya log seviyeleri belirlenirken ya da "trace Jaeger'da görünmüyor" sorunu çözülürken kullan.
license: MIT AND Apache-2.0
metadata:
  sources:
    - https://github.com/HoangNguyen0403/agent-skills-standard/tree/main/skills/spring-boot/spring-boot-observability
    - https://github.com/jabrena/plinth/tree/main/skills/183-java-observability-tracing-opentelemetry
  adapted-for: Spring Boot 4.1 (property isimleri spring-boot-4.1.1 jar metadata'sından doğrulandı) + Jaeger v2
---

# Observability: Tracing (Jaeger) ve Loglama

> **Proje durumu:** Tracing ve structured (JSON) loglama projede henüz kurulmadı: `pom.xml`'de OpenTelemetry/Micrometer Tracing dependency'si, Compose'da Jaeger yok; loglar Spring Boot'un varsayılan düz metin formatındadır. Bugün geçerli olan sadece log mesajı kuralıdır: İngilizce cümle + `key=value` alanları, sorgu metni ve parça içeriği loglanmaz (`backend-conventions` bölüm 4; ör. `Ingestion job finished jobId={} sourceId={} replacedChunks={} newChunks={} durationMs={}`). Aşağıdakiler bu altyapıyı getiren task'ın planı için hedeftir; dependency'ler o planda eklenir.

Akış: uygulama → Micrometer Observation → OpenTelemetry SDK → OTLP (HTTP, 4318) → Jaeger → UI (16686). Loglar her satırda aynı `traceId`/`spanId`'yi taşır; Jaeger'daki trace ile log satırı bu ID üzerinden eşleşir.

## 1. Dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
<!-- Sadece @Observed / @NewSpan annotation'ları kullanılacaksa -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

`spring-boot-starter-opentelemetry`, `micrometer-tracing-bridge-otel` ve `opentelemetry-exporter-otlp`'yi getirir. Boot 4'te `spring-boot-starter-aop` yoktur; adı `spring-boot-starter-aspectj`'dir. Ayrı Jaeger exporter'ı ekleme (deprecated); Jaeger OTLP'yi doğrudan kabul eder.

## 2. Yapılandırma (`application.properties`)

```properties
spring.application.name=embedding-pgvector

management.tracing.sampling.probability=1.0
management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces
management.otlp.metrics.export.enabled=false
management.observations.annotations.enabled=true
```

`application-docker.properties` / prod profile'ına ek olarak: `logging.structured.format.console=ecs`

- `spring.application.name` Jaeger'da servis adı olur.
- `sampling.probability` varsayılanı `0.1`'dir; dev/test'te `1.0`, production'da `0.1` civarı. Profile'a göre ayır.
- Transport varsayılanı `http` (4318). gRPC için `management.opentelemetry.tracing.export.otlp.transport=grpc` ve port `4317`.
- Jaeger metrics kabul etmez; OTLP metrics export'u kapat (aksi halde `localhost:4318/v1/metrics` hatası loglanır).
- Docker Compose'da endpoint `http://jaeger:4318/v1/traces` olur; env ile override et: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`.
- Testlerde export'u kapat: `management.tracing.export.enabled=false`.

## 3. Jaeger (lokal / compose)

```yaml
jaeger:
  image: jaegertracing/jaeger:2.22.0
  ports:
    - "16686:16686"
    - "4318:4318"
```

Compose dosyası `deployment/` altındadır ve devops agent'ının sorumluluğundadır; bu skill sadece uygulama tarafını kapsar.

## 4. Log korelasyonu ve format

- Tracing aktifken Boot, konsol log pattern'ine `traceId` ve `spanId`'yi otomatik ekler (`logging.pattern.correlation`). Elle `%X{traceId}` pattern'i yazma.
- Structured logging Boot'ta yerleşiktir: `logging.structured.format.console=ecs` (veya `logstash`, `gelf`). `logstash-logback-encoder` ekleme.
- Lokal geliştirmede okunabilirlik için structured format'ı sadece `prod`/`docker` profile'ında aç.

## 5. Loglama kuralları

- Logger: `private static final Logger log = LoggerFactory.getLogger(SearchService.class);` (Lombok yok).
- Parametreli log: `log.info("Search completed workspaceId={} mode={} hitCount={} durationMs={}", workspaceId, mode, hitCount, durationMs);` — string birleştirme yok.
- Mesajlar İngilizce, `key=value` alanlarıyla.
- Seviyeler: `ERROR` = müdahale gerekir (exception ile birlikte logla), `WARN` = beklenmeyen ama tolere edilen, `INFO` = iş olayı (kaynak eklendi, içe alma tamamlandı), `DEBUG` = geliştirme detayı. Döngü içinde `INFO` yok.
- Exception'ı ya logla ya fırlat; ikisini birden yapma (tekrar eden stack trace). Global handler'da bir kez logla.
- Asla loglama: secret (embedding API anahtarı), token, tam request/response body, kullanıcının yazdığı sorgu metni, parça içeriği, embedding vektörü. Sorgu için sadece uzunluğunu (`queryLength`) logla.
- `System.out.println` ve `printStackTrace()` yasak.
- MDC'ye ek alan koyarsan (`sourceId` gibi) `try/finally` ile temizle; trace ID'leri MDC'ye elle koyma.

## 6. Context propagation (thread sınırları)

HTTP request'leri otomatik trace edilir. Thread değiştiğinde context kaybolmaması için:

```java
@Configuration(proxyBeanMethods = false)
class ContextPropagationConfig {

    @Bean
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
```

Boot, `TaskDecorator` bean'ini auto-configure edilen `applicationTaskExecutor`'a uygular. Bu executor `@Async` metotları **ve STOMP inbound/outbound kanalları** tarafından kullanılır; böylece WebSocket handler'larında da log korelasyonu korunur. Kendi `ThreadPoolTaskExecutor`'ını tanımlarsan decorator'ı ona da elle set et.

## 7. Özel span / observation

Otomatik instrumentation (HTTP server, `RestClient`, JDBC) yetmiyorsa iş akışına anlamlı span ekle:

```java
@Observed(name = "search.chunks", contextualName = "search-chunks", lowCardinalityKeyValues = {"feature", "search"})
public SearchResultResponse searchChunks(long workspaceId, SearchChunksRequest request) {
    ...
}
```

Programatik:

```java
Observation.createNotStarted("embedding.generate", observationRegistry)
        .lowCardinalityKeyValue("model", embeddingProperties.model())
        .observe(() -> embeddingClient.embed(query));
```

- Span adları düşük kardinaliteli olsun (`search.chunks`, `search.chunks.ws42` değil); arama modu (`KEYWORD`/`VECTOR`/`HYBRID`) gibi az değerli alanlar `lowCardinalityKeyValue` olabilir.
- Kaynak id'si gibi yüksek kardinaliteli değerleri `highCardinalityKeyValue` ile ekle.
- PII, secret, payload'ı span attribute'una koyma.
- `@Observed` için `spring-boot-starter-aspectj` ve `management.observations.annotations.enabled=true` gerekir (varsayılanı `false`); ikisi varsa Boot `ObservedAspect`'i auto-configure eder.

## Doğrulama checklist'i

```
- [ ] Uygulama açılışında OTLP export hatası yok
- [ ] Bir HTTP isteği sonrası log satırında traceId/spanId var
- [ ] Aynı traceId Jaeger UI'da (http://localhost:16686) servis adıyla görünüyor
- [ ] @Async / WebSocket handler loglarında traceId kaybolmuyor
- [ ] Log'larda secret / payload yok
```

## Sorun giderme

- **Trace görünmüyor**: endpoint path'i `/v1/traces` ile bitiyor mu, port 4318 mi, sampling 0 mı, `management.tracing.export.enabled=false` mı?
- **Log'da traceId boş**: istek tracing dışı bir thread'de işleniyor; bölüm 6'yı uygula.
- **`/v1/metrics` bağlantı hatası**: `management.otlp.metrics.export.enabled=false`.

## Referans

Span modelleme, semantic convention'lar, sampling, baggage ve tracing testleri için ayrıntılı rehber (vendor-agnostic OpenTelemetry): [references/opentelemetry-tracing.md](references/opentelemetry-tracing.md). Bu dosyadaki Spring Boot yapılandırması referans dosyadakiyle çelişirse bu dosya geçerlidir.
