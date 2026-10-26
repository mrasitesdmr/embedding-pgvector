TASK: TASK-001
# Backend uygulama raporu

## Kullanılan skill'ler
- `backend-conventions`: isimlendirme, paket yapısı, yorum yasağı, `@NullMarked`, `Clock`, log formatı.
- `springboot-patterns`: katmanlar, `RestClient` + timeout.
- `problem-details-rfc9457`: `BaseException`, `ServiceUnavailableException`, `GlobalExceptionHandler` şablonu.
- `java-coding-standards`.
- `spring-boot-openapi-documentation`: springdoc, controller ve DTO annotation'ları, `OpenApiCustomizer` kalıbı (`references/exception-handling.md`).
- `springboot-verification`: JaCoCo/PIT kurulumu, verify adımları ve grep kontrolleri.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|------------------------|-----------|-----|
| İskelet, `pom.xml` | `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`, `EmbeddingPgvectorApplication` | Spring Initializr çıktısından alındı: Boot 4.1.1, Java 26, Maven 3.10.0 wrapper. Uygulama sınıfında `@ConfigurationPropertiesScan` var. Modül adları Initializr ile doğrulandı: `spring-boot-starter-restclient`, `spring-boot-starter-webmvc-test`. |
| Her pakette `@NullMarked` | 7 adet `package-info.java` | Sınıfı olan her pakette. Boş ara paketler (`embedding`, `embedding.domain`) açılmadı. |
| `application.properties` | `src/main/resources/application.properties` | Planla birebir aynı. Ek olarak skill §2'deki `springdoc.*` satırları var. |
| K3, D13 | `EmbeddingProperties`, `application.properties` | `${OLLAMA_BASE_URL:http://localhost:11434}`, `${EMBEDDING_MODEL:bge-m3}`, `connect-timeout=2s`, `read-timeout=60s`. Record `@Validated`. |
| D9 | `config/ClockConfig` | `Clock.systemUTC()` |
| K1, K8, K9, D7, D8, D10, D11 | `embedding/client/OllamaEmbeddingClient`, `EmbeddingClient`, `EmbeddingResult`, `OllamaEmbedRequest`, `OllamaEmbedResponse` | İstek: `POST {baseUrl}/api/embed`, gövde `{"model","input"}`. İstemci `JdkClientHttpRequestFactory` + `HttpClient` (HTTP/1.1, connect timeout) ve `setReadTimeout(readTimeout)` ile kurulur. Hata eşlemesi: `HttpClientErrorException.NotFound` → `EMBEDDING_MODEL_NOT_FOUND` (`"Embedding model is not available: " + model`). Diğer `RestClientException`'lar (bağlantı reddi, timeout, 4xx/5xx, parse hatası) → `EMBEDDING_UNAVAILABLE` (`"Embedding service is not reachable"`). Gövde null ya da `embeddings` null/boş ya da ilk vektör null/boş → `EMBEDDING_UNAVAILABLE` (`"Embedding service returned no embedding"`). Ollama'nın hata gövdesi mesaja konmaz. Instance'ta hata durumu tutulmaz. `OllamaEmbedResponse` üzerinde `@JsonIgnoreProperties(ignoreUnknown = true)` var. |
| K6, K7, D3, D4, D5 | `embedding/domain/dto/EmbedTextRequest` | Compact constructor'da `strip()`. `@NotBlank(message="must not be blank")`, `@Size(max=2000, message="size must be at most 2000 characters")`. Sabitler: `TEXT_MAX_LENGTH`, `TEXT_BLANK_MESSAGE`, `TEXT_TOO_LONG_MESSAGE`. |
| K5, D12 | `embedding/domain/dto/EmbeddingResponse` | `from(result, durationMs)`, `dimensions = embedding.length` |
| K5, K10 | `embedding/service/EmbeddingService` | `clock.millis()` önce ve sonra okunur. Tek INFO log: `Text embedded model={} dimensions={} durationMs={} textLength={}`. Metin ve vektör loglanmaz. |
| D1, S2 | `embedding/controller/EmbeddingController` | `POST /api/embeddings` → 200. `@Tag("Embeddings")`, `@Operation(operationId="embedText")`. 400/500/503 için `application/problem+json` ve `ProblemDetail` belgelendi. |
| AC-4, D15, D16 | `exception/BaseException`, `ServiceUnavailableException`, `GlobalExceptionHandler` | Skill şablonu kullanıldı. Plandaki imzaya göre `BaseException.getStatus()` (şablondaki `getHttpStatus()` değil). Log sadece 500'de atılır. |
| D19 (plan güncellemesi, aşağıya bkz.) | `config/OpenApiConfig` | `@Bean OpenApiCustomizer problemDetailSchemaCustomizer()`: `info` sözleşmedeki başlık ve sürümle ayarlanır. `ProblemDetail` şemasından `properties` çıkarılır, `errorCode` (9 değerlik enum, sözleşmedeki sırayla) ve `violations` eklenir, `required = [type, title, status, errorCode]`. `Violation { field, message }` şeması eklenir (ikisi de required). |
| D19, DTO `@Schema` | `EmbedTextRequest`, `EmbeddingResponse` | `@Schema(requiredMode = REQUIRED, description, example)`. Required listeleri sözleşmedekiyle aynı. |
| D17, K4 | `ollama.md` (kök) | Girişte onaylanan model `bge-m3` yazıyor. "Nasıl çalışıyor?" bölümü backend'i de gösteriyor. Bölüm 3'te "Neden bge-m3?" tablosu ve gerekçesi var. Bölüm 5 dolu: başlatma, health, curl, boyut/süre yazdırma, iki istekte aynı vektör (hash), kırpma ve boş metin örneği, Swagger UI, `EMBEDDING_MODEL` / `OLLAMA_BASE_URL` tablosu. Eski "6. Docker" bölümü çıkarıldı, "7." numarası "6." oldu. 6. bölüme `EMBEDDING_UNAVAILABLE`, `EMBEDDING_MODEL_NOT_FOUND`, `VALIDATION_FAILED` ve `Port 8080 was already in use` satırları eklendi. `address already in use` satırı plandaki metinle düzeltildi. Dosyada "Docker" geçmiyor (`grep -ci docker` → 0). Bölüm 5'teki bütün komutlar canlı backend ile çalıştırıldı ve çıktılar gerçek. |
| `backend/CLAUDE.md` | `backend/CLAUDE.md` | "Teknoloji", "Paket yapısı" ve "Komutlar" bölümlerindeki "hedef" ifadeleri kaldırıldı; gerçek sürümler, dependency'ler ve plugin'ler yazıldı. "Henüz olmayanlar" listesi güncellendi. Eklenen komutlar: coverage eşiğini kırmadan verify (`-Djacoco.haltOnFailure=false`), coverage raporunun yolu, PIT komutu ve rapor yolu, env ile çalıştırma, Swagger, `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify`. |

## Değişen dosyalar
Yeni (`backend/`):
- `pom.xml`, `.gitignore`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- `src/main/resources/application.properties`
- `src/main/java/com/embedding/pgvector/`
  - `EmbeddingPgvectorApplication.java`, `package-info.java`
  - `config/`: `ClockConfig.java`, `OpenApiConfig.java`, `package-info.java`
  - `exception/`: `BaseException.java`, `ServiceUnavailableException.java`, `GlobalExceptionHandler.java`, `package-info.java`
  - `embedding/controller/`: `EmbeddingController.java`, `package-info.java`
  - `embedding/service/`: `EmbeddingService.java`, `package-info.java`
  - `embedding/client/`: `EmbeddingClient.java`, `OllamaEmbeddingClient.java`, `EmbeddingResult.java`, `EmbeddingProperties.java`, `OllamaEmbedRequest.java`, `OllamaEmbedResponse.java`, `package-info.java`
  - `embedding/domain/dto/`: `EmbedTextRequest.java`, `EmbeddingResponse.java`, `package-info.java`

Değişen:
- `backend/CLAUDE.md`
- `ollama.md` (kök; plan D17 gereği)

Silinen: yok. `src/test/**` altına dosya yazılmadı. Initializr'ın `contextLoads` testi tester'ın alanında olduğu için kopyalanmadı.

Eklenen dependency'ler (hepsi planda var):
- `spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, `spring-boot-starter-validation`, `spring-boot-starter-actuator` (Boot yönetir)
- `org.jspecify:jspecify:1.0.1`, `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`
- test: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`

Plugin'ler:
- `maven-dependency-plugin` (`properties` goal'ü, Mockito agent yolu için)
- surefire `argLine` = `@{argLine} -javaagent:${org.mockito:mockito-core:jar}`. `<argLine></argLine>` boş property olarak tanımlı; JaCoCo atlansa da `@{argLine}` çözülür.
- `jacoco-maven-plugin` 0.8.15 (`prepare-agent`; `verify` fazında `report` + `check`, satır coverage en az %80)
- `pitest-maven` 1.30.1 + `pitest-junit5-plugin` 1.2.3 (`targetClasses`/`targetTests` = `com.embedding.pgvector.*`, HTML+XML, zaman damgasız rapor)

## Plandan sapmalar
Yok.

Plan güncellemesi (sapma değil): `config/OpenApiConfig` ve DTO'lardaki `@Schema(requiredMode = REQUIRED)` planner'ın onayıyla plana D19 ve T24 olarak eklendi. Sebebi: springdoc'un ürettiği dokümanda `ProblemDetail` içinde `errorCode`/`violations` görünmüyordu, `info` "OpenAPI definition"/"v0" çıkıyordu ve `EmbeddingResponse.required` eksikti. `docs/api/openapi.yaml` değişmedi, onay kartı etkilenmedi.

Küçük uygulama ayrıntıları (planla çelişmez):
- `EmbeddingService`, metni `Objects.requireNonNull(request.text())` ile alır. Validation'dan geçen istekte metin hiçbir zaman null değildir.
- `OllamaEmbeddingClient`, ilk vektör boş (`[[]]`) geldiğinde de `EMBEDDING_UNAVAILABLE` döner (G5'in devamı).
- `HttpClient` HTTP/1.1'e sabitlendi; JDK istemcisi düz HTTP'de h2c upgrade denemesi yapmaz.
- `OpenApiConfig`, `ProblemDetail.instance`'ın `format: uri`'sini de kaldırır; sözleşmede `instance: string` yazıyor.

## Doğrulama
- `cd backend && ./mvnw verify` → **BUILD SUCCESS**. "No tests to run": henüz test yok, testleri tester yazacak. JaCoCo `report`/`check` "Skipping JaCoCo execution due to missing execution data file" ile atlandı. Coverage eşiği bu adımda doğrulanmadı; tester ve qa tam `./mvnw verify` ile doğrular. Eşiği kapatan komuta (`-Djacoco.haltOnFailure=false`) gerek kalmadı, düz `verify` geçti.
- `./mvnw -q compile` → hatasız.
- **Java 26 araç uyumu:** Projenin bir kopyasında (`src/test`'e dokunmadan) 5 geçici testle denendi:
  - `./mvnw verify -Djacoco.haltOnFailure=false` → Tests run: 5, Failures: 0. JaCoCo 0.8.15 Java 26 class'larını okudu, satır coverage %55 olarak raporlandı (eşik uyarısı beklendiği gibi çıktı).
  - `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` → PIT 1.30.1 Java 26'da çalıştı, mutant üretip öldürdü. Mockito agent PIT minion'ına surefire `argLine`'ı üzerinden geçti.
  - Java 26 ile JaCoCo/PIT uyumsuzluğu **yok**.
- **Canlı smoke** (kullanıcının Ollama 0.40.2'si, `bge-m3:latest`; Ollama'ya dokunulmadı, backend sonunda durduruldu):

  | İstek | Sonuç |
  |-------|-------|
  | `POST /api/embeddings` `{"text":"Şifremi unuttum, online işlemlere giremiyorum"}` | 200, `embeddingModel=bge-m3`, `dimensions=1024`, `embedding` 1024 sayı, `durationMs` ilk istekte 1410 (model yükleniyor), sonra 42 / 13 |
  | Aynı metin iki kez | İki vektör birebir aynı |
  | `"  modem ışığı kırmızı yanıyor  "` ile `"modem ışığı kırmızı yanıyor"` | Aynı vektör hash'i |
  | `{"text":"   "}` | 400 `VALIDATION_FAILED`, `violations[0]={field:text, message:"must not be blank"}` |
  | Bozuk JSON | 400 `BAD_REQUEST`, `application/problem+json` |
  | `EMBEDDING_MODEL=olmayan-model` (port 8081) | 503 `EMBEDDING_MODEL_NOT_FOUND`, `detail="Embedding model is not available: olmayan-model"` |
  | `OLLAMA_BASE_URL=http://127.0.0.1:9` (port 8081) | 503 `EMBEDDING_UNAVAILABLE`; ardından aynı süreçte `/actuator/health` → `UP` |
  | `/swagger-ui.html` | 302 → `/swagger-ui/index.html` 200 |
  | `/v3/api-docs.yaml` | `info`, path, operationId, status kodları, `ProblemDetail` (`errorCode` enum, `violations`), `Violation`, `EmbeddingResponse`, `EmbedTextRequest` sözleşmeyle aynı |

  Log: `Text embedded model=bge-m3 dimensions=1024 durationMs=42 textLength=45`. Logda metin ve vektör yok.
- Grep kontrolleri (`System.out`, `printStackTrace`, Lombok, `@MockBean`, Jackson 2 `databind`, Javadoc, koda gömülü `11434`) → `src/main/java`'da bulgu yok.

## Tester için notlar
- **Client testleri (T5-T10b):** `new OllamaEmbeddingClient(RestClient.builder(), new EmbeddingProperties(URI.create("http://127.0.0.1:" + port), "bge-m3", Duration.ofSeconds(2), Duration.ofMillis(300)))`. Spring context gerekmez; düz `RestClient.builder()` Jackson 3 converter'ını classpath'ten bulur. Read timeout'u `EmbeddingProperties.readTimeout` belirler (T9 için 300 ms).
- İstemci her `embed` çağrısında yeni HTTP isteği atar ve hata durumu tutmaz. T9b ve T22'de aynı nesne ya da süreç kullanılabilir. Bağlantı HTTP/1.1, istekte `Content-Type: application/json`, gövde UTF-8 JSON `{"model":"...","input":"..."}`.
- **Hata mesajları** (testte birebir kullanılabilir):
  - `EMBEDDING_MODEL_NOT_FOUND`: `"Embedding model is not available: " + model`
  - `EMBEDDING_UNAVAILABLE`: bağlantı, timeout ya da 4xx/5xx durumunda `"Embedding service is not reachable"`; gövde, `embeddings` ya da ilk vektör null/boşsa `"Embedding service returned no embedding"`
  - Kategori `ServiceUnavailableException`. `getErrorCode()` ve `getStatus()` (`HttpStatus.SERVICE_UNAVAILABLE`) var; `extracting("errorCode")` çalışır.
- **Integration testleri:** `embedding.base-url`'i `@DynamicPropertySource` ile stub'a çevirin. `Duration` property'leri `"300ms"` biçiminde verilebilir.
- **Zaman:** `EmbeddingService` `Clock` alır (`ClockConfig`). T1/T2'de `Clock` mock'u `millis()` → `1000L, 1085L`. Service constructor'ı `(EmbeddingClient, Clock)`; `@InjectMocks` yerine elle kurun.
- **Log (T2):** Logger `com.embedding.pgvector.embedding.service.EmbeddingService`. Mesaj: `Text embedded model=bge-m3 dimensions=2 durationMs=85 textLength=<n>`.
- **Controller (T11-T18b):** `@WebMvcTest(EmbeddingController.class)`, `GlobalExceptionHandler` advice olarak yüklenir. Validation yanıtında `instance=/api/embeddings` Spring tarafından eklenir. Violation sabitleri `EmbedTextRequest.TEXT_BLANK_MESSAGE` / `TEXT_TOO_LONG_MESSAGE` olarak var; beklenen metni yine de testte bağımsız literal olarak yazmanız önerilir. Framework hataları (`BAD_REQUEST` vb.) `createResponseEntity` override'ından `errorCode` ve `type` alır.
- **T24 (`/v3/api-docs`):** springdoc `required` listelerini alfabetik sıralıyor (`[errorCode, status, title, type]`, `[dimensions, durationMs, embedding, embeddingModel]`). Listeleri sırasız karşılaştırın (`containsExactlyInAnyOrder`). `errorCode` enum sırası sözleşmedeki gibidir. `EmbedTextRequest.text`'te springdoc `@Size`'dan `minLength: 0` üretir; sözleşmede bu alan yok, zararsız.
- **Coverage:** `EmbeddingPgvectorApplication.main` hiçbir testte çağrılmaz (1 satır). `OpenApiConfig` T24 ile kapsanır. `ProblemDetail` şeması yoksa erken dönen dal pratikte çalışmaz.
- **PIT:** `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` → `target/pit-reports/index.html`.

## Düzeltme
Yok.

STATUS: DONE
