TASK: TASK-001
# Plan

## Özet
Mevcut durum: `backend/`, `frontend/`, `deployment/` altında sadece `CLAUDE.md` var; `pom.xml` yok; `docs/api/openapi.yaml` boş (`paths: {}`); `ollama.md` model adı olarak `bge-m3` örneğiyle yazılmış, "5. Backend üzerinden istek atma" bölümü boş.

Yeni durum:
1. Spring Boot 4.1.1 / Java 26 / Maven wrapper backend iskeleti kurulur (kök paket `com.embedding.pgvector`).
2. Tek endpoint: `POST /api/embeddings`. Gelen metni kırpar, doğrular, Ollama'nın `/api/embed` uç noktasına gönderir ve `EmbeddingResponse(embeddingModel, dimensions, embedding, durationMs)` döner. Hiçbir şey kaydedilmez.
3. Ollama hataları iki ayrı `errorCode` ile 503 döner: `EMBEDDING_UNAVAILABLE`, `EMBEDDING_MODEL_NOT_FOUND`. Validation hataları `VALIDATION_FAILED` (400).
4. `ollama.md` onaylanan modele göre ve gerçek endpoint ile güncellenir (backend-coder). Kural 11'in cevabı **Hayır (kullanıcı kararı: "Docker sürecini kaldır. Docker gerek yok")**: Ollama Docker'da çalıştırılmaz, `deployment/` altında dosya açılmaz.

Varsayılan model **`bge-m3`** (kartta soru 1; kullanıcı başka model seçerse sadece `embedding.model` varsayılanı ve `ollama.md`'deki model adları değişir, kod ve testler değişmez).

Bu plan karttaki iki sorunun önerilen cevaplarına göre yazıldı: S1=A (`bge-m3`), S2=A (Swagger UI var). Başka cevap gelirse etkilenen bölümler "Soru cevaplarına göre değişenler"de.

Silinen mevcut parça: `ollama.md`'deki "## 6. Docker ile çalıştırma (alternatif)" bölümü (kullanıcı kararı); sonraki bölüm "7. Sık karşılaşılan hatalar" → "6. Sık karşılaşılan hatalar" olur. Bu plandaki rehber bölüm numaraları yeni numaralandırmaya göredir: 1 Kurulum, 2 Sunucuyu başlatma, 3 Modeli indirme, 4 Doğrudan Ollama'ya istek, 5 Backend üzerinden istek, 6 Sık karşılaşılan hatalar.

## Çalışacak roller
- **planner** (bu plan)
- **backend-coder**: iskelet, endpoint, Ollama istemcisi, `ollama.md` (Docker bölümünün çıkarılması dahil), `backend/CLAUDE.md`.
- **tester**: backend unit, controller, client ve integration testleri.
- **qa**: kural kontrolü, sözleşme uyumu, `ollama.md` komutlarının canlı Ollama ile denenmesi. qa doğrudan tester'a bağlıdır (kök `CLAUDE.md` iş akışı madde 2).
- Atlanan: **frontend-coder** (TASK'ta arayüz kapsam dışı, Frontend katmanı işaretli değil).
- Atlanan: **devops** (kullanıcı Kural 11'e "hayır" dedi: Ollama Docker'da çalıştırılmaz; backend imajı ve compose TASK'ta zaten kapsam dışı).

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | Ollama lokal, ücretli servis yok, API anahtarı yok | `OllamaEmbeddingClient` sadece `embedding.base-url`'e gider; properties'te anahtar yok | T6 (istek `embedding.base-url`'deki `/api/embed`'e gider); qa: `application.properties` ve kodda anahtar ya da dış embedding URL'si yok |
| K2 | Çok dilli model, karşılaştırma ve öneri kartta, kullanıcı onaylar | Kart S1; `embedding.model` varsayılanı onaylanan model | Kart onayı; qa: canlı istekte `dimensions` onaylanan modelin boyutu (bge-m3 → 1024) |
| K3 | Model adı ve Ollama adresi yapılandırmadan; varsayılan `http://localhost:11434` | `EmbeddingProperties` + `application.properties` (`OLLAMA_BASE_URL`, `EMBEDDING_MODEL` env) | T6 (model adı istekte yapılandırmadan), T19 (base-url testte stub'a çevriliyor); qa: varsayılan değerler |
| K4 | `ollama.md` güncellenir, backend bölümü dolar (Docker bölümü: kullanıcı "hayır" dedi, kesinleşmez) | backend-coder: model adları, bölüm 3-6, Docker bölümünün çıkarılması | qa: rehberdeki komutları sırayla canlı çalıştırır (Q1-Q4); rehberde Docker bölümü ve Docker atfı kalmamış |
| K5 | Yanıtta model adı, boyut, vektör, süre (ms) | `EmbeddingService.embedText`, `EmbeddingResponse` | T1, T10, T17b, T19 |
| K6 | Boş / sadece boşluk metin boş sayılır; baş-son boşluk kırpılır | `EmbedTextRequest` compact constructor (`strip()`) + `@NotBlank` | T3, T4, T11, T12, T13, T20, T21 |
| K7 | En fazla 2000 karakter | `@Size(max = 2000)` (kırpılmış metne) | T14, T15, T16 |
| K8 | Türkçe karakterler bozulmadan modele gider | Jackson UTF-8 JSON (backend girişi ve Ollama isteği) | T6, T20 |
| K9 | Ollama'ya ulaşılamaz / model yok → ayrı `errorCode`, backend açık kalır | `OllamaEmbeddingClient` hata eşlemesi, `GlobalExceptionHandler` | T7, T8, T8b, T9, T9b, T10b, T18, T22, T23 |
| K10 | Model, boyut, süre loga; metin ve vektör loga yazılmaz | `EmbeddingService.embedText` tek INFO log | T2 |
| K11 | Docker isteğe bağlı; kullanıcıya sorulur | Lead kullanıcıya sordu, cevap: Hayır. Uygulanmaz: Ollama container'ı yok, devops çalışmaz | Test yok (uygulanmaz); qa: `deployment/` altında yeni dosya yok |
| S-1 | Rehberle kurulum, terminalden doğrudan Ollama isteği, vektör görülür | `ollama.md` bölüm 1-4 | qa Q1 |
| S-2 | Backend'e aynı metin → model, boyut (1024), vektör, süre | Endpoint | T19; qa Q2 |
| S-3 | Aynı metin iki kez → aynı vektör | Backend metni değiştirmeden, rastgelelik eklemeden iletir | T5 (aynı istek gövdesi), qa Q3 (canlı modelle iki yanıt aynı) |
| S-4 | `"  modem ışığı kırmızı yanıyor  "` kırpılarak embed edilir | `EmbedTextRequest` | T3, T20 |
| S-5 | Boş / `"   "` → boş metin hatası, Ollama'ya istek yok | `@NotBlank` + `@Valid` controller'da | T11, T12, T21 |
| S-6 | 2001 karakter → çok uzun hatası | `@Size` | T14 |
| S-7 | Ollama kapalı → ulaşılamıyor; açılınca restart'sız başarılı | Client her istekte yeni çağrı yapar, hata durumu tutulmaz | T7, T9, T22 |
| S-8 | Model indirilmemiş → model bulunamadı | Ollama 404 → `EMBEDDING_MODEL_NOT_FOUND` | T8, T18, T23; qa Q4 |
| AC-1 | `ollama.md` güncel, komutlar çalışıyor | backend-coder | qa Q1-Q4 |
| AC-2 | `./mvnw verify` geçiyor | Tüm modül | tester + qa komut çalıştırır |
| AC-3 | Onaylanan modelin boyutunda vektör; model adı, boyut, süre var | Endpoint | T19; qa Q2 |
| AC-4 | K6, K7, K9 hataları sözleşmedeki `errorCode` ile RFC 9457 | `GlobalExceptionHandler` | T11-T18 (content type `application/problem+json`, `errorCode`, `type`) |
| AC-5 | Endpoint `openapi.yaml`'da, backend birebir uyumlu | `EmbeddingController`, DTO'lar, `OpenApiConfig` | T24; qa: `/v3/api-docs.yaml` ↔ `docs/api/openapi.yaml` karşılaştırması (S2=Evet), aksi halde kod okuma |
| AC-6 | Testler gerçek Ollama olmadan geçiyor | Testler base-url'i her zaman stub'a ya da kapalı porta çevirir | qa: `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` geçer; testlerde `11434` geçmez |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | Tam 2000 karakter; ya da 2000 karakter + baş/son boşluk | Kabul edilir; sınır kırpılmış metne uygulanır | T15, T16 |
| G2 | Sekme / satır sonu ile çevrili metin; metnin içinde çift boşluk | Baştaki/sondaki sekme ve satır sonu da kırpılır; içteki boşluklar aynen kalır | T3, T4 |
| G3 | `text` alanı hiç yok / `null` / gövde bozuk JSON | `text` yok/null → 400 `VALIDATION_FAILED` (`field=text`); bozuk JSON → 400 `BAD_REQUEST`; Ollama çağrılmaz | T13, T17 |
| G4 | Ollama bağlantıyı kabul edip cevap vermiyor (takılma) | `embedding.read-timeout` (60 s) dolunca 503 `EMBEDDING_UNAVAILABLE`; istek sonsuza kadar asılı kalmaz | T9 |
| G5 | Ollama 200 döner ama `embeddings` boş/yok ya da 500 döner | 503 `EMBEDDING_UNAVAILABLE`; Ollama'nın hata gövdesi `detail`'e kopyalanmaz | T8b, T10b |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | Endpoint `POST /api/embeddings`, başarıda `200`, workspace altında değil | Kaynak oluşturmaz (arama gibi `POST` + 200, `backend-conventions` §3); bu task'ta workspace yok | `POST /api/embed`: path'te fiil; `GET ?text=`: 2000 karakter URL'de, metin loglara/proxy'ye düşer |
| D2 | Vektörün kendisi bu endpoint'in yanıtına konur | TASK K5 açıkça istiyor; `backend-conventions` "vektör response'a konmaz" kuralına bu endpoint için TASK gereği istisna | Sadece boyut dönmek: K5'i ihlal eder |
| D3 | Kırpma `EmbedTextRequest` compact constructor'ında `String.strip()` ile; validation kırpılmış değere uygulanır; içteki boşluk korunur | Jackson record'u canonical constructor ile kurar, validation zaten kırpılmış alanı görür; 2000 sınırı embed edilen metne uygulanır | Service'te kırpmak: `@Size` ham metne uygulanır, `"  " + 2000 karakter` yanlışlıkla reddedilir; `trim()`: Unicode boşlukları kaçırır |
| D4 | Uzunluk `String.length()` (UTF-16 birimi) ile sayılır | İleride frontend'in JS `length`'iyle aynı sonucu verir; Türkçe harfler 1 sayılır | Code point saymak: frontend ile ayrışır |
| D5 | Boş ve çok uzun metin aynı `errorCode` `VALIDATION_FAILED` + `violations[{field:"text"}]`; mesajlar sabit İngilizce: `must not be blank`, `size must be at most 2000 characters` | Proje standardı (Bean Validation → `VALIDATION_FAILED`); sabit `message`, JVM locale'i Türkçe olsa bile Türkçe metin üretilmesini engeller | `TEXT_EMPTY` / `TEXT_TOO_LONG` ayrı kodlar: standarttan sapar, frontend alan kural mesajını zaten alandan üretir |
| D6 | Model indirilmemiş → `503 EMBEDDING_MODEL_NOT_FOUND` (`ServiceUnavailableException`) | İstemcinin isteği doğru; sorun sunucu tarafında ve model indirilince geçer | 404: endpoint/kaynak yok gibi okunur; 500: sebebi gizler |
| D7 | Ollama yanıt kodu eşlemesi: 404 → `EMBEDDING_MODEL_NOT_FOUND`; diğer tüm 2xx-dışı, bağlantı hatası, timeout, boş `embeddings` → `EMBEDDING_UNAVAILABLE` | K9'daki iki durumu ayırır, diğer her şey "servise ulaşılamıyor" | Ollama'nın hata metnini parse etmek: sürüme bağlı, kırılgan |
| D8 | Retry ve circuit breaker yok; her istek Ollama'ya yeni çağrı | S-7: Ollama açılınca restart'sız çalışır; hata hemen döner | Spring Retry / Resilience4j: yeni dependency, kapalı serviste yanıtı geciktirir; circuit breaker açılınca S-7'yi bozar |
| D9 | Süre backend'de ölçülür: `clock.millis()` Ollama çağrısından önce ve sonra (`Clock` bean'i) | `Clock` kuralı; testte `Clock` mock'u ile deterministik (1000 → 1085 = 85 ms) | Ollama'nın `total_duration`'ı: nanosaniye, HTTP süresini içermez, Ollama'ya bağımlı; `System.nanoTime`: testte sabitlenemez |
| D10 | HTTP istemcisi: Spring `RestClient` + `JdkClientHttpRequestFactory` (connect/read timeout) | Ek dependency yok; senkron MVC ile uyumlu | Spring AI Ollama starter: büyük dependency, eğitimde göstermek istediğimiz isteği gizler; `WebClient`: reaktif yığın gereksiz |
| D11 | `embedding.connect-timeout=2s`, `embedding.read-timeout=60s` | Kapalı sunucu hemen hata; ilk istekte model belleğe yüklenirken (birkaç sn) zaman aşımı olmaz | Varsayılan sınırsız read timeout: takılan Ollama isteği asılı bırakır |
| D12 | `embeddingModel` yanıtta yapılandırılmış model adıdır; `dimensions` = dönen vektörün uzunluğu; `EmbeddingProperties`'te `dimensions` alanı yok | Bu task'ta veritabanı yok; model yapılandırmadan değişince boyut da kendiliğinden doğru olur | `dimensions` property'si + eşleşme kontrolü: veritabanı task'ında kolon boyutuyla birlikte eklenir |
| D13 | Env değişken adları: `OLLAMA_BASE_URL`, `EMBEDDING_MODEL` (`${OLLAMA_BASE_URL:http://localhost:11434}` biçiminde) | Relaxed binding'de `base-url` ↔ env adı belirsizliği yok; rehberde yazılabilir | Relaxed binding'e güvenmek (`EMBEDDING_BASEURL`): kullanıcı için anlaşılmaz |
| D14 | Testlerde Ollama, JDK'nın `com.sun.net.httpserver.HttpServer`'ı ile yazılan `OllamaStubServer` test utility'si ile taklit edilir | Ek dependency yok; gerçek bağlantı reddi, gerçek timeout ve "kapalıyken başlat" senaryosu (S-7) gerçek HTTP ile test edilir | WireMock / MockWebServer: yeni dependency; `MockRestServiceServer`: gerçek bağlantı reddi ve timeout'u simüle etmez |
| D15 | `exception` paketinde sadece `BaseException`, `ServiceUnavailableException`, `GlobalExceptionHandler` | Kullanılmayan kategori sınıfı ölü koddur; kategoriler ihtiyaç duyan task'ta eklenir (`problem-details-rfc9457`) | Şablondaki tüm kategorileri açmak: kullanılmayan kod |
| D16 | 503 için log atılmaz; sadece 500'de handler loglar | `problem-details-rfc9457` / `backend-conventions` log kuralı | Client'ta WARN log + fırlatma: çift log |
| D17 | `ollama.md`'yi tamamen backend-coder günceller; Docker bölümünü çıkarır ve bölüm numaralarını düzeltir | TASK K4 rolü planner'a bırakıyor; endpoint'i ve curl çıktısını backend-coder bilir; Docker kullanıcı kararıyla kapsam dışı, devops çalışmıyor | Docker bölümünü "bu task'ta yok" notuyla bırakmak: kullanıcı Docker sürecinin kaldırılmasını istedi; qa'ya yazdırmak: qa dosya yazmaz |
| D19 | `config/OpenApiConfig`: `OpenApiCustomizer` bean'i `/v3/api-docs` çıktısını sözleşmeye eşitler (backend-coder sorusu üzerine eklendi) | springdoc, `ProblemDetail`'i Spring sınıfından `properties: object` ile üretiyor; `errorCode`/`violations` görünmüyor, `info` "OpenAPI definition"/"v0" çıkıyor. AC-5 araçla karşılaştırılabilsin diye. Yeni dependency yok (swagger-models springdoc ile gelir); `spring-boot-openapi-documentation/references/exception-handling.md` kalıbı | Farkları qa raporunda "springdoc kısıtı" diye bırakmak: AC-5 "birebir uyumlu" kriteri araçla doğrulanamaz |
| D18 | Kapsama: JaCoCo (satır %80 `check`), mutation: PIT (`pitest-maven` + `pitest-junit5-plugin`) kurulur | `backend-conventions` §5 coverage zorunlu; tester/qa akışı mutation bekliyor | Sonraki task'a bırakmak: tester/qa adımları eksik kalır |

## Sözleşme değişiklikleri
`docs/api/openapi.yaml`:
- Eklendi: `POST /api/embeddings` (`operationId: embedText`, tag `Embeddings`).
- Şemalar: `EmbedTextRequest { text: string, maxLength 2000 }`, `EmbeddingResponse { embeddingModel: string, dimensions: int32, embedding: number(float)[], durationMs: int64 }`, `ProblemDetail` (+ `errorCode`, `violations`), `Violation { field, message }`.
- Hatalar: 400 `VALIDATION_FAILED` / `BAD_REQUEST`, 503 `EMBEDDING_UNAVAILABLE` / `EMBEDDING_MODEL_NOT_FOUND`, 500 `INTERNAL_ERROR`.
- `docs/api/websocket.md` yok (WebSocket kapsam dışı).

Sözleşme sorusu 1 (backend-coder, onaydan sonra): springdoc'un `/v3/api-docs` çıktısı `ProblemDetail`'de `errorCode`/`violations`'ı göstermiyor, `info` farklı, `EmbeddingResponse.required` yok. Sözleşme **değişmedi**. Plana eklenenler: D19 + `OpenApiConfig` satırı (Backend planı); DTO alanlarına `@Schema(requiredMode = REQUIRED, description = ...)` (sözleşmedeki `required` listeleriyle aynı); test T24. Kart etkilenmedi, yeniden onay gerekmez.

## Backend planı
Yüklenecek skill'ler: `backend-conventions`, `springboot-patterns`, `problem-details-rfc9457`, `java-coding-standards`, `spring-boot-openapi-documentation` (S2=Evet), `springboot-verification` (JaCoCo/PIT kurulumu için).

### İskelet ve `pom.xml`
- Spring Boot 4.1.1 parent, Java 26 (`<java.version>26</java.version>`), Maven wrapper (`mvnw`, `.mvn/`). groupId `com.embedding`, artifactId `embedding-pgvector-backend`, uygulama sınıfı `com.embedding.pgvector.EmbeddingPgvectorApplication`.
- Dependency'ler: `spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `org.jspecify:jspecify` (Boot yönetmiyorsa sürüm açık), `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` (sadece S2=Evet).
- Test dependency'leri: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` (Boot 4'ün `@WebMvcTest` modülü; Boot 4.1'de adı farklıysa Initializr'ın ürettiği modül kullanılır ve raporda yazılır).
- Plugin'ler: surefire (Mockito `-javaagent`, `@{argLine}` korunur), JaCoCo (Java 26 class dosyalarını destekleyen sürüm; `check` satır %80), PIT (`targetClasses=com.embedding.pgvector.*`, junit5 plugin). Java 26 desteği olan sürüm yoksa bkz. "Açık sorular ve riskler".
- Her pakette `package-info.java` + `@NullMarked`.

### `application.properties`
```
spring.application.name=embedding-pgvector
spring.mvc.problemdetails.enabled=true
embedding.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
embedding.model=${EMBEDDING_MODEL:bge-m3}
embedding.connect-timeout=2s
embedding.read-timeout=60s
management.endpoints.web.exposure.include=health
```
S2=Evet ise `spring-boot-openapi-documentation` §2'deki `springdoc.*` satırları (`springdoc.paths-to-match=/api/**`).

### Paketler ve sınıflar
```
com.embedding.pgvector
├── EmbeddingPgvectorApplication
├── config/        ClockConfig
├── exception/     BaseException, ServiceUnavailableException, GlobalExceptionHandler
└── embedding/
    ├── controller/   EmbeddingController
    ├── service/      EmbeddingService
    ├── client/       EmbeddingClient, OllamaEmbeddingClient, EmbeddingResult,
    │                 EmbeddingProperties, OllamaEmbedRequest, OllamaEmbedResponse
    └── domain/dto/   EmbedTextRequest, EmbeddingResponse
```

| Sınıf | Sorumluluk ve imza |
|-------|--------------------|
| `ClockConfig` | `@Bean Clock clock()` → `Clock.systemUTC()` |
| `OpenApiConfig` (S2=Evet, D19) | `@Configuration`. `@Bean OpenApiCustomizer problemDetailSchemaCustomizer()`: `ProblemDetail` şemasından `properties` alanını çıkarır; `errorCode` (string, sözleşmedeki 9 değerlik enum, required) ve `violations` (`Violation` dizisi) ekler; `Violation { field, message }` şemasını ekler (ikisi required); `ProblemDetail.required` = `[type, title, status, errorCode]`. `info.title = "Embedding ve pgvector API"`, `info.version = "0.1.0"` (`@Bean OpenAPI` ya da aynı customizer içinde). Enum değerleri sözleşmedeki listeyle aynı sırada |
| `EmbeddingProperties` | `@ConfigurationProperties("embedding") @Validated record EmbeddingProperties(@NotNull URI baseUrl, @NotBlank String model, @NotNull Duration connectTimeout, @NotNull Duration readTimeout)`; `@ConfigurationPropertiesScan` ya da `@EnableConfigurationProperties` ile kaydedilir |
| `EmbeddingClient` | `interface`: `EmbeddingResult embed(String text)` |
| `EmbeddingResult` | `record EmbeddingResult(String embeddingModel, float[] embedding)` |
| `OllamaEmbeddingClient` | `@Component`, `implements EmbeddingClient`. Constructor `(RestClient.Builder builder, EmbeddingProperties properties)`; `JdkClientHttpRequestFactory` ile connect/read timeout, `baseUrl` = `properties.baseUrl()`. `embed`: `POST /api/embed`, gövde `OllamaEmbedRequest(model, input)`. Dönüş: `new EmbeddingResult(properties.model(), embeddings[0])`. Hata eşlemesi D7: Ollama 404 → `ServiceUnavailableException("EMBEDDING_MODEL_NOT_FOUND", "Embedding model is not available: " + model)`; diğer 2xx-dışı, `ResourceAccessException` (bağlantı reddi, timeout), `embeddings` null/boş → `ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service is not reachable")` (ya da boş vektör için `"Embedding service returned no embedding"`). Ollama'nın hata gövdesi mesaja konmaz. Instance alanında hata durumu tutulmaz (D8) |
| `OllamaEmbedRequest` | `record OllamaEmbedRequest(String model, String input)` (Ollama alan adları) |
| `OllamaEmbedResponse` | `record OllamaEmbedResponse(String model, float[][] embeddings)`, iki alan da nullable (JSpecify); bilinmeyen alanlar (`total_duration` vb.) yok sayılır |
| `EmbedTextRequest` | `record EmbedTextRequest(@NotBlank(message = "must not be blank") @Size(max = 2000, message = "size must be at most 2000 characters") @Nullable String text)`; compact constructor: `text = text == null ? null : text.strip()`. Sabitler: `TEXT_MAX_LENGTH = 2000`, `TEXT_BLANK_MESSAGE`, `TEXT_TOO_LONG_MESSAGE` (aynı record'da `public static final`) |
| `EmbeddingResponse` | `record EmbeddingResponse(String embeddingModel, int dimensions, float[] embedding, long durationMs)`; `static EmbeddingResponse from(EmbeddingResult result, long durationMs)` (`dimensions = embedding.length`) |
| `EmbeddingService` | `@Service`. Constructor `(EmbeddingClient embeddingClient, Clock clock)`. `EmbeddingResponse embedText(EmbedTextRequest request)`: `clock.millis()` → `embeddingClient.embed(request.text())` → `clock.millis()` → `EmbeddingResponse.from(...)`. Tek log: `log.info("Text embedded model={} dimensions={} durationMs={} textLength={}", ...)`. Metin ve vektör loglanmaz |
| `EmbeddingController` | `@RestController @RequestMapping("/api/embeddings")`, `@PostMapping EmbeddingResponse embedText(@Valid @RequestBody EmbedTextRequest request)`; S2=Evet ise `@Tag(name = "Embeddings")`, `@Operation`, `@ApiResponse` (400/500/503 `application/problem+json`) |
| `BaseException` | abstract; `(String errorCode, String message)`, `getErrorCode()`, `getStatus()` |
| `ServiceUnavailableException` | `extends BaseException`, status 503 |
| `GlobalExceptionHandler` | `problem-details-rfc9457` şablonu: `@RestControllerAdvice extends ResponseEntityExceptionHandler`; `BaseException` → status + `errorCode`; `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` + `violations[{field, message}]` (`message` = annotation'daki sabit mesaj); `createResponseEntity` override'ı framework hatalarına status adından `errorCode` ekler (`BAD_REQUEST`...); `Exception` → 500 `INTERNAL_ERROR`, generic `detail`, sadece burada log. `ERROR_TYPE_BASE_URI = "https://embedding-pgvector.example/errors/"`, `type` = base + `errorCode` kebab-case |

### Diğer dosyalar
- `backend/.gitignore` (`target/`).
- `backend/CLAUDE.md`: "hedef" ifadeleri gerçek sürümlerle değiştirilir; "Komutlar"a JaCoCo eşiğini atlayan komut (`./mvnw verify -Djacoco.skip=true` ya da seçilen biçim), coverage raporu yolu ve PIT komutu (`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage`) yazılır.
- `ollama.md` (D17): girişteki "model onay kartında kesinleşecek" notu kaldırılır, onaylanan model yazılır; bölüm 3'teki aday tablosu "neden bu model" satırıyla korunur; bölüm 5 gerçek endpoint ile doldurulur: backend'i başlatma, health, `curl -s localhost:8080/api/embeddings -H 'Content-Type: application/json' -d '{"text":"Şifremi unuttum, online işlemlere giremiyorum"}'` + boyutu sayan `python3` komutu, örnek yanıt (kısaltılmış), env ile model/adres değiştirme (`EMBEDDING_MODEL=... ./mvnw spring-boot:run`); "## 6. Docker ile çalıştırma (alternatif)" bölümü tamamen çıkarılır (kullanıcı kararı); "## 7. Sık karşılaşılan hatalar" başlığı "## 6. Sık karşılaşılan hatalar" olur. Bu tabloya backend hataları eklenir (503 `EMBEDDING_UNAVAILABLE` → Ollama kapalı; 503 `EMBEDDING_MODEL_NOT_FOUND` → `ollama pull`; `Port 8080 was already in use` → `lsof -i :8080`). Docker atıfları temizlenir: `address already in use` satırındaki "(servis ya da Docker)" → "(servis olarak ya da başka bir terminalde `ollama serve` ile)". Dosyada başka "Docker" geçmez.

## Frontend planı
Yok (frontend-coder atlanıyor).

## Test senaryoları
Hepsi `backend/src/test/java/com/embedding/pgvector/...` altında, test ettiği sınıfla aynı pakette. Hiçbir test `localhost:11434`'e gitmez: client testleri `OllamaStubServer`'ın portunu ya da kapalı bir portu kullanır; `@SpringBootTest`/`@WebMvcTest` testleri `embedding.base-url`'i `@DynamicPropertySource` ya da test properties ile stub'a çevirir.

Test utility'leri (tester yazar, `src/test`): `OllamaStubServer` (`embedding/client/`): JDK `HttpServer`, rastgele ya da verilen portta açılır; sıradaki yanıtı (status + JSON gövde) ayarlama, gelen istekleri (method, path, UTF-8 gövde) kaydetme, istek sayısı, "yanıt verme, latch'te bekle" modu, `close()`. `freePort()`: `ServerSocket(0)` açıp kapatarak boş port verir.

| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| T1 | Başarılı embed: alanlar ve süre | unit `EmbeddingServiceTest` | `givenClientReturnsEmbedding_whenEmbedText_thenReturnsModelDimensionsEmbeddingAndDuration` | Sahte client `EmbeddingResult("bge-m3", [0.5f, -0.25f, 0.125f, 1.0f])`, `clock.millis()` sırayla `1000L`, `1085L` → `embeddingModel="bge-m3"`, `dimensions=4`, `embedding` = `[0.5, -0.25, 0.125, 1.0]`, `durationMs=85` |
| T2 | Log içeriği (K10) | unit `EmbeddingServiceTest` + `OutputCaptureExtension` | `givenTurkishText_whenEmbedText_thenLogsModelDimensionsDurationWithoutTextOrEmbedding` | Metin `"Şifremi unuttum, online işlemlere giremiyorum"`, vektör `[0.123f, -0.456f]`, süre 85 → log `model=bge-m3`, `dimensions=2`, `durationMs=85` içerir; `Şifremi`, `0.123`, `-0.456` içermez |
| T3 | Kırpma (S-4, G2) | unit `EmbedTextRequestTest` | `givenTextWithSurroundingWhitespace_whenCreateRequest_thenStripsLeadingAndTrailingWhitespace` | `"  modem ışığı kırmızı yanıyor  "` → `"modem ışığı kırmızı yanıyor"`; `"\t\n modem \n"` → `"modem"` |
| T4 | İçteki boşluk korunur (G2) | unit `EmbedTextRequestTest` | `givenTextWithInnerDoubleSpace_whenCreateRequest_thenKeepsInnerWhitespace` | `" modem  ışığı "` → `"modem  ışığı"` (iki boşluk kalır) |
| T5 | Aynı metin iki kez aynı istek (S-3) | client `OllamaEmbeddingClientTest` | `givenSameTextTwice_whenEmbed_thenSendsIdenticalRequestBodies` | Stub'a gelen iki gövde birebir aynı; iki `EmbeddingResult.embedding` stub vektörüyle `[0.5, -0.25, 0.125]` aynı |
| T6 | İstek gövdesi: model + UTF-8 Türkçe (K3, K8) | client | `givenTurkishText_whenEmbed_thenPostsConfiguredModelAndUtf8InputToApiEmbed` | Properties `model="bge-m3"`; metin `"çğıİöşü ÇĞIÖŞÜ Şifremi unuttum"` → stub: `POST /api/embed`, JSON `model == "bge-m3"`, `input` UTF-8 çözülünce metinle birebir aynı |
| T7 | Ollama kapalı (S-7) | client | `givenNothingListeningOnBaseUrl_whenEmbed_thenThrowsEmbeddingUnavailable` | base-url kapalı port → `ServiceUnavailableException`, `errorCode="EMBEDDING_UNAVAILABLE"` |
| T8 | Model indirilmemiş (S-8) | client | `givenOllamaReturns404_whenEmbed_thenThrowsEmbeddingModelNotFound` | Stub 404 `{"error":"model \"bge-m3\" not found, try pulling it first"}` → `errorCode="EMBEDDING_MODEL_NOT_FOUND"`; exception mesajı `try pulling` içermez |
| T8b | Ollama 500 (G5) | client | `givenOllamaReturns500_whenEmbed_thenThrowsEmbeddingUnavailable` | Stub 500 `{"error":"boom"}` → `errorCode="EMBEDDING_UNAVAILABLE"`; mesaj `boom` içermez |
| T9 | Takılan Ollama (G4) | client | `givenOllamaDoesNotAnswerWithinReadTimeout_whenEmbed_thenThrowsEmbeddingUnavailable` | `readTimeout=300ms`, stub latch'te bekler → `errorCode="EMBEDDING_UNAVAILABLE"`; test sonunda latch bırakılır (`Thread.sleep` yok) |
| T9b | Kapalıyken başlatılınca aynı client çalışır (S-7) | client | `givenOllamaStartedAfterFailedCall_whenEmbedAgain_thenReturnsEmbedding` | Aynı client nesnesi: `freePort()` portunda ilk çağrı `EMBEDDING_UNAVAILABLE`; stub o portta açılınca ikinci çağrı `[0.5, -0.25, 0.125]` döner |
| T10 | Yanıt parse ve model adı | client | `givenOllamaReturnsEmbedding_whenEmbed_thenReturnsEmbeddingWithConfiguredModel` | Stub `{"model":"bge-m3","embeddings":[[0.5,-0.25,0.125]],"total_duration":84512000,"load_duration":1234000,"prompt_eval_count":14}` → `embeddingModel="bge-m3"`, `embedding=[0.5,-0.25,0.125]` |
| T10b | Boş vektör (G5) | client | `givenOllamaReturnsNoEmbeddings_whenEmbed_thenThrowsEmbeddingUnavailable` | Stub 200 `{"model":"bge-m3","embeddings":[]}` → `errorCode="EMBEDDING_UNAVAILABLE"` |
| T11 | Boş metin (S-5) | controller `EmbeddingControllerTest` (`@WebMvcTest`, `@MockitoBean EmbeddingService`) | `givenEmptyText_whenPostEmbeddings_thenReturns400ValidationFailedAndServiceNotCalled` | `{"text":""}` → 400, `application/problem+json`, `errorCode=VALIDATION_FAILED`, `violations[0].field=text`, `violations[0].message="must not be blank"`, `type` `.../errors/validation-failed`; service hiç çağrılmaz |
| T12 | Sadece boşluk (S-5) | controller | `givenWhitespaceOnlyText_whenPostEmbeddings_thenReturns400ValidationFailedAndServiceNotCalled` | `{"text":"   "}` → T11 ile aynı |
| T13 | `text` yok / null (G3) | controller | `givenMissingText_whenPostEmbeddings_thenReturns400ValidationFailed` | `{}` ve `{"text":null}` → 400 `VALIDATION_FAILED`, `field=text`; service çağrılmaz |
| T14 | 2001 karakter (S-6) | controller | `givenTextOf2001Characters_whenPostEmbeddings_thenReturns400ValidationFailed` | `"a".repeat(2001)` → 400 `VALIDATION_FAILED`, `message="size must be at most 2000 characters"`; service çağrılmaz |
| T15 | Tam 2000 karakter (G1) | controller | `givenTextOf2000Characters_whenPostEmbeddings_thenReturns200` | `"ş".repeat(2000)` → 200 |
| T16 | 2000 + baş/son boşluk (G1) | controller | `givenTextOf2000CharactersWithSurroundingSpaces_whenPostEmbeddings_thenServiceReceivesStrippedText` | `"  " + "a".repeat(2000) + "  "` → 200; `ArgumentCaptor` ile service'e gelen `text.length() == 2000` |
| T17 | Bozuk JSON (G3) | controller | `givenMalformedJson_whenPostEmbeddings_thenReturns400BadRequest` | Gövde `{"text":` → 400, `application/problem+json`, `errorCode=BAD_REQUEST` |
| T17b | Başarılı yanıt şekli (K5) | controller | `givenServiceReturnsEmbedding_whenPostEmbeddings_thenReturns200WithEmbeddingResponseJson` | Service `EmbeddingResponse("bge-m3", 3, [0.5f,-0.25f,0.125f], 85)` → 200 `application/json`, `$.embeddingModel="bge-m3"`, `$.dimensions=3`, `$.embedding=[0.5,-0.25,0.125]`, `$.durationMs=85` |
| T18 | 503 eşlemeleri (K9, AC-4) | controller | `givenEmbeddingUnavailable_whenPostEmbeddings_thenReturns503EmbeddingUnavailable` ve `givenEmbeddingModelNotFound_whenPostEmbeddings_thenReturns503EmbeddingModelNotFound` | Service `ServiceUnavailableException(...)` fırlatır → 503, `application/problem+json`, `errorCode` sırasıyla `EMBEDDING_UNAVAILABLE` / `EMBEDDING_MODEL_NOT_FOUND`, `type` `.../errors/embedding-unavailable` / `.../errors/embedding-model-not-found`, `status=503` |
| T18b | Beklenmeyen hata | controller | `givenUnexpectedException_whenPostEmbeddings_thenReturns500InternalErrorWithoutInternalMessage` | Service `IllegalStateException("secret internal")` → 500, `errorCode=INTERNAL_ERROR`, gövde `secret internal` içermez |
| T19 | Uçtan uca başarı (S-2, AC-3) | integration `EmbeddingIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, `OllamaStubServer`) | `givenOllamaStubReturns1024Values_whenPostEmbeddings_thenReturns200WithModelDimensionsEmbeddingAndDuration` | Stub 1024 elemanlı vektör (fixture: `i / 1024f`) → 200, `embeddingModel="bge-m3"`, `dimensions=1024`, `embedding` uzunluğu 1024 ve ilk iki değer `0.0`, `0.0009765625`, `durationMs >= 0` |
| T20 | Kırpma + Türkçe uçtan uca (S-4, K8) | integration | `givenTurkishTextWithSurroundingSpaces_whenPostEmbeddings_thenOllamaReceivesStrippedUtf8Text` | `{"text":"  modem ışığı kırmızı yanıyor  "}` → stub'ın aldığı `input == "modem ışığı kırmızı yanıyor"` |
| T21 | Boş metinde Ollama'ya istek yok (S-5) | integration | `givenWhitespaceOnlyText_whenPostEmbeddings_thenReturns400AndOllamaReceivesNoRequest` | 400 `VALIDATION_FAILED`; stub istek sayısı 0 |
| T22 | Kapalı → açık, restart yok (S-7, K9) | integration | `givenOllamaDownThenStarted_whenPostEmbeddingsTwice_thenFirstReturns503AndSecondReturns200` | `embedding.base-url` = `freePort()`; ilk istek 503 `EMBEDDING_UNAVAILABLE`; stub o portta açılır; ikinci istek 200 |
| T24 | Üretilen API dokümanı sözleşmeye uyuyor (AC-5, D19) | integration `OpenApiDocumentationIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`) | `givenRunningApplication_whenGetApiDocs_thenProblemDetailSchemaHasErrorCodeViolationsAndContractInfo` | `GET /v3/api-docs` → `info.title="Embedding ve pgvector API"`, `info.version="0.1.0"`; `components.schemas.ProblemDetail.properties` içinde `errorCode` (enum `EMBEDDING_UNAVAILABLE` ve `EMBEDDING_MODEL_NOT_FOUND` dahil 9 değer) ve `violations` var, `properties.properties` yok; `Violation.required = [field, message]`; `EmbeddingResponse.required = [embeddingModel, dimensions, embedding, durationMs]` |
| T23 | Model yok uçtan uca (S-8) | integration | `givenOllamaStubReturns404_whenPostEmbeddings_thenReturns503EmbeddingModelNotFound` | 503, `application/problem+json`, `errorCode=EMBEDDING_MODEL_NOT_FOUND` |

### qa canlı kontrolleri (gerçek Ollama, kullanıcının makinesi)
- Q1: `ollama.md` bölüm 1-4 komutları sırayla: `ollama --version`, `curl http://localhost:11434` → `Ollama is running`, `ollama list` modeli gösterir, doğrudan `/api/embed` isteği vektör döner ve boyut sayma komutu `1024` yazar.
- Q2: Backend `./mvnw spring-boot:run`, bölüm 5'teki curl → 200, `embeddingModel=bge-m3`, `dimensions=1024`, `embedding` 1024 sayı, `durationMs` var.
- Q3: Aynı metin iki kez → iki `embedding` dizisi birebir aynı.
- Q4: `EMBEDDING_MODEL=olmayan-model ./mvnw spring-boot:run` ile istek → 503 `EMBEDDING_MODEL_NOT_FOUND`; `OLLAMA_BASE_URL=http://127.0.0.1:9` ile → 503 `EMBEDDING_UNAVAILABLE`, ardından aynı süreçte `/actuator/health` → `UP` (backend açık). qa kullanıcının Ollama servisini lead/kullanıcı onayı olmadan durdurmaz; "kapalı" durumu kapalı port ile denenir.
- AC-6: `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` geçer.

## Soru cevaplarına göre değişenler
- **S1 ≠ bge-m3:** `embedding.model` varsayılanı ve `ollama.md`'deki model adları/boyutlar seçilen modele göre (qwen3-embedding → `qwen3-embedding:0.6b`, 1024; embeddinggemma → `embeddinggemma`, 768; nomic → `nomic-embed-text`, 768). Kullanıcı modeli `ollama pull` ile indirir. qa Q1-Q3'te beklenen boyut değişir. Kod ve testler değişmez (testler model adı olarak `bge-m3` stub değeri kullanır).
- **S2 = Hayır:** springdoc dependency'si, `springdoc.*` property'leri ve controller annotation'ları eklenmez; AC-5 qa tarafından kod ↔ `openapi.yaml` elle karşılaştırılır.

## Deployment planı
Yok. Kullanıcı kararı: Ollama Docker'da çalıştırılmaz; backend imajı ve compose TASK'ta kapsam dışı. Backend `./mvnw spring-boot:run` ile lokalde, Ollama lokalde (`http://localhost:11434`) çalışır. `deployment/` altında dosya açılmaz, devops çalışmaz.

## Açık sorular ve riskler
- **Java 26 araç desteği:** JaCoCo ve PIT'in Java 26 class dosyalarını destekleyen sürümü yoksa coverage/mutation çalışmaz. backend-coder en yeni sürümü dener; çalışmazsa lead'e mesaj atar (Java sürümünü düşürmek kullanıcı kararıdır).
- **Boot 4.1 modül adları:** `spring-boot-starter-restclient` / `spring-boot-starter-webmvc-test` adları Initializr çıktısıyla doğrulanır; farklıysa backend-coder Initializr'ın verdiği adı kullanır ve raporda yazar (davranış değişmez).
- **İlk istek yavaş:** Model belleğe yüklenirken ilk `durationMs` birkaç saniye olabilir; read timeout 60 s bunu kapsar.
- Kapsam dışı not: `backend-conventions` "vektör kolonu `vector(1536)`" ve "vektör hiçbir response'a konmaz" diyor. bge-m3 ile boyut 1024 olacak; veritabanı task'ında kolon boyutu onaylanan modelden alınmalı (skill metnini kullanıcı/lead günceller). Vektör yanıt istisnası sadece bu endpoint için (D2).
- Kapsam dışı not: Kök `CLAUDE.md`'deki rol-yol tablosunda `ollama.md` yok; bu task'ta yazma yetkisi TASK K4 gereği sadece backend-coder'a verildi. Kök `CLAUDE.md`'deki "modüllerde henüz kod yok" satırı bu task'tan sonra eskiyecek (sahibi kullanıcı/lead).

STATUS: DONE
