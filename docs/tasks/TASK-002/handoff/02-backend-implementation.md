TASK: TASK-002
# Backend uygulama raporu

## Kullanılan skill'ler
`backend-conventions`, `springboot-patterns`, `problem-details-rfc9457`, `postgres-patterns`. Plandaki diğer skill'lerin (flyway-migrations, sql-optimization-patterns, spring-transactions, java-concurrency, spring-boot-openapi-documentation, java-coding-standards) kararları plana zaten işlenmişti (D1-D25). Kod bu kararlara göre yazıldı, bu skill'leri ayrıca yüklemedim.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| pom eklemeleri (S1=A) | `pom.xml` | Eklenenler: `spring-boot-starter-jdbc`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql` (runtime), `pdfbox` 3.0.6 (`pdfbox.version`). Test scope: `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `awaitility`. Starter adları Boot 4.1.1 BOM'unda doğrulandı, sürümler Boot'tan geliyor (Testcontainers 2.0.5, Flyway 12.4.0, Awaitility 4.3.0, pgjdbc 42.7.13). PDFBox 3.0.6 Maven Central'da var (en yenisi 3.0.8), plandaki sürüm kullanıldı. |
| application.properties | `src/main/resources/application.properties` | Plandaki bütün anahtarlar eklendi. TASK-001 anahtarları korundu. |
| K26, D16, D7 migration'lar | `db/migration/V1`…`V6` | Adlar, kolonlar, CHECK'ler ve index'ler planla aynı: HNSW `vector_cosine_ops` (m=16, ef_construction=64), GIN `tsv`, B-tree `workspace_id`, `uk_sources_workspace_id_name (workspace_id, lower(name))`, partial unique `uk_ingestion_jobs_source_id_active`. `tsv` generated column. Planda olmayan küçük ekler: `ck_sources_chunk_count`, `ck_chunks_token_count`, `ck_ingestion_jobs_file_size_bytes`, `ck_ingestion_jobs_embedded_chunks`, `ck_search_events_result_count` (hepsi `>= 0`) ve FK adları (`fk_<tablo>_<hedef>`). |
| D1, D2 JDBC + vektör metni | `chunk/repository/VectorLiteral`, bütün `*Repository` sınıfları | `JdbcClient` ve native SQL. Vektör `CAST(? AS vector)` ile yazılıyor, hiç okunmuyor. Toplu ekleme `JdbcTemplate.batchUpdate` ile. |
| K2, K3 çalışma alanları | `workspace/**` | Liste (size varsayılanı 50) ve tekil getirme. Sayılar alt sorguyla. `WORKSPACE_NOT_FOUND`. |
| K4, K15, D17 kaynak ve parça listeleri | `source/**`, `chunk/**`, `pagination/PageResponse` | Sıra sabit, `sort` parametresi yok. Başka alanın kaynağı → `SOURCE_NOT_FOUND`. |
| K5, K6, D10, D12, D13 yükleme doğrulama | `ingestion/parsing/FileTypeDetector`, `source/controller/SourceController`, `source/domain/dto/NotEmptyFile*`, `exception/GlobalExceptionHandler` | Tür, uzantı ve içerikten bulunuyor. UTF-8 katı çözülüyor, BOM atılıyor. 20 MB üstü → 413 `FILE_TOO_LARGE` (`title="Content Too Large"`). Eksik ya da boş part → 400 `VALIDATION_FAILED`, `field` = part adı. |
| K7, K8, D7, D8, D11, D23 yükleme akışı | `ingestion/service/IngestionService` | Kontrol sırası sözleşmedeki gibi. Yeni kaynakta alan satırı, yeni sürümde kaynak satırı `FOR UPDATE` ile kilitleniyor. `DuplicateKeyException` 409'a çevriliyor. İş, `afterCommit` içinde executor'a veriliyor. |
| K9, K11, K14 iş yürütme | `ingestion/service/IngestionJobRunner`, `config/IngestionExecutorConfig` | Aşamalar sırayla işleniyor. Embed 96'lık gruplarla ve transaction dışında yapılıyor, ilerleme her gruptan sonra yazılıyor. Hata eşlemesi planla aynı. Hiçbir exception executor thread'inden dışarı çıkmıyor. |
| K12, K13, K14 tek transaction | `ingestion/service/IngestionJobLifecycleService` | `completeJob` tek transaction'da sırayla şunları yapar: kaynak satırını `FOR UPDATE` ile kilitler, eski parçaları siler, yenileri `modelTag` ile ekler, kaynağı `markReady` ile günceller, işi `markDone` yapar. `failJob` işi `FAILED` yapar. Kaynak `synced_at` doluysa `READY`, boşsa `FAILED` olur. |
| G1 kesinti kurtarma | `ingestion/service/IngestionRecovery` | `ApplicationReadyEvent` sırası 1. Aktif işler `INGESTION_INTERRUPTED` olur. |
| K10, D9 parçalama | `ingestion/parsing/{Pdf,Markdown,Csv}DocumentParser`, `ingestion/chunking/{TextChunker,ChunkDraft}` | Hedef 1600, üst sınır 2000, örtüşme 200 (sadece bütün cümleler). Token = ceil(embeddingInput/4). Parçalar bölüm sınırını geçmiyor. Bir parça, önceki parçanın tamamını örtüşme olarak tekrar etmiyor. |
| K16, D22 arama girdileri | `search/domain/dto/SearchChunksRequest`, `config/SearchProperties` | Plandaki sabit İngilizce mesajlar kullanılıyor. Mod ve filtre büyük/küçük harf duyarsız. Varsayılanlar `search.default-*` ayarlarından geliyor. |
| K17-K19, D3-D6 arama SQL'i | `search/repository/ChunkSearchRepository`, `search/service/ChunkRetrievalService` | Kelime CTE'si, `semantic AS MATERIALIZED` + `semantic_ranked`, hybrid tek SQL'de `FULL OUTER JOIN` + RRF ile çalışıyor. Ayarlar `set_config(..., true)` ile veriliyor: `hnsw.ef_search`, `hnsw.iterative_scan`, `hnsw.max_scan_tuples`, ve `force-vector-index=true` ise `enable_sort=off`. EXPLAIN aynı ayarlarla, bind parametreli çalışıyor (pgjdbc ile sorun çıkmadı, istisnaya gerek kalmadı). |
| K20, D19, D20 yanıt | `search/service/SearchService`, `search/service/KeywordHighlighter`, `search/domain/dto/*` | Vurgulama konumları UTF-16. Kökler PostgreSQL'den geliyor: sorgu için bir, kelimeler için bir SQL. Süreler `Clock.millis()` ile ölçülüyor. |
| K21, D18 model koruması | `embedding/service/EmbeddingModelGuard` | Arama ve yüklemede alan uyumu, embed sonrasında boyut kontrolü yapılıyor. Açılışta (`@Order(2)`) uyuşmazlık için WARN yazılıyor. |
| K24, K25, D21 arama kayıtları | `search/repository/SearchEventRepository`, `search/controller/SearchEventController` | Sadece 200 dönen aramalar kaydediliyor. Kayıt hatası (`DataAccessException`) ERROR log'una yazılıyor, yanıt yine dönüyor. |
| K22, D14, D15, D16, D25 başlangıç verisi | `seed/**`, `src/main/resources/seed/workspace-*.json` (37 dosya) | Toplam 1.410 parça. Dalga Fiber 42 parça (%2,98); `lab.html` `CH`'den birebir çıkarıldı (301→21, 302→5, 303→1, 304→15). 36 alan × 38 parça, D15'teki konu dağılımıyla, 8 telekom alanı Dalga'ya çok yakın içerikte. Kaynak bazında idempotent. Ollama kapalıysa WARN yazılıyor ve sonraki açılışta tamamlanıyor. |
| K27 hata formatı | `exception/{NotFound,Conflict,UnsupportedMediaType}Exception`, `GlobalExceptionHandler`, `config/OpenApiConfig` | `ERROR_CODES` sözleşmedeki 20 kodla aynı sırada, `API_VERSION=0.2.0`. |
| K28 loglar | Runner, Lifecycle, Recovery, SearchService, Guard, SeedDataLoader | Planın "Loglar" bölümündeki satırlar birebir yazıldı. Sorgu metni, parça içeriği ve vektör loglanmıyor; sadece `queryLength` yazılıyor. |
| K29 | `embedding/**` | `POST /api/embeddings` canlı denendi: 200, `bge-m3`, 1024. |
| Örnek dosyalar | `backend/samples/Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf`, `yardim-notlari.md`, `hata-kodlari.csv` | PDF'i oluşturan program repoya konmadı. Ayrıntı "Plandan sapmalar" 6'da. |
| backend/CLAUDE.md | `backend/CLAUDE.md` | "Teknoloji" ve "Komutlar" bölümleri güncellendi. |

## Değişen dosyalar
- **Değişen:** `pom.xml`, `CLAUDE.md`, `src/main/resources/application.properties`, `config/OpenApiConfig.java`, `embedding/client/{EmbeddingClient, EmbeddingProperties, OllamaEmbeddingClient}.java`, `exception/GlobalExceptionHandler.java`
- **Yeni (src/main/java/com/embedding/pgvector/…):**
  - `pagination/{PageResponse, PageParameters}`
  - `config/{SearchProperties, IngestionProperties, SeedProperties, IngestionExecutorConfig}`
  - `exception/{NotFoundException, ConflictException, UnsupportedMediaTypeException}`
  - `embedding/client/{EmbeddingBatchResult, OllamaEmbedBatchRequest}`, `embedding/service/EmbeddingModelGuard`
  - `workspace/{controller/WorkspaceController, service/WorkspaceService, repository/WorkspaceRepository, domain/entity/Workspace, domain/dto/WorkspaceResponse}`
  - `source/{controller/SourceController, service/SourceService, repository/SourceRepository, domain/entity/{Source, SourceKind, SourceStatus}, domain/dto/{SourceResponse, NotEmptyFile, NotEmptyFileValidator}}`
  - `chunk/{controller/ChunkController, service/ChunkService, repository/{ChunkRepository, VectorLiteral}, domain/entity/{Chunk, NewChunk}, domain/dto/ChunkResponse}`
  - `ingestion/controller/IngestionJobController`, `ingestion/service/{IngestionService, IngestionJobRunner, IngestionJobLifecycleService, IngestionRecovery}`, `ingestion/repository/IngestionJobRepository`
  - `ingestion/parsing/{FileTypeDetector, SourceDocumentParser, PdfDocumentParser, MarkdownDocumentParser, CsvDocumentParser, TextSection, DocumentUnreadableException}`, `ingestion/chunking/{TextChunker, ChunkDraft}`
  - `ingestion/domain/entity/{IngestionJob, IngestionStage, IngestionErrorCode}`, `ingestion/domain/dto/IngestionJobResponse`
  - `search/controller/{SearchController, SearchEventController}`, `search/service/{SearchService, ChunkRetrievalService, KeywordHighlighter}`, `search/repository/{ChunkSearchRepository, SearchEventRepository}`
  - `search/domain/entity/{SearchMode, FilterStrategy, SearchBranch, MatchedTermField, RetrievedHit, RetrievalResult, SearchEvent, NewSearchEvent}`
  - `search/domain/dto/{SearchChunksRequest, SearchResultResponse, SearchTimingsResponse, SearchHitResponse, MatchedTermResponse, SearchEventResponse}`
  - `seed/service/{SeedDataLoader, SeedDataReader, SeedResult}`, `seed/domain/dto/{SeedWorkspace, SeedSource, SeedChunk}`
  - Her yeni pakette `package-info.java` (`@NullMarked`)
- **Yeni kaynaklar:** `src/main/resources/db/migration/V1__enable_vector_extension.sql` … `V6__create_search_events_table.sql`, `src/main/resources/seed/workspace-{1..36,42}.json`
- **Yeni örnekler:** `backend/samples/Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf`, `backend/samples/yardim-notlari.md`, `backend/samples/hata-kodlari.csv`
- **Silinen:** yok
- **Eklenen dependency'ler:** yukarıdaki pom satırında. Hepsi planda yazılı. Planda olmayan dependency eklenmedi.

## Plandan sapmalar
Hepsi küçük ve plandaki boşlukları dolduruyor. Hiçbiri sözleşmeyi değiştirmiyor; üretilen `/v3/api-docs.yaml` sözleşmeyle karşılaştırıldı, fark yok (aşağıda "Doğrulama"). Kimseyle önceden konuşulmadı; lead'e bitiş mesajında bildirildi.
1. **`DocumentUnreadableException` public:** Plan "package-private RuntimeException" diyor. Ama exception'ı başka bir paketteki `IngestionJobRunner` (`ingestion/service`) yakalıyor, bu yüzden sınıf public olmak zorunda. Constructor package-private kaldı, API'ye çıkmıyor.
2. **Ad ve sürüm doğrulaması `@Size` yerine `@Pattern` ile:** `@Size` kırpılmamış uzunluğa bakar. `" a "` 3 karakter olduğu için geçer, kırpılınca 1 karaktere düşer ve DB CHECK'i 500 döndürürdü. Düzenli ifade kırpılmış uzunluğu ölçüyor: ad 2-80 (`SourceController.NAME_SIZE_PATTERN`), sürüm 0-40. Mesajlar plandaki gibi kaldı: `size must be between 2 and 80` ve `size must be at most 40`. Boş ad yalnızca `must not be blank` hatası verir.
3. **`page`/`size` için sabit İngilizce mesaj (`pagination/PageParameters`):** Varsayılan Bean Validation mesajı JVM diline göre Türkçe çıkıyordu (lokalde `'0' değerinden büyük yada eşit olmalı`). Mesajlar artık sabit: `must be greater than or equal to 0` ve `must be between 1 and 100`.
4. **Plandaki imzalara küçük ekler:**
   - `NewSearchEvent` record'u eklendi. `SearchEventRepository.insert(NewSearchEvent)`; planda insert parametresi tanımlı değildi.
   - `SeedResult` ayrı dosyada.
   - `SourceRepository`'ye `findByIdForUpdate(sourceId)` (lifecycle kilidi) ve `existsByName` eklendi.
   - `EmbeddingModelGuard`'a `isWorkspaceCompatible` eklendi (seed kullanıyor).
   - `KeywordHighlighter`'a `words(...)` eklendi.
   - `IngestionJobRunner` constructor'ı `IngestionJobRepository` alıyor; "Ingestion job started" logundaki workspaceId ve sourceId için.
   - "Ingestion job finished" ve "Ingestion job failed" logları `IngestionJobLifecycleService` içinde yazılıyor, çünkü sourceId ve parça sayıları orada. `completeJob` planla aynı şekilde `void`.
   - `ChunkRetrievalService` metotları sorgu vektörünü `float[]` olarak alıyor; literal'e kendisi çeviriyor.
5. **Multipart `required` listesi için `OpenApiConfig.requiredMultipartPartsCustomizer`:** Part'lar `@RequestPart(required = false)` olarak tanımlı, böylece eksik part `VALIDATION_FAILED` döner (D12). Bu ayarla springdoc gövdenin `required` listesini boş üretiyor. Customizer, `@Parameter(required = true)` taşıyan part'ları listeye ekliyor. Sözleşmedeki `required: [name, file]` ve `required: [file]` artık aynen üretiliyor.
6. **Örnek PDF 48 sayfa, 12'si metinli:** Plan hem "12 sayfa" hem de başlıkların `s. 31`, `s. 44` gibi sayfa numaralarında olmasını istiyor. Parser başlığa fiziksel sayfa numarasını yazdığından 12 sayfalık bir PDF'te `s. 44` olamaz. Bu yüzden PDF lab'daki v3.1 gibi 48 sayfa. Metinli 12 sayfa: 1, 2, 4, 6, 9, 12, 18, 24, 31, 38, 44, 47. Diğer sayfalar boş ve parser onları atlıyor.
   - Yükleme sonucu 12 parça çıkıyor: `s. 4 · Kutu içeriği`, `s. 6 · Wi-Fi 6 ve kanal genişliği (yeni)` (lab k5 metni, "802.11ax" ve "80 MHz" geçiyor), `s. 9 · Modemi fiber prizine bağlama`, `s. 18 · Wi-Fi ağ adını ve şifresini değiştirme`, `s. 31 · Modemi yeni adreste kurma`, `s. 44 · Fabrika ayarlarına dönme` ve diğerleri.
   - Font Arial Unicode TTF, PDF'e gömülü. Sayfa altbilgisi konmadı; konsaydı parça metnine karışır ve boş sayfaları boş olmaktan çıkarırdı.
7. **PDF paragraf birleştirme:** `PdfDocumentParser`, PDFBox'ın paragraf tespitini açıyor (`setParagraphEnd("\n")`). Sayfa düzeninden gelen satır kırılmaları paragraf içinde boşlukla birleşiyor, paragraflar arasında `\n\n` kalıyor. Plan gövdeyi "ilk satır hariç sayfa metninin kalanı" diye tanımlıyor; içerik aynı, sadece yapay satır sonları gitti.
8. **Seed kaynaklarında `created_at = synced_at`:** Plan bu alanı tanımlamıyordu.
9. **413 status sabiti:** Spring 7'deki `HttpStatus.CONTENT_TOO_LARGE` kullanıldı. Yanıt plandaki gibi: 413, `title="Content Too Large"`, `errorCode=FILE_TOO_LARGE`.

## Doğrulama
- `cd backend && ./mvnw -q compile` → başarılı (üretim kodu temiz derleniyor).
- `cd backend && ./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD FAILURE, test derlemesinde.** Hata: `OllamaEmbeddingClientTest.java:[23,42] constructor EmbeddingProperties … cannot be applied to given types`. Bu tester'ın dosyası; planın istediği yeni `dimensions` alanı yüzünden eski 4 argümanlı constructor artık yok (ayrıntı "Tester için notlar" 1).
  - `clean` öncesindeki artımlı koşu (test sınıfları eski haliyle derliydi): 34 testten 19'u geçti (`EmbeddingControllerTest` 11, `EmbedTextRequestTest` 6, `EmbeddingServiceTest` 2). 15 hata:
    - `OllamaEmbeddingClientTest` 9 × `NoSuchMethodError` (aynı constructor).
    - `EmbeddingIntegrationTest` 4, `EmbeddingOllamaRestartIntegrationTest` 1 ve `OpenApiDocumentationIntegrationTest` 1: context açılmıyor, çünkü testlerde veritabanı yok. Plan bunu öngörüyor ("Var olan @SpringBootTest testleri artık veritabanı ister, tester ortak Testcontainers taban sınıfına geçirir").
  - Coverage eşiği bu koşuda kapalıydı.
- **Canlı deneme:** Geçici `pgvector/pgvector:0.8.1-pg18` container'ı (port 55432) ve lokal Ollama (`bge-m3`) ile `./mvnw spring-boot:run` çalıştırıldı. Container iş bitince kaldırıldı (`docker rm -f backend-coder-pg-tmp`); 8080'de süreç kalmadı.
  - **Migration:** V1-V6 boş veritabanında hatasız uygulandı (PostgreSQL 18.2).
  - **Seed:** `Seed data started workspaces=37 sources=112` → 112 × `Seed source loaded` → `Seed data completed`, yaklaşık 40 saniye. `GET /api/workspaces/42` → `sourceCount=4`, `chunkCount=42`; liste `totalElements=37`. İkinci açılış: `loadedSources=0 skippedSources=112`.
  - **Seed, Ollama kapalı, boş veritabanı:** `Seed data incomplete, will retry on next start missingSources=112 errorCode=EMBEDDING_UNAVAILABLE`. 37 alan yazıldı, kaynak yok, backend UP.
  - **S-2:** Parola sorgusu `KEYWORD` → 0 sonuç. `HYBRID` → 1. sırada `Online İşlemler parolanı sıfırlama`, `matchedBy=[SEMANTIC]`, mesafe 0,3431.
  - **S-3:** `TS-999 hata kodu` `HYBRID` → ilk 4 sonucun hepsi TS-999 parçası, `[KEYWORD, SEMANTIC]` (s4, c5, c7, c6). İlk sonucun rrf değeri 0,03279.
  - **S-4:** Nakil sorgusu `VECTOR` + `POST_FILTER` + ef 40 → `vectorCount=4`, `vectorShort=true`, log'da `Short vector branch workspaceId=42 requested=10 returned=4 efSearch=40 filterStrategy=POST_FILTER`. `ITERATIVE_SCAN` → `vectorCount=10`. `HYBRID` + `POST_FILTER` → 4.
  - **S-5:** `explain=true` → `Index Scan using idx_chunks_embedding on chunks`, `Filter: (workspace_id = '42'::bigint)`, `Rows Removed by Filter: 36`.
  - **S-6:** v3.2 PDF → 202. İş `PARSING` → `DONE`, 12/12 parça. Kaynak 302: `version="3.2"`, `chunkCount=12`, `READY`. `Wi-Fi 6 kanal genişliği 80 MHz` `HYBRID` → 1. sırada `s. 6 · Wi-Fi 6 ve kanal genişliği (yeni)` (`KEYWORD, SEMANTIC`).
  - **S-7:** İş sürerken ikinci yükleme → 409 `INGESTION_IN_PROGRESS`.
  - **S-8:** `OLLAMA_BASE_URL=http://127.0.0.1:9` ile çalışan backend'de 302'ye yükleme → iş `FAILED`, `EMBEDDING_UNAVAILABLE`, progress 0,1. 302 `READY`, eski sürümüyle (`3.2`, 12 parça). Keyword arama eski parçaları buluyor. Vector arama → 503 `EMBEDDING_UNAVAILABLE`.
  - **S-9 ve diğer yükleme hataları:**
    - `.docx` ve sahte `.pdf` → 415 `UNSUPPORTED_FILE_TYPE`.
    - 25 MB PDF → 413 `FILE_TOO_LARGE`.
    - 0 baytlık dosya, eksik part → 400 `VALIDATION_FAILED` (`field=file`, `must not be empty`).
    - Eksik ya da `" a "` ad → 400 `VALIDATION_FAILED` (`name`).
    - Windows-1254 TXT → 415 `UNSUPPORTED_FILE_ENCODING`. NUL baytlı CSV → 415 `UNSUPPORTED_FILE_TYPE`.
    - JSON gövde → 415 `UNSUPPORTED_MEDIA_TYPE`.
    - PDF kaynağa CSV ve WEB_CRAWL kaynağa MD → 409 `SOURCE_KIND_MISMATCH`.
    - 41 karakterlik sürüm → 400 (`version`).
    - `"  yardım MERKEZİ "` → 409 `SOURCE_NAME_TAKEN`.
    - 11. kaynak → 409 `SOURCE_LIMIT_REACHED`.
  - **S-10:** `EMBEDDING_MODEL=embeddinggemma` → açılışta `Embedding model mismatch configuredModel=embeddinggemma@1024 storedModel=bge-m3@1024 workspaceCount=37`. `VECTOR` ve `HYBRID` → 409 `EMBEDDING_MODEL_MISMATCH`, `KEYWORD` → 200. Yükleme → 409 `EMBEDDING_MODEL_MISMATCH`.
  - **S-11:** Kıyı Net (19) `Şifremi unuttum` → `VECTOR` ve `HYBRID` sonuçlarının kaynakları sadece {1191, 1192, 1193}.
  - **K3:** `/api/workspaces/19/sources/301` → 404 `SOURCE_NOT_FOUND`. `/19/ingestion-jobs/1` → 404 `INGESTION_JOB_NOT_FOUND`. `/999/search` → 404 `WORKSPACE_NOT_FOUND`.
  - **G1:** Elle eklenmiş `EMBEDDING` aşamasındaki iş, yeniden başlatmada `FAILED/INGESTION_INTERRUPTED` oldu; kaynak `READY`'ye döndü.
  - **Diğer:** CSV ve MD yükleme `DONE` (5 ve 3 parça; CSV başlığı ilk sütun, içerik `Kod: …\nAçıklama: …\nÇözüm: …`). Türkçe kaynak adı bozulmadan kaydediliyor. Arama kayıtları yeniden eskiye, `channel` ve `topChunkHeading` ile listeleniyor. `POST /api/embeddings` → 200, `bge-m3`, 1024.
- **Sözleşme uyumu:** Çalışan uygulamadan `/v3/api-docs.yaml` alındı ve `docs/api/openapi.yaml` ile script'le karşılaştırıldı: path, method, operationId, parametreler, status kodları, response content-type'ları, şema alanları, `required` listeleri, tipler (nullable dahil) ve enum'lar. **Fark yok.** `info.version` = 0.2.0.

## Tester için notlar
1. **Derleme kırığı (önce bu):** `src/test/.../embedding/client/OllamaEmbeddingClientTest.java:23` `new EmbeddingProperties(baseUrl, model, connectTimeout, readTimeout)` çağırıyor. Yeni imza: `EmbeddingProperties(URI baseUrl, String model, int dimensions, Duration connectTimeout, Duration readTimeout)`. Bu düzelene kadar temiz `./mvnw verify` ve `./mvnw spring-boot:run` derlemede kırılıyor; geçici çalıştırma için `./mvnw spring-boot:run -Dmaven.test.skip=true`.
2. **Var olan 3 `@SpringBootTest` sınıfı veritabanı istiyor:** Plandaki `PostgresIntegrationTest` tabanı gerekli. `seed.enabled=false` verilmeli. Uygulama varsayılan olarak `localhost:5432`'ye bağlanmaya çalışıyor.
3. **`EmbeddingClient`'ın yeni metodu var:** `embedAll(List<String>)`. `FakeEmbeddingClient` iki metodu da uygulamalı. Boş liste için Ollama çağrılmıyor.
4. **Clock:** `SearchService`, süreleri `clock.millis()` ile ölçüyor. Sırasıyla: başlangıç, embed öncesi ve sonrası, SQL öncesi ve sonrası, toplam. Sabit Clock ile bütün süreler 0 çıkar. Runner ve seed de `clock.millis()` kullanıyor. Zaman damgaları `Instant.now(clock)` ile yazılıyor.
5. **İşin commit sonrası gönderilmesi:** `IngestionService.submitAfterCommit`, aktif transaction senkronizasyonu yoksa (ör. Spring'siz unit test) `IngestionJobRunner.submit`'i hemen çağırıyor. Varsa `afterCommit`'te çağırıyor.
6. **`IngestionJobRunner`:**
   - Constructor: `(TaskExecutor ingestionExecutor, IngestionJobLifecycleService, IngestionJobRepository, List<SourceDocumentParser>, TextChunker, EmbeddingClient, EmbeddingModelGuard, IngestionProperties, Clock)`. `IngestionJobRepository` sadece başlangıç logu için; mock `Optional.empty()` dönerse sorun yok.
   - `run(...)` exception fırlatmıyor. `failJob` hata verirse ERROR log'una yazılıyor.
   - T19 için `IngestionProperties(96, 1600, 2000, 200, 4, 2, 100)` ve 214 parça üreten bölümler gerekli. En kolayı, her biri 1600 karakteri aşmayan 214 ayrı `TextSection`.
7. **`SearchService` → `ChunkRetrievalService` imzaları:**
   - `retrieveKeyword(long workspaceId, String query, int limit)`
   - `retrieveVector(long workspaceId, float[] queryEmbedding, int limit, int efSearch, FilterStrategy)`
   - `retrieveHybrid(long workspaceId, String query, float[] queryEmbedding, int limit, int efSearch, FilterStrategy)`
   - `explainVector(long workspaceId, float[] queryEmbedding, int limit, int efSearch, FilterStrategy)`
   - Kökler (`findQueryLexemes`, `findLexemes`) `ChunkSearchRepository`'den, retrieval transaction'ının dışında, sadece kelime kolunun bulduğu sonuç varsa çekiliyor.
   - Kayıt `SearchEventRepository.insert(NewSearchEvent)` ile yazılıyor; T28/T30 için ArgumentCaptor kullanılabilir.
8. **Multipart controller testleri:**
   - `name` ve `version` `@RequestPart String` olarak tanımlı. MockMvc'de form parametresi (`.param`) değil part olarak gönderilmeli: `.part(new MockPart("name", "Fatura SSS".getBytes(UTF_8)))`, dosya için `MockMultipartFile("file", "yardim.md", "text/markdown", bytes)`.
   - Doğrulama `HandlerMethodValidationException` ile geliyor; handler `violations[].field`'a part adını yazıyor.
   - Kırpılmış uzunluk düzenli ifadeyle kontrol ediliyor (sapma 2).
9. **Doğrulama mesajları sabit İngilizce:**
   - Arama: plandaki listeyle aynı.
   - Upload: `must not be blank`, `size must be between 2 and 80`, `must not be empty`, `size must be at most 40`.
   - Sayfalama: `page` için `must be greater than or equal to 0`, `size` için `must be between 1 and 100`.
10. **I21-I23:**
    - `search.force-vector-index=true` iken vektör transaction'ında `enable_sort=off` ayarlanıyor.
    - I22 için: `set_config(..., true)` transaction'a yerel; sonraki transaction'da `hnsw.ef_search` 40'a, `enable_sort` `on`'a dönmeli.
    - EXPLAIN çıktısında `Order By` satırı vektör literal'ini tam içeriyor; assertion'da `contains` kullanın.
11. **Seed (I27):**
    - `SeedDataLoader.seedMissingData()` public ve senkron.
    - `seed.location=classpath:seed-test/` için dosya adı kalıbı `workspace-*.json`.
    - Dönüş `SeedResult(loadedSources, skippedSources, missingSources)`. İkinci çalıştırmada `loadedSources=0`, `skippedSources=` mevcut kaynak sayısı.
    - Ollama hatasında `missingSources` = o andan sonra kalan bütün kaynaklar.
12. **Seed içeriği (T41):** 37 dosya, 1.410 parça. Dalga parça sayıları `21/5/1/15`. Dalga dışında 1.368 içerik birbirinden farklı; her biri 60-700 karakter, hepsinin başlığı dolu. Diğer alanların kaynak id'leri `1000 + 10×id + n`.
13. **I13:** 25 MB isteği gerçek Tomcat'te (`RANDOM_PORT`) 413 `FILE_TOO_LARGE` dönüyor; `server.tomcat.max-swallow-size=-1` ayarlı. MockMvc'de multipart limitleri uygulanmaz.
14. **Açılış dinleyicileri (hepsi `ApplicationReadyEvent`):** `IngestionRecovery` (Order 1), `EmbeddingModelGuard.warnOnMismatch` (Order 2), `SeedDataLoader.scheduleSeed` (Order 3, `seed.enabled=false` ise bir şey yapmaz).
15. **Örnek PDF:** Testlerde `backend/samples/` yerine plandaki gibi `SampleFiles` ile üretilmiş ASCII PDF kullanın. Örnek PDF Arial Unicode gömülü ve 48 sayfa.

## Düzeltme
—

## Düzeltme turu 1
Kaynak: kullanıcı kararı (lead aracılığıyla) ve `handoff/01-plan.md` "Düzeltme turu 1". Veri erişimi JDBC'den tamamen Spring Data JPA + entity'lere çevrildi. Bu bölüm, yukarıdaki bölümlerdeki JDBC'ye ait anlatımların (D1/D2 satırları, repository imzaları, "Tester için notlar" 6-7) yerine geçer. Parsing, parçalama, Ollama toplu embed, hata eşlemesi, DTO'lar, yapılandırma, seed JSON'ları ve örnek dosyalar değişmedi. Sözleşme ve onay kartı da değişmedi.

### Madde → yapılan değişiklik
| Plan maddesi | Yapılan | Dosya(lar) |
|---|---|---|
| Dependency'ler | `spring-boot-starter-jdbc` çıktı. `spring-boot-starter-data-jpa` ve `org.hibernate.orm:hibernate-vector` eklendi; sürüm Boot BOM'undan geliyor, pom'da sürüm yazılı değil. `./mvnw dependency:tree` ile doğrulandı: `hibernate-vector:7.4.5.Final` → `hibernate-core:7.4.5.Final`, ikisi aynı sürüm. Test scope'a `spring-boot-starter-data-jpa-test` eklendi. | `pom.xml` |
| application.properties | Eklenenler: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `hibernate.jdbc.batch_size=100`, `hibernate.order_inserts=true`. | `application.properties` |
| D16 migration V2-V4 | V2: `workspaces.id` atanmış `bigint PRIMARY KEY` oldu. V3: `sources_id_seq` (`START WITH 10001`) eklendi, `sources.id` atanmış, sequence `OWNED BY sources.id`. V4: `chunks_id_seq` (`INCREMENT BY 50`) eklendi, `chunks.id` atanmış, sequence `OWNED BY chunks.id`. V5 ve V6 değişmedi (IDENTITY). | `db/migration/V2…V4` |
| D1 entity'ler | Beş entity yazıldı: `Workspace` ve `Source` (atanmış id + `Persistable<Long>`, `@Transient newEntity`, `@PostLoad`/`@PostPersist`), `Chunk` (`SEQUENCE` `chunks_id_seq`, `allocationSize = 50`; `workspaceId` düz kolon; `@ManyToOne(LAZY) source`; `@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 1024) float[] embedding`; `tsv` eşlenmedi), `IngestionJob` ve `SearchEvent` (`IDENTITY`). Hepsinde `protected` no-arg constructor var, setter yok, değişiklik domain fiilleriyle yapılıyor. | `workspace/domain/entity/Workspace`, `source/domain/entity/Source`, `chunk/domain/entity/Chunk`, `ingestion/domain/entity/IngestionJob`, `search/domain/entity/SearchEvent` |
| D23 kaynak kuralları | `Workspace.addSource(sourceId, name, kind, createdAt)` limiti (10 → `SOURCE_LIMIT_REACHED`) ve adı (`toLowerCase(Locale.ROOT)` → `SOURCE_NAME_TAKEN`) kontrol ediyor. Kilit `WorkspaceRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`). Son güvence: `saveAndFlush` sırasında `DataIntegrityViolationException` → `SOURCE_NAME_TAKEN`. | `Workspace`, `IngestionService.createSource` |
| D7 aynı kaynağa ikinci yükleme | `findByWorkspaceIdAndIdForUpdate` (`PESSIMISTIC_WRITE`) + `existsBySourceIdAndStageNotIn` + `saveAndFlush`. `DataIntegrityViolationException` → `INGESTION_IN_PROGRESS`. | `SourceRepository`, `IngestionJobRepository`, `IngestionService.uploadSourceVersion` |
| K12 tek transaction | `completeJob(jobId, drafts, embeddings)` tek `@Transactional`: kaynak kilidi, `@Modifying(flushAutomatically = true)` JPQL bulk delete, `saveAll(Chunk.create…)` (batch insert), `Source.completeIngestion`, `IngestionJob.complete`. `failJob` iki entity fiilini çağırıyor: `job.fail` ve kilitli kaynakta `source.failIngestion()`. Diğer aşama metotları (`startJob`, `moveToStage`, `recordTotalChunks`, `recordEmbeddingProgress(jobId, embedded)`) kısa transaction'lar; ilerleme değerleri entity'de hesaplanıyor. | `IngestionJobLifecycleService`, `IngestionJob`, `IngestionJobRunner` |
| Repository'ler | `WorkspaceRepository` (`findByIdForUpdate`, `findSummaries`, `findSummaryById` → `WorkspaceSummary`), `SourceRepository` (`findByWorkspaceIdAndId`, `findByWorkspaceIdAndIdForUpdate`, `findByWorkspaceIdOrderByIdAsc`, `nextSourceId`), `ChunkRepository` (`findBySourceIdOrderByChunkNoAscIdAsc` → `ChunkView`, `deleteBySourceId`, `findEmbeddingModels`, `countWorkspacesByEmbeddingModel` → `EmbeddingModelUsage`), `IngestionJobRepository` (`findByWorkspaceIdAndId`, `existsBySourceIdAndStageNotIn`, `findByStageNotIn`), `SearchEventRepository` (`findByWorkspaceIdOrderByCreatedAtDescIdDesc`). | `*/repository/*` |
| D3, D4, D5 native sorgular | `ChunkSearchRepository extends Repository<Chunk, Long>`. Bütün metotlar `@Query(nativeQuery = true)`: `searchKeyword`, `searchVector`, `searchHybrid`, `explainVector`, `applySearchSettings` (tek `SELECT set_config(...) × 4`), `findQueryLexemes`, `findLexemes`. Sonuçlar `RetrievedHitView`, `WordLexemeView`, `AppliedSearchSettings` projection'larına alınıyor; alias'lar çift tırnaklı camelCase. SQL'ler JDBC sürümüyle aynı. EXPLAIN native `@Query` ile bind parametreli çalıştı, `EntityManager` yedeğine gerek kalmadı. | `search/repository/*`, `search/service/ChunkRetrievalService` |
| D2 VectorLiteral | `search/service/VectorLiteral`'a taşındı ve sadece native arama parametresi için kullanılıyor. Entity tarafında yazım `hibernate-vector` ile tipli. | `search/service/VectorLiteral` |
| D17 sayfalama | Service katmanı `PageRequest.of(page, size)` ile `Page<XResponse>` dönüyor, controller `PageResponse.from(page)` ile sarıyor. `PageResponse.of` ve `offset` kaldırıldı. | `pagination/PageResponse`, 4 controller, 4 service |
| D14 seed | Alan için `existsById`, yoksa `save(Workspace.seeded…)`. Kaynak için `existsById`, sonra embed, sonra `TransactionTemplate` içinde `save(Source.seeded…)` + `saveAll(Chunk.create…)`. `DataIntegrityViolationException` → WARN ve atla. | `seed/service/SeedDataLoader` |
| Silinenler | Silinen dosyalar: `chunk/domain/entity/NewChunk`, `search/domain/entity/NewSearchEvent`, `chunk/repository/VectorLiteral`. `Workspace`, `Source`, `IngestionJob` ve `SearchEvent` record'ları entity olarak yeniden yazıldı. | — |
| backend/CLAUDE.md | "Teknoloji" güncellendi: JPA, hibernate-vector, id stratejileri, native sorgu kuralı, "Henüz olmayanlar". "Spring Boot 4 tuzakları"na `@DataJpaTest` `flush()`+`clear()` ve "`IDENTITY` id'de batch insert yok" eklendi. | `backend/CLAUDE.md` |

### Değişen dosyalar (Düzeltme turu 1)
- **Değişen:**
  - `pom.xml`, `CLAUDE.md`, `application.properties`, `V2`/`V3`/`V4` migration'ları
  - `pagination/PageResponse`
  - Controller'lar: `WorkspaceController`, `SourceController`, `ChunkController`, `SearchEventController`
  - Servisler: `WorkspaceService`, `SourceService`, `ChunkService`, `IngestionService`, `IngestionJobLifecycleService`, `IngestionJobRunner`, `IngestionRecovery`, `ChunkRetrievalService`, `SearchService`, `SeedDataLoader`, `EmbeddingModelGuard`
  - DTO'lar: `WorkspaceResponse`, `SourceResponse`, `ChunkResponse`, `IngestionJobResponse`, `SearchEventResponse`
  - Enum: `IngestionStage`
- **Entity'ye dönen:** `Workspace`, `Source`, `IngestionJob`, `SearchEvent`, `Chunk`
- **Interface'e dönen:** `WorkspaceRepository`, `SourceRepository`, `ChunkRepository`, `IngestionJobRepository`, `SearchEventRepository`, `ChunkSearchRepository`
- **Yeni:** `workspace/repository/WorkspaceSummary`, `chunk/repository/{ChunkView, EmbeddingModelUsage}`, `search/repository/{RetrievedHitView, WordLexemeView, AppliedSearchSettings}`, `search/service/VectorLiteral`
- **Silinen:** `chunk/domain/entity/NewChunk`, `search/domain/entity/NewSearchEvent`, `chunk/repository/VectorLiteral`

### Plandan sapmalar (Düzeltme turu 1)
Kimseyle önceden konuşulmadı; lead'e bitiş mesajında bildirildi.
1. **`Source` için public static fabrika:** Plan, `Source`'un package-private constructor'ını `Workspace.addSource`'un çağırmasını istiyor. İki sınıf farklı paketlerde olduğu için bu derlenmez. Constructor `private` oldu; `Workspace.addSource` public `Source.ingesting(workspace, id, name, kind, createdAt)` fabrikasını çağırıyor. Plandaki `Source.seeded(...)` fabrikası da var.
2. **Dört "derived" adlı metoda açık JPQL `@Query`:** `SourceRepository.findByWorkspaceIdAndId` ve `findByWorkspaceIdOrderByIdAsc`, `ChunkRepository.findBySourceIdOrderByChunkNoAscIdAsc`, `IngestionJobRepository.existsBySourceIdAndStageNotIn`. Plan bu metotlarda `getWorkspaceId()`/`getSourceId()` getter'larını öngörüyor. Spring Data bu getter'ları özellik sandı ve `s.workspaceId` üretti, Hibernate de bunu reddetti (canlı denemede 500). Metot adları plandaki gibi kaldı, sorgular ilişki üzerinden (`s.workspace.id`, `c.source.id`) yazıldı.
3. **"Ingestion job started" logu `startJob`'da:** `IngestionJobLifecycleService.startJob` logluyor; kind, kaynağın türünden okunuyor. Runner'ın repository bağımlılığı kalktı. Constructor: `(TaskExecutor, IngestionJobLifecycleService, List<SourceDocumentParser>, TextChunker, EmbeddingClient, EmbeddingModelGuard, IngestionProperties, Clock)`.
4. **`RetrievedHitView` sıra ve sayı alanları `Long`:** `ROW_NUMBER()` ve `count(*)` PostgreSQL'de `bigint` döner; `ChunkRetrievalService` bunları `Integer`'a çeviriyor. `ts_rank_cd` (`real`) ve RRF (`numeric`) SQL'de `CAST(... AS double precision)` ile `Double` geliyor. `::` cast'i yerine `CAST(...)` kullanıldı, çünkü native sorgunun parametre ayrıştırıcısı `:` ile karışabilir.
5. **Küçük ekler:** `IngestionStage.FINISHED` (`EnumSet` DONE/FAILED) eklendi; repository çağrılarında ve `isActive()`'de kullanılıyor. `Chunk.EMBEDDING_DIMENSIONS = 1024` sabiti `@Array(length = ...)` için.
6. **`VectorLiteral` kaldı:** Lead'in mesajında "JDBC kalıntısı" örnekleri arasında sayıldı. Plan (D2 ve paket ağacı) onu `search/service`'te native arama parametresi için açıkça tutuyor ve JDBC'ye bağımlılığı yok. Plana göre bırakıldı. Kaldırılması isteniyorsa vektör parametresi başka bir yolla bağlanmalı; bu planner kararı.
7. **`"YARDIM MERKEZI"` gibi adlar kabul ediliyor:** Hem `toLowerCase(Locale.ROOT)` hem PostgreSQL `lower()` `I`'yı `i`'ye çeviriyor. Bu yüzden "yardim" ile "yardım" farklı ad sayılıyor. `"yardım MERKEZİ"` ise DB index'iyle 409 alıyor. JDBC sürümüyle aynı davranış, bilgi olarak yazıldı.

### Doğrulama (Düzeltme turu 1)
- **JDBC kalıntısı:** `grep -rn "JdbcClient\|JdbcTemplate\|starter-jdbc\|jdbc.core\|DuplicateKeyException" pom.xml src/main` → sonuç yok.
- **Derleme:** `cd backend && ./mvnw -q clean compile` → başarılı.
- **verify:** `cd backend && ./mvnw verify -Djacoco.haltOnFailure=false` → **BUILD FAILURE (testCompile)**. Neden aynı: `OllamaEmbeddingClientTest.java:[23,42]` eski 4 argümanlı `EmbeddingProperties` constructor'ını çağırıyor. Tester'ın dosyası; tester düzeltene kadar test derlemesi, `verify` ve `spring-boot:run` kırık. Lokal çalıştırma: `./mvnw spring-boot:run -Dmaven.test.skip=true`. Var olan 3 `@SpringBootTest` (`EmbeddingIntegrationTest`, `EmbeddingOllamaRestartIntegrationTest`, `OpenApiDocumentationIntegrationTest`) veritabanı istiyor (plan: Testcontainers tabanı tester'da).
- **Canlı deneme:** Geçici `pgvector/pgvector:0.8.1-pg18` (port 55432) ve lokal Ollama `bge-m3` ile `./mvnw spring-boot:run -Dmaven.test.skip=true`. Container iş bitince kaldırıldı (`docker rm -f backend-coder-pg-tmp`); 8080'de süreç kalmadı.
  - **Hibernate `validate` ve migration:** V1-V6 boş veritabanında uygulandı, context hatasız açıldı. Tip uyuşmazlığı çıkmadı: `text`, `timestamptz`, `vector(1024)` ve sequence artışları entity'lerle uyumlu.
  - **Seed:** `loadedSources=112`, yaklaşık 31 saniye. `chunks`: 1.410 satır, id'ler 1-1410 kesintisiz (pooled sequence ve batch insert), tek model etiketi, `vector_dims=1024`. `sources_id_seq` `last_value` 10001 (seed onu kullanmıyor). İkinci açılış: `loadedSources=0 skippedSources=112`. Boş veritabanı + Ollama kapalı: `Seed data incomplete … missingSources=112 errorCode=EMBEDDING_UNAVAILABLE`, 37 alan yazıldı. Sonraki açılışta (Ollama açık) `loadedSources=112`, 1.410 parça.
  - **S-2:** Parola sorgusu `KEYWORD` → 0. `HYBRID` → 1. sırada `Online İşlemler parolanı sıfırlama` (`SEMANTIC`, mesafe 0,3431).
  - **S-3:** `TS-999 hata kodu` `HYBRID` → ilk 4 sonucun hepsi `[KEYWORD, SEMANTIC]` TS-999 parçası; ilk rrf 0,03279.
  - **S-4:** Nakil sorgusu `VECTOR` + `POST_FILTER` + ef 40 → `vectorCount=4`, `vectorShort=true`, log'da `Short vector branch … returned=4`. `ITERATIVE_SCAN` → 10.
  - **S-5:** `Index Scan using idx_chunks_embedding`, `Rows Removed by Filter: 36`.
  - **S-6:** v3.2 → 202; iş `PARSING` → `DONE`, 12/12. 302 `READY`, `3.2`, 12 parça. Yeni parça id'leri 1452-1463; eski id'lerden (<1411) 302'de kalan yok. `Wi-Fi 6 kanal genişliği 80 MHz` → 1. sırada `s. 6 · Wi-Fi 6 ve kanal genişliği (yeni)` `[KEYWORD, SEMANTIC]`. Parça listesi `page=1,size=3` → `totalElements=12`, `totalPages=4`, `chunkNo` sırasıyla.
  - **S-7 ve D7:** İş sürerken ikinci yükleme → 409 `INGESTION_IN_PROGRESS`. Aynı kaynağa iki paralel yükleme → biri 202, biri 409; tek iş satırı oluştu.
  - **S-8:** `OLLAMA_BASE_URL=http://127.0.0.1:9` ile 302'ye 3.3 yüklendi → `FAILED`, `EMBEDDING_UNAVAILABLE`, progress 0,1. 302 `READY`, `3.2`, 12 parça. Keyword arama eski parçaları buluyor. Vector arama → 503 `EMBEDDING_UNAVAILABLE`.
  - **G1:** Elle eklenen `EMBEDDING` işi → açılışta `FAILED/INGESTION_INTERRUPTED`, kaynak 10003 `READY`.
  - **S-9 ve diğer hatalar:**
    - `.docx` → 415 `UNSUPPORTED_FILE_TYPE`. 25 MB → 413 `FILE_TOO_LARGE`.
    - 0 bayt → 400 (`file`). `" a "` → 400 (`name`, `size must be between 2 and 80`).
    - Windows-1254 → 415 `UNSUPPORTED_FILE_ENCODING`.
    - PDF kaynağa CSV ve WEB_CRAWL kaynağa MD → 409 `SOURCE_KIND_MISMATCH`.
    - `/19/sources/301/versions` → 404 `SOURCE_NOT_FOUND`. `/999/sources` → 404 `WORKSPACE_NOT_FOUND`.
    - `"  yardım MERKEZİ "` → 409 `SOURCE_NAME_TAKEN`. 11. kaynak → 409 `SOURCE_LIMIT_REACHED`.
    - CSV yükleme `DONE`, 5 parça.
  - **S-10:** `EMBEDDING_MODEL=embeddinggemma` → WARN `configuredModel=embeddinggemma@1024 storedModel=bge-m3@1024 workspaceCount=37`. `VECTOR`/`HYBRID` → 409, `KEYWORD` → 200, yükleme → 409.
  - **S-11:** Kıyı Net `VECTOR`/`HYBRID` → kaynaklar sadece {1191, 1192, 1193}. `/19/ingestion-jobs/2` → 404 `INGESTION_JOB_NOT_FOUND`.
  - **Diğer:** `size=101` → 400 `must be between 1 and 100`. Arama kayıtları yeniden eskiye, `topChunkHeading` ile dönüyor. `POST /api/embeddings` → 200, `bge-m3`, 1024.
  - **Sözleşme:** `/v3/api-docs.yaml` ↔ `docs/api/openapi.yaml` script karşılaştırması → **fark yok** (path, method, operationId, parametre, status, content-type, şema alanları, `required`, tipler, enum'lar).

### Tester için notlar (Düzeltme turu 1, öncekilerin yerine geçer)
1. **Derleme kırığı (önce bu):** `OllamaEmbeddingClientTest:23` → `new EmbeddingProperties(baseUrl, model, dimensions, connectTimeout, readTimeout)`.
2. **Veritabanı isteyen testler:** Var olan 3 `@SpringBootTest` için Testcontainers tabanı gerekiyor; `seed.enabled=false` verin. `ddl-auto=validate` açık, I33 bunu context açılışıyla doğrular. `TRUNCATE … RESTART IDENTITY CASCADE`, `OWNED BY` sequence'leri de sıfırlar: `sources_id_seq` 10001'e, `chunks_id_seq` 1'e döner.
3. **Id kuralları:**
   - Seed kaynakları dışında kaynak id'si `SourceRepository.nextSourceId()` ile alınıyor. Oluşturma 409 ile bitse bile sequence değeri harcanır; id'lerde boşluk olabilir.
   - `Workspace` ve `Source` testte `Workspace.seeded(...)` ve `Source.seeded(workspace, seedSource, chunkCount)` / `workspace.addSource(...)` ile kurulabilir. `Persistable` sayesinde `persist` olarak yazılırlar.
4. **`IngestionJobLifecycleService`:**
   - `startJob(jobId)`, `moveToStage(jobId, stage)` (sadece CHUNKING/EMBEDDING/WRITING), `recordTotalChunks(jobId, total)`, `recordEmbeddingProgress(jobId, embedded)`, `completeJob(jobId, drafts, embeddings)` (listeler aynı uzunlukta olmalı), `failJob(jobId, code)`.
   - T19'daki ilerleme beklentisi artık `recordEmbeddingProgress(jobId, 96)`, `(jobId, 192)`, `(jobId, 214)`.
   - `completeJob` bir kez çağrılır: 214 draft ve 214 vektörle.
5. **`IngestionJob` entity (E4):** `start`, `moveTo`, `recordTotalChunks`, `recordEmbeddingProgress`, `complete`, `fail`. Biten işte her fiil `IllegalStateException` fırlatıyor. `moveTo(PARSING/DONE/FAILED/QUEUED)` `IllegalArgumentException` fırlatıyor.
6. **`Source` entity (E3):** `ensureAcceptsUpload`, `startIngestion`, `completeIngestion`, `failIngestion`, `hasSuccessfulVersion`. `Workspace.addSource` (E1, E2) önce limiti, sonra adı kontrol ediyor.
7. **Arama:**
   - `SearchService` → `ChunkRetrievalService` imzaları önceki rapordakiyle aynı (`float[] queryEmbedding`).
   - Arama kaydı `searchEventRepository.save(SearchEvent.record(...))` ile yazılıyor. T28 için `save`'i `DataAccessResourceFailureException` fırlatacak şekilde stub'layın. T30 için ArgumentCaptor ile `SearchEvent`'in `getResultCount`, `getTopChunkId` ve `getTopChunkHeading` değerlerine bakın.
   - `ChunkSearchRepository` unit testte mock'lanırsa `RetrievedHitView` sıra ve sayı alanları `Long` döner.
8. **I22:** `AppliedSearchSettings` projection'ı `set_config` dönüş değerlerini veriyor: `efSearch`, `iterativeScan`, `maxScanTuples`, `enableSort` (`force-vector-index=true` iken `"off"`). Sonraki transaction'da `current_setting(...)` varsayılana dönmeli.
9. **Önceki notlardan geçerliliğini koruyanlar:** 3, 4, 5, 8, 9, 10, 11, 12, 13, 14, 15. Not 6'dan sadece runner constructor'ı değişti (madde 3, "Plandan sapmalar").

STATUS: DONE
