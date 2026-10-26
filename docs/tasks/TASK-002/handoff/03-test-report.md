TASK: TASK-002
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`, `spring-testing-fundamentals`, `spring-mvc-testing`, `spring-jpa-testing`. Sadece `backend/src/test/**` altına yazıldı; `src/main`, `pom.xml` değişmedi. Frontend yok (planda atlanmıştı).

## Test altyapısı (yeni)
- `support/TestcontainersConfiguration`: `pgvector/pgvector:0.8.1-pg18`, `@ServiceConnection`. Container JVM başına tek (static); bütün context'ler paylaşır.
- `support/PostgresIntegrationTest`: `@SpringBootTest(RANDOM_PORT)`, `seed.enabled=false`, her testten önce **ve sonra** `TRUNCATE ... RESTART IDENTITY CASCADE` (`@DataJpaTest` testleri temiz veritabanı görsün diye).
- `support/FakeEmbeddingIntegrationTest` + `FakeEmbeddingClient` (`@Primary`): kayıtlı/deterministik vektör, `failWith`, `blockUntilReleased` (latch, `Thread.sleep` yok), batch boyutu kaydı. Gerçek Ollama'ya hiçbir test gitmez.
- Fixture'lar: `WorkspaceFixtures`, `SourceFixtures`, `ChunkFixtures`, `IngestionJobFixtures`, `VectorFixtures`, `SampleFiles` (PDFBox ile ASCII PDF), `SteppingClock`. `ApiTestClient`'a `postMultipart` eklendi.
- `src/test/resources/seed-test/` (2 alan, 3 kaynak, 5 parça).

## Bilinen kırıkların düzeltilmesi
1. `OllamaEmbeddingClientTest` derleme kırığı: `EmbeddingProperties(baseUrl, model, 1024, connectTimeout, readTimeout)`.
2. `EmbeddingIntegrationTest`, `EmbeddingOllamaRestartIntegrationTest`, `OpenApiDocumentationIntegrationTest` → `PostgresIntegrationTest` tabanına (Testcontainers). Mevcut stub sunucu (`OllamaStubServer`) kaldı.
3. `OpenApiDocumentationIntegrationTest` T43'e göre güncellendi (sürüm 0.2.0, 20 `errorCode`, 10 path, şema adları).

## Senaryo → test eşlemesi
| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| T1 | Desteklenen türler (K5) | `FileTypeDetectorTest#givenSupportedExtensionAndMatchingContent_whenDetect_thenReturnsSourceKind` (7 satır) | geçti |
| T2 | Desteklenmeyen uzantı (S-9) | `FileTypeDetectorTest#givenUnsupportedOrMissingExtension_...` (+ null ad → `none`) | geçti |
| T3 | Sahte PDF | `FileTypeDetectorTest#givenPdfExtensionWithoutPdfHeader_...`, `givenPdfSignatureWithinFirst1024Bytes_...` | geçti |
| T4 | Kodlama / NUL (G2) | `FileTypeDetectorTest#givenWindows1254Text_...`, `givenTextWithNulByte_...` | geçti |
| T5 | BOM (G2) | `MarkdownDocumentParserTest#givenUtf8BomBeforeHeading_...`, `FileTypeDetectorTest#givenUtf8Bom_whenDecodeText_...` | geçti |
| T6, T7 | MD başlıkları, kod bloğu | `MarkdownDocumentParserTest#givenMarkdownWithHeadings_...`, `givenHashLineInsideFencedCode_...`, `givenHashLineInsideTildeFence_...`, `givenSeveralLinesInsideBacktickFence_...` | geçti |
| T8-T10 | PDF sayfa başlığı, metinsiz, bozuk | `PdfDocumentParserTest` (9 test: 80/81 karakter başlık sınırı, boşluksuz satır, satır birleştirme dahil) | geçti |
| T11-T14 | CSV (G5) | `CsvDocumentParserTest` (13 test: `;`/sekme ayracı, tırnak kaçışı, çok satır, CRLF, eşitlikte `,`, boş satır, sadece başlık) | geçti |
| T15-T18 | Parçalama (K10) | `TextChunkerTest` (17 test: 1600/2000 sınırları, 99/100 karakterlik cümlelerle örtüşme, bölüm sınırı, sert bölme) | geçti |
| T19 | 96'lık gruplar (K11) | `IngestionJobRunnerTest#given214Chunks_...` + parametreli sınır testi (1, 95, 96, 97, 192, 193) | geçti |
| T20-T22 | Ollama kapalı / yanlış boyut / beklenmeyen hata (K14) | `IngestionJobRunnerTest#givenEmbeddingUnavailable_...`, `givenModelReturns768Dimensions_...`, `givenParserThrowsUnexpectedException_...`, ayrıca `NO_TEXT_EXTRACTED`, model yok, `failJob` hatası, executor reddi | geçti |
| T23, T24 | Toplu istek (K11) | `OllamaEmbeddingClientTest#givenTwoTexts_whenEmbedAll_...`, `givenOllamaReturnsFewerEmbeddingsThanInputs_...`, boş liste, boş vektör, 404 | geçti |
| T25-T31 | Arama servisi (K16-K18, K21, K24, K25, K28) | `SearchServiceTest` (18 test: varsayılanlar, keyword'de embed yok, süre ayrımı, model koruması, kayıt hatası, kısa kol WARN, sıfır sonuç WARN, log'da sorgu metni yok `queryLength=45`) | geçti |
| T32-T34 | Arama girdi doğrulama (K16, D22) | `SearchControllerTest` (27 test: boş/uzun sorgu, 7 geçersiz parametre, sınır değerleri, küçük harf mod, hata eşleme 404/409/503/500) | geçti |
| T35-T37 | Yükleme doğrulama, 202 (K6, K7) | `SourceControllerTest` (28 test: eksik/boş ad ve dosya, ad 2-80 sınırları, sürüm 40, 202 + `Location`, 415 JSON gövde) | geçti |
| T38 | 404/409/415 eşleme (K27) | `SourceControllerTest#givenDomainException_whenUploadSourceVersion_...` (+ `type`, `instance`, `title`) | geçti |
| T39 | Liste parametreleri (D17) | `WorkspaceControllerTest`, `SourceControllerTest#givenInvalidPageOrSize_...`, `SearchControllerTest`, `ChunkControllerTest` | geçti |
| T40 | İş bulunamadı (K3) | `IngestionJobControllerTest#givenUnknownJob_...` | geçti |
| T41 | Seed içeriği (K22, D15, D25) | `SeedDataReaderTest` (5 test: 37 alan, Dalga 21/5/1/15, 1410, oran, 38'er parça, tekil içerik, kaynak id'leri) | geçti |
| T42 | Kesinti logu (K28, G1) | `IngestionRecoveryTest#givenActiveJobAtStartup_...` | geçti |
| T43 | API dokümanı (AC-5) | `OpenApiDocumentationIntegrationTest` | geçti |
| E1, E2 | Kaynak limiti, ad tekilliği (D23, G4) | `WorkspaceTest` (limit 10/9, büyük-küçük harf ad, yeni kaynak alanları) | geçti |
| E3 | Kaynak durum geçişleri (D11) | `SourceTest` (11 test) | geçti |
| E4 | İş ilerlemesi (K9) | `IngestionJobTest` (14 test; 0.4588785 formülü, biten işte her fiil) | geçti |
| R1 | Alan özetleri (K2) | `WorkspaceRepositoryTest` (3 test) | geçti |
| R2 | Kilitli okuma, alan sınırı, tekil ad | `SourceRepositoryTest` (6 test) | geçti |
| R3 | Vektör eşlemesi (D2) | `ChunkRepositoryTest#givenChunkPersistedWithFloatArray_...` (+ elle hesaplı karışık vektör 1-√0,5) | geçti |
| R4 | Parça sorguları (K15, K21) | `ChunkRepositoryTest` (sıra, sayfa, silme, modeller, model kullanımı) | geçti |
| R5 | Aktif iş tekilliği (D7) | `IngestionJobRepositoryTest` (6 test) | geçti |
| I1 | Şema (K26) | `SchemaMigrationIntegrationTest` (4 test: eklenti, tablolar, `vector(1024)`, HNSW/GIN/btree index'leri, `tsv`, sequence'ler) | geçti |
| I2 | Alanlar (K2, K3) | `WorkspaceIntegrationTest` (3 test) | geçti |
| I3, I4, I31 | Kaynak listesi, çapraz alan 404, parça listesi | `SourceIntegrationTest` (10 test) | geçti |
| I5 | Yeni kaynak uçtan uca (K7, K9, K13, K15) | `IngestionIntegrationTest#givenMarkdownFile_whenCreateSourceAndPollJob_...` (+ CSV/PDF testi) | geçti |
| I6 | Yeni sürüm değiştirir (K12, S-6) | `IngestionIntegrationTest#givenSourceWithOldChunks_whenUploadNewVersion_...` | geçti |
| I7 | Commit'e kadar eski parçalar (K12) | `IngestionIntegrationTest#givenJobBlockedInEmbedding_...` | geçti |
| I8, I9 | Aktif iş 409, eşzamanlı yükleme (K8) | `IngestionIntegrationTest#givenActiveJob_...`, `givenTwoParallelUploadsToSameSource_...` | geçti |
| I10, I11 | Başarısız sürüm / ilk yükleme (K12, K14) | `IngestionIntegrationTest#givenEmbeddingUnavailable_whenUploadNewVersion_...`, `..._whenCreateSource_...` | geçti |
| I12 | Metinsiz PDF (K14) | `IngestionIntegrationTest#givenPdfWithoutText_...` (+ bozuk PDF → `FILE_UNREADABLE`) | geçti |
| I13 | Reddedilen dosyalar (K5, K6) | `IngestionIntegrationTest#givenRejectedUpload_...` (gerçek Tomcat: docx, sahte pdf, Windows-1254, 25 MB → 413, 0 bayt) | geçti |
| I14-I16 | Tür uyuşmazlığı, ad/limit, model koruması | `IngestionIntegrationTest#givenFileKindDifferentFromSourceKind_...`, `SourceIntegrationTest#givenExistingNameWithDifferentCase_...`, `givenTenSources_...`, `IngestionIntegrationTest#givenWorkspaceChunksWithOtherModel_...` | geçti |
| I17 | Kesinti kurtarma (G1) | `IngestionIntegrationTest#givenJobsLeftActive_whenRecoveryRuns_...` | geçti |
| I18 | Kelime kolu (K17, S-2) | `SearchIntegrationTest#givenLabPasswordChunk_...` | geçti (sorgu uyarlandı, bkz. Notlar) |
| I19, I20 | RRF, boş kelime listesi (K19, G3) | `SearchIntegrationTest#givenChunkInBothBranches_...`, `givenStopWordOnlyQuery_...` | geçti |
| I21 | Post-filter / iterative (K18, S-4) | `SearchIntegrationTest#givenSelectiveWorkspaceFilter_whenPostFilterWithEf40_...`, `..._whenIterativeScan_...` | geçti |
| I22 | SET LOCAL kapsamı (K18) | `SearchIntegrationTest#givenSearchWithEf100_...` | geçti |
| I23 | EXPLAIN (K20, S-5) | `SearchIntegrationTest#givenExplainTrue_...` | geçti |
| I24 | İzolasyon (K1, S-11) | `SearchIntegrationTest#givenTwoWorkspacesWithSimilarChunks_...` (3 mod) | geçti |
| I25 | Arama kayıtları (K24) | `SearchIntegrationTest#givenThreeSearches_whenListSearchEvents_...`, `givenFailedSearch_...` | geçti |
| I26 | Vurgulama konumları (K20) | `SearchIntegrationTest#givenTs999Chunk_whenHybridSearch_...`, `SearchServiceTest#givenKeywordHitsAndExplainFlag_...` | geçti |
| I27 | Seed (K22, S-1) | `SeedDataLoaderIntegrationTest` (5 test: bir kez/bozmadan, Ollama kapalıyken devam, model uyuşmazlığı, kısmi seed) | geçti |
| I28 | Açılış uyuşmazlık WARN (K21, S-10) | `EmbeddingModelMismatchIntegrationTest` (`warnOnMismatch()` doğrudan çağrılır, bkz. Notlar) | geçti |
| I32 | Eşzamanlı oluşturma / limit (D23) | `SourceIntegrationTest#givenNineSourcesAndTwoParallelCreates_...` | geçti |
| I33 | Hibernate `validate` (D1) | `SchemaMigrationIntegrationTest#givenFlywaySchema_whenContextStarts_...` (context `ddl-auto=validate` ile açılır; her `@SpringBootTest` ve `@DataJpaTest` bunu zaten doğrular) | geçti |
| K29 | `POST /api/embeddings` aynen | mevcut `EmbeddingControllerTest`, `EmbeddingIntegrationTest`, `EmbeddingOllamaRestartIntegrationTest`, `EmbeddingServiceTest`, `EmbedTextRequestTest` (veritabanlı context'te) | geçti |

Plandan sonra eklenen (planda yoktu, TASK kuralından): `VectorLiteralTest`, `KeywordHighlighterTest`, `ChunkControllerTest`, `SearchEventController` sayfa doğrulaması, `givenFailedFirstIngestion_whenUploadAgain_...` (FAILED kaynak yeniden yüklemeyle READY), arama başarısızken kayıt yazılmaması (D21), `givenFileOver20Mb_...` (413 `title`).

## Mutation
PIT 1.30.1. İlk koşuda tüm sınıfları + `@SpringBootTest` testlerini hedefleyen tam koşu mutant başına Testcontainers/Spring context yüzünden zaman aşımına düştü ve iptal edildi. Kapsam hızlı testlere daraltıldı (`-DexcludedTestClasses='*IntegrationTest'`, 4 thread, ~40 sn):

`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Djacoco.skip=true -DtargetClasses='…ingestion.parsing.*,…ingestion.chunking.*,…ingestion.domain.entity.*,…source.domain.entity.*,…workspace.domain.entity.*,…search.service.*,…ingestion.service.IngestionJobRunner,…embedding.client.OllamaEmbeddingClient,…exception.*' -DtargetTests=<unit + @WebMvcTest paketleri> -DexcludedTestClasses='*IntegrationTest' -DtimeoutConstant=8000 -Dthreads=4`

| Koşu | Üretilen | Öldürülen | Test gücü |
|------|----------|-----------|-----------|
| 1 | 462 | 366 (%79) | %87 |
| 2 (mutant testleri eklendikten sonra) | 462 | 388 (%84) | %90 |

Kapatılan hayatta kalan mutantlar (kurala ait olanlar): sürüm/süre hesabı (`SteppingClock` başlangıcı 0 olduğu için `+`/`-` ayırt edilemiyordu → 1.000.000'dan başlıyor); vurgulama için `findLexemes` argümanının doğrulanması; `IngestionJob.recordEmbeddingProgress` aktif kontrolü ve `total <= 0` sınırı; parti sayısı sınırları (95/96/97/192/193 parça); `FileTypeDetector` (`none` uzantısı, sadece-BOM içerik); Markdown kod bloğu içinde birden fazla satır; CSV boş-ayraç satırı, CRLF, tırnaklı ayraç, `,` eşitlik önceliği; parçalama sınırları (1600 / 1601, 2000 / 2001, 99 vs 100 karakter örtüşmesi); PDF başlığı 80/81 karakter sınırı; hata cevabında `type`/`title`/`instance`/`status`.

Hayatta kalanlar (bilinçli):
- `GlobalExceptionHandler` `setTitle`/`setInstance`: Spring `ProblemDetail` aynı değerleri durumdan/istek yolundan kendisi türetiyor; JSON'da fark yok (denk mutant).
- `KeywordHighlighter.highlight:32`, `VectorLiteral.format:9` (StringBuilder kapasitesi), `IngestionJobRunner.averageTokens`/süre hesabı (sadece log): denk ya da sadece log.
- `Workspace.isNew` (`true` dönen mutant): `@DataJpaTest`/entegrasyon testlerinde öldürülüyor, hızlı kapsamda değil.
- `TextChunker` iç sınırları (`assemble`, `overlap`, `hardSplitIndex`, `length`) ve `CsvDocumentParser.parseRecords` satır 115/127 iç koşulları: davranışı gözlemlenebilir tüm sınırlar test edildi; kalanlar tek karakter farkı yaratan iç kenar durumları. Zorunlu eşik yok; ek test eklenmedi.
- Entegrasyon testleriyle kapsanan sınıflar (repository'ler, `ChunkRetrievalService`, `IngestionService`, `SeedDataLoader`, `GlobalExceptionHandler` 413 dalı) PIT kapsamında değil (yukarıdaki zaman aşımı); bunlar için kanıt JaCoCo ve entegrasyon testleridir.

## Komutlar ve sonuçları
- `cd backend && ./mvnw verify` → **BUILD SUCCESS**, 345 test, 0 başarısız, 0 atlanan. JaCoCo satır coverage **%96,6** (1360 kapsanan / 48 kaçan; eşik %80 geçti), dal coverage ≈ %88.
- `cd backend && OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify` → **BUILD SUCCESS**, 345 test, 0 başarısız (testler Ollama'ya bağlı değil).
- Testlerden sonra kalan container yok (`docker ps -a` boş; Ryuk kendini temizledi).
- Her test dosyası yazıldıktan sonra tek başına çalıştırıldı (`-Dtest=...`).

## Çalıştırılamayanlar
- Gerçek Ollama ve compose'lu uçtan uca kontroller (qa Q0-Q12): çalışan sistem gerektiriyor; qa çalıştıracak.
- S-4/S-5'in **gerçek bge-m3 verisiyle** sonucu (Q4, Q5): bu raporda sentetik vektörlerle (I21, I23) doğrulandı.
- Frontend yok.

## Notlar (plana göre sapmalar / bulgular)
1. **I18 sorgusu:** planın `"parolanı sıfırla"` sorgusu PostgreSQL `turkish` yapılandırmasında eşleşmiyor (`sıfırla` → `sıfır` kökü, kayıt `sıfırlama` → `sıfırla`). Test `"parolanı sıfırlama"` ile yazıldı; "şifre" sorgusunun 0 sonuç vermesi (S-2) aynen doğrulandı. qa Q2 gerçek veride zaten `HYBRID` ile bakıyor, etkilenmez.
2. **I1:** `tsv` kökleri `'parol'` ve `'sıfırla'` (planın `'parola'` beklentisi stemmer çıktısıyla uyuşmuyordu).
3. **I21 sentetik veri:** planın "yakın" vektörleri (aynı eksende, eşit mesafeli) HNSW'de dejenere oluyor (ITERATIVE_SCAN da 0 satır buldu, scratch container'da tekrarlandı). Rastgele gürültülü vektörler (`VectorFixtures.around`, `random`) kullanıldı; bu durumda `POST_FILTER` < 10, `ITERATIVE_SCAN` = 10. Üretim kodunda hata değil, test verisi etkisi; planner/qa için bilgi: sentetik S-4 verisi tekrar eden eşit mesafeler içermemeli.
4. **I28:** açılış olayı context yüklenirken çalıştığı için, log doğrulaması `EmbeddingModelGuard.warnOnMismatch()` doğrudan çağrılarak yapıldı (aynı metot `ApplicationReadyEvent` dinleyicisi); `/actuator/health` UP ve vector/hybrid 409, keyword 200 doğrulandı.
5. **Test JVM'i Türkçe locale'de çalışabiliyor** (`toLowerCase()` → `ı`); testlerde `Locale.ROOT` kullanıldı. Üretim kodu zaten `Locale.ROOT` kullanıyor.
6. **Kaynak adı büyük/küçük harf:** `Workspace.addSource` `toLowerCase(Locale.ROOT)` kullandığı için "yardım merkezi" / "YARDIM MERKEZİ" gibi Türkçe `I/İ` farkları entity kuralında farklı sayılabilir (backend raporu madde 7'de bilinen davranış); `lower(name)` indeksi yakalıyor. Bu davranış test edilmedi (planda yok), sadece bilgi.
7. Üretim kodunda hata bulunmadı.

## Düzeltme
Yok.

STATUS: DONE
