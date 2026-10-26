# SpringDoc OpenAPI Sorun Giderme

## Parametre İsimleri Görünmüyor (`arg0`, `arg1`)

`spring-boot-starter-parent` maven-compiler-plugin'i zaten `<parameters>true</parameters>` ile yapılandırır; projede ayrıca bir şey gerekmez. Parent kullanılmayan bir modülde görülürse:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

## Swagger UI "Unable to render definition" Gösteriyor

- `/v3/api-docs` ham çıktısını aç; JSON yerine string içinde JSON (çift tırnaklı, escape edilmiş) dönüyorsa message converter sırası bozulmuştur. `WebMvcConfigurer.configureMessageConverters` ile converter listesini **değiştirme**; gerekiyorsa `extendMessageConverters` kullan ve `ByteArrayHttpMessageConverter`'ı listede bırak.
- Global bir `ResponseBodyAdvice` tüm response'ları bir zarfa sarıyorsa `/v3/api-docs` path'ini hariç tut.

## Endpoint'ler Dokümantasyonda Görünmüyor

1. Path filtresi endpoint'i kapsıyor mu?
   ```properties
   springdoc.paths-to-match=/api/**
   ```
2. Package filtresi doğru mu? (`springdoc.packages-to-scan=com.embedding.pgvector`)
3. Controller veya metot `@Hidden` ile işaretli mi?
4. Controller, `EmbeddingPgvectorApplication`'ın paketinin (`com.embedding.pgvector`) altında mı? Değilse component scan bulmaz.

## Spring Security Eklenince Swagger'a Erişilemiyor

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().authenticated())
```

## Build / Sürüm Sorunları

- Açılışta `NoSuchMethodError` / `ClassNotFoundException` (`org.springframework...`): SpringDoc sürümü Boot sürümüyle uyumsuzdur. Boot 4.1 → SpringDoc 3.1.x ([dependency-setup.md](dependency-setup.md)).
- WebMVC projesine `springdoc-openapi-starter-webflux-*` eklenmez.

## Javadoc'tan Açıklama Gelmiyor

Beklenen davranıştır: projede metot/sınıf üstüne Javadoc yazılmaz (`backend-conventions`) ve `therapi-runtime-javadoc` eklenmez. Açıklamalar `@Operation(description = ...)` ve `@Schema(description = ...)` ile verilir.

## Alanlar Beklenen İsimle Görünmüyor

SpringDoc şemayı uygulamanın Jackson yapılandırmasından üretir (Boot 4'te Jackson 3). Alan adı değiştiren `@JsonProperty`, `@JsonIgnore` gibi anotasyonlar şemaya yansır. Kendi `ObjectMapper`/`JsonMapper` bean'ini tanımlama; Boot'un auto-configure ettiği `JsonMapper`'ı `spring.jackson.*` property'leri veya `JsonMapperBuilderCustomizer` ile ayarla — aksi halde hem API hem doküman Boot varsayılanlarını kaybeder.

## Generic Tiplerde Şema Bozuk

`PageResponse<SourceResponse>` gibi generic dönüşlerde SpringDoc içerik tipine özel şema üretir (`PageResponseSourceResponse`). Tip bilgisi kayboluyorsa (ör. `ResponseEntity<?>` dönüşü) dönüş tipini somut yaz veya `@ApiResponse(content = @Content(schema = @Schema(implementation = ...)))` ver.

## Hata Response'ları Yanlış Belgeleniyor

Projeye özel `ErrorResponse` sınıfı açma; hata şeması `ProblemDetail` + `errorCode` extension'ıdır ([exception-handling.md](exception-handling.md), `problem-details-rfc9457`).

## Production'da Swagger Açık Kalmış

```properties
# application-prod.properties
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

## Debug İpuçları

1. Ham dokümanı kontrol et: `curl localhost:8080/v3/api-docs.yaml`
2. Debug log: `logging.level.org.springdoc=DEBUG`
3. Sözleşme dosyasını doğrula: [Swagger Editor](https://editor.swagger.io/) veya `npx @redocly/cli lint docs/api/openapi.yaml`
4. Kod ↔ sözleşme farkı için [build-integration.md](build-integration.md)'deki sözleşme testi

## Açılış Süresi

Doküman ilk istekte üretilir ve cache'lenir. Açılış/ilk istek yavaşsa tarama kapsamını daralt (`packages-to-scan`, `paths-to-match`) ve gerekiyorsa API'yi `GroupedOpenApi` ile gruplara böl ([advanced-configuration.md](advanced-configuration.md)).
