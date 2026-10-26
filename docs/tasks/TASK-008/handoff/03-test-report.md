TASK: TASK-008
# Test raporu (Tur 1)

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`. Test kalıpları için mevcut `FakeEmbeddingIntegrationTest`, MSW ve Playwright desteği kullanıldı.

## Başlangıç koşusu (T-ALL, değişiklik yapmadan)
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, **417 test**, 0 hata; "All coverage checks have been met." |
| `cd frontend && npm run build` / `npm run lint` | geçti / geçti (uyarı yok) |
| `cd frontend && npm test` | **27 dosya / 405 test** geçti |
| `cd frontend && npm run test:coverage` | geçti, satır coverage **%96,36** (eşik %80) |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` (tam sistem `up --build -d`, 4 servis healthy) | **8 test** geçti |

Plandaki başlangıç sayılarıyla (417 / 405 / 8) aynı.

## Yeni testler sonrası koşu (Tur 1 sonu)
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | **432 test, 3 başarısız** (15 yeni): BUILD FAILURE. Kırmızılar: B-01 (`givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone`), B-02 (`givenModelReturns768Dimensions_…`) ve B-01'in yan etkisi (aşağıda) |
| `cd frontend && npm run build` / `npm run lint` | geçti / geçti |
| `cd frontend && npm test` | **27 dosya / 411 test, 4 başarısız** (6 yeni): hepsi B-03 |
| `npm run test:coverage` | Kırmızı testler yüzünden komut hata verdi; başlangıç coverage'ı %96,36 idi. Tur 2'de yeniden ölçülecek |
| `E2E_BASE_URL=http://localhost:3000 npx playwright test e2e/document-flow.spec.ts` | **17 test, 16 geçti, 1 başarısız** (B-04). 8 mevcut + ET1-ET8 + H3 testi |
| D4 override ile `E2E_OLLAMA_DOWN=1 npx playwright test e2e/ollama-down.spec.ts` | **2 test geçti** (OD1, OD2). Sonra backend eski hâline döndürüldü: `OLLAMA_BASE_URL=http://host.docker.internal:11434`, HYBRID arama `curl` ile 200 |

Not: `givenLargeFileWithManyChunks_whenCreateSource_thenEmbeddedInBatchesOf96AndAllWritten` (mevcut test) tam sette kırılıyor, tek başına geçiyor. [DÜZELTME: Tur 2'de bu neden yanlış çıktı, bkz. Düzeltme turu 1] Eski varsayım: BT4 başarısız olunca kurtarma tarafından kapatılan işin worker'ı sonradan `ensureActive`'e takılıyor ve `RESTART IDENTITY` ile aynı id'yi (1) alan sonraki testin işini bozuyor. B-01 düzelince kaybolması beklenir; Tur 2'de doğrulanacak, ayrı hata sayılmadı.

Düzenlenen mevcut testler: `httpClient.test.ts` içindeki `givenBodyWithoutErrorCode_whenRequest_thenUnexpectedError` (500 → 400) ve `givenJsonWithoutErrorCode_whenRequest_thenUnexpectedError` (502 → 404). Sebep: plan H2'nin beklenen davranışı 5xx gövdesizse `NETWORK_ERROR`; bu iki test eski davranışı 5xx ile kilitliyordu. 4xx için anlamları korunuyor.

## Yeni dosyalar
- Backend: `ingestion/IngestionIntegrationTest` (BT1, BT3, BT4, BT6 eklendi), `source/SourceIntegrationTest` (BT2), `exception/GlobalExceptionHandlerIntegrationTest` (BT5, yeni), `search/EmbeddingDimensionsIntegrationTest` (BT7, yeni).
- Frontend unit: `shared/api/httpClient.test.ts` (FT1), `features/search/pages/SearchPage.test.tsx` (FT2).
- E2E: `e2e/support/database.ts` (D5), `e2e/support/contract.ts` (D6, ajv + `additionalProperties: false`), `e2e/document-flow.spec.ts` (ET1-ET8 + H3 testi), `e2e/ollama-down.spec.ts` (OD1, OD2), `e2e/global-setup.ts` (`E2E_OLLAMA_DOWN=1` iken sıfırlamaz). `document-flow.spec.ts` `E2E_OLLAMA_DOWN=1` iken atlanır (D7).

## Senaryo → test eşlemesi
| # | Senaryo | Test (dosya#ad) | Sonuç |
|---|---------|-----------------|-------|
| BT1 | Türkçe dosya adı | IngestionIntegrationTest#givenTurkishFileName_whenCreateSource_thenFileNameKeptInJobAndSource | geçti |
| BT2 | Aynı ad (İ/i) aynı anda | SourceIntegrationTest#givenTwoParallelCreatesWithSameNameDifferentCase_whenSubmitted_thenOneAcceptedAndOneSourceNameTaken | geçti |
| BT3 | Üç farklı yükleme aynı anda | IngestionIntegrationTest#givenThreeParallelCreatesWithDifferentNames_whenAllFinished_thenThreeReadySourcesWithOwnChunks | geçti |
| BT4 | Kurtarma süren işi kapatmaz (H1) | IngestionIntegrationTest#givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone | **kırmızı (B-01)** |
| BT5 | Framework hataları (H6) | GlobalExceptionHandlerIntegrationTest (5 yol parametreli `…BadRequestProblem`, `givenUnknownPath…404`, `givenDeleteOnSource…405`, `givenPlainTextSearchBody…415`) | 8/8 geçti |
| BT6 | Metinsiz dosya (soru 2 = A) | IngestionIntegrationTest#givenWhitespaceOnlyMarkdownOrHeaderOnlyCsv_whenCreateSource_thenJobFailedNoTextExtractedAndNoChunks (bos.md, baslik.csv) | geçti |
| BT7 | Kolondan farklı boyut (H5) | EmbeddingDimensionsIntegrationTest#givenModelReturns768Dimensions_whenVectorSearchAndUpload_thenUploadFailsAsModelMismatchNotInternalError | **kırmızı (B-02)** |
| FT1 | Ağ geçidi hataları (H2) | httpClient.test.ts `givenGatewayErrorWithoutProblemBody_…` (500, 502, 504) ve `givenServerErrorWithProblemBody_whenRequest_thenKeepsErrorCode` (503, 500) | ilki **kırmızı (B-03)**, ikincisi geçti |
| FT2 | Arama sırasında geçit hatası | SearchPage.test.tsx#givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage | **kırmızı (B-03)** |
| ET1 | Örnek PDF üç katmanda | document-flow.spec.ts#givenReadySamplePdf_whenInspectApiAndDatabase_thenChunksHave1024DimEmbeddingsWithModelTag | geçti |
| ET2 | Gerçek içe almada yenileme (H8) | …#givenLargeMarkdown_whenReloadDuringEmbedding_thenProgressContinuesAndJobDone | geçti |
| ET3 | Yeni sürüm sürerken arama | …#givenVersionUploadRunning_whenSearch_thenOldChunksUntilDoneThenOnlyNew | geçti |
| ET4 | Üç yükleme aynı anda | …#givenThreeFilesPostedAtOnce_whenAllFinished_thenThreeReadySourcesWithOwnChunks | geçti |
| ET5 | Anlam araması, kayıt, istatistik, Sık aranan | …#givenVectorSearch_whenOpenSearchEventsPage_thenEventStatsAndPopularMatchDatabase | geçti |
| ET6 | Parça detayı ve eski parça | …#givenSearchHitOpened_whenSourceGetsNewVersion_thenChunkNotFoundNoticeAndHeadingKept | geçti |
| ET7 | Hata cevapları site üzerinden (29 satır, 22 MB hariç) | …#givenInvalidRequestsThroughSite_whenSent_thenProblemDetailsAndNoDatabaseChange | geçti |
| ET7/H3 | 22 MB gövde nginx üzerinden | …#givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails | **kırmızı (B-04)** |
| ET8 | Ekranda doğrulama ve çakışma mesajları | …#givenUploadsOnPage_whenNameTakenOrLimitReached_thenTurkishMessagesAndNoExtraSource | geçti |
| OD1 | Ollama kapalı arama | ollama-down.spec.ts#givenOllamaDown_whenHybridSearch_thenOllamaMessageAndKeywordSearchStillWorks | geçti |
| OD2 | Ollama kapalı yükleme | ollama-down.spec.ts#givenOllamaDown_whenUploadNewVersionOfMarkdown_thenFailureReasonAndOldChunksKept | geçti |

Plandan sapma: H3 satırı (22 MB) ET7 tablosundan ayrı bir teste alındı ve `serial` describe'ın sonuna kondu. Sebep: serial modda ET7 kırılırsa ET8 çalışmıyordu; hata ET7'nin diğer 28 satırını ve ET8'i gizlerdi.

Her E2E testi ekran, REST (`expectMatchesSchema` ile sözleşme, ProblemDetail dahil) ve DB (`docker compose exec psql`) assertion'ı taşır. İlk koşuda bütün REST cevapları `additionalProperties: false` ile sözleşmeye uydu (alan farkı çıkmadı).

## Hipotez sonuçları (H1-H8)
| # | Sonuç | Kanıt |
|---|-------|-------|
| H1 | **Hata (B-01)** | BT4 kırmızı: kurtarma bu süreçte kuyruğa verilen işi `FAILED` yapıyor (beklenen `DONE`, gerçek `FAILED`); log "Ingestion job is already finished: 1" |
| H2 | **Hata (B-03)** | FT1 (500/502/504) ve FT2 kırmızı: `UNEXPECTED_ERROR` dönüyor, beklenen `NETWORK_ERROR` |
| H3 | **Hata (B-04)** | 22 MB gövde 3000'de 413 `text/html` (nginx), beklenen `application/problem+json` `FILE_TOO_LARGE`. 20 MiB+1 bayt dosya doğru `FILE_TOO_LARGE` Problem Details dönüyor (ET7 satırı geçti) |
| H4 | Hata değil | BT1 ve ET8(a) geçti: ad ve dosya adı REST'te ve DB'de aynen; boş adda "Çalışan İzin Notları" |
| H5 | **Hata (B-02)** | BT7 kırmızı: 768 boyutlu model ile yükleme işi `INTERNAL_ERROR` ile bitiyor (`ERROR: expected 1024 dimensions, not 768`), beklenen `EMBEDDING_MODEL_MISMATCH`. Boş çalışma alanında VECTOR arama 200 döner (500 değil); parçası olan çalışma alanında 768 boyutlu sorgu vektörü SQL hatası verir, bu yol testle ayrıca denenmedi (parçaları 768 etiketli üretmek mümkün değil, çünkü yazma zaten düşüyor) |
| H6 | Hata değil | BT5 geçti: 5 yol 400 `BAD_REQUEST` `application/problem+json`; ET7 `/sources/abc` satırı geçti |
| H7 | Hata değil (soru 2 = A) | BT6 geçti: iş `FAILED` `NO_TEXT_EXTRACTED`, kaynak `FAILED`, 0 parça |
| H8 | Hata değil | ET2 geçti: gerçek içe almada `EMBEDDING` aşamasında yenileme sonrası ilerleme çubuğu düşmedi, DB `embedded_chunks` artmaya devam etti, iş `DONE` |

## Hata listesi
| ID | Belirti | Nasıl tekrarlanır | Sorumlu | Önem (D9) | Yakalayan test | Durum |
|----|---------|-------------------|---------|-----------|----------------|-------|
| B-01 | Süren iş, `recoverInterruptedJobs` çalışınca `INGESTION_INTERRUPTED` ile kapanıyor; worker sonra "Ingestion job is already finished" fırlatıyor. Açılışta kurtarma, Tomcat istek almaya başladıktan sonra çalışırsa bu pencerede açılan iş kaybolur | `./mvnw test -Dtest='IngestionIntegrationTest#givenJobSubmittedInThisRun*'` | backend-coder | Düşük (pencere dar, sadece açılışta) | IngestionIntegrationTest#givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone | AÇIK |
| B-02 | `embedding.dimensions` ≠ 1024 iken yükleme işi `INTERNAL_ERROR` ile bitiyor (SQL "expected 1024 dimensions"). README modeli değiştirmeyi öneriyor | `./mvnw test -Dtest=EmbeddingDimensionsIntegrationTest` | backend-coder (kod); doküman notu devops | Düşük | EmbeddingDimensionsIntegrationTest#givenModelReturns768Dimensions_whenVectorSearchAndUpload_thenUploadFailsAsModelMismatchNotInternalError | AÇIK |
| B-03 | Backend kapalı/yeniden başlarken (nginx 502/504 HTML ya da gövdesiz 500) ekranda "Beklenmeyen bir hata oluştu" görünüyor; beklenen "Sunucuya ulaşılamıyor…" | `npx vitest run src/shared/api/httpClient.test.ts src/features/search/pages/SearchPage.test.tsx` | frontend-coder | Orta | httpClient.test.ts#givenGatewayErrorWithoutProblemBody_whenRequest_thenNetworkErrorWithStatus (500/502/504); SearchPage.test.tsx#givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage | AÇIK |
| B-04 | 3000 üzerinden 21 MiB'ı aşan gövde nginx'in HTML 413'ünü döner; sözleşme 413 `FILE_TOO_LARGE` Problem Details (`application/problem+json`) der | `npx playwright test e2e/document-flow.spec.ts -g givenBodyOver21Mb` (tam sistem açık; serial olduğu için tümünü koşmak da olur) | devops (`frontend/nginx.conf`) | Düşük (ekran 20 MB üstünü zaten göndermiyor) | document-flow.spec.ts#givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails | AÇIK |

## Akış matrisi
| # | Akış | Ekran (UI) | REST | DB | Test | Sonuç |
|---|------|------------|------|----|------|-------|
| F1 | Sistemi açma | `/` → `/w/42/belge-yukle`; seçici yok | health UP (tam sistem 4 servis healthy) | Flyway/`vector` kontrolü devops P2/P3'te | mevcut E1 + sistem açılışı | geçti (DB kısmı devops'ta) |
| F2 | Çalışma alanını görme | "Henüz belge yüklenmedi" (E1) | `WorkspaceResponse` şemaya uydu; sayılar DB `count(*)` ile aynı | `workspaces`/sayılar | E1, ET1 | geçti |
| F3 | Belge yükleme (PDF, MD, TXT, CSV) | `?kaynak=` adresine gider | 202 + `Location` = `statusUrl`, `IngestionJobResponse` şemaya uydu | kaynak/iş satırları | E2, ET2, ET4, ET8 | geçti |
| F4 | İlerleme + yenileme | Gerçek EMBEDDING'de yenileme, değer düşmedi | iş aşamaları şemaya uydu | `embedded_chunks` arttı, sonda `= total_chunks = chunk_count = count(*)` | ET2 | geçti |
| F5 | Embedding yazılması | "Belge hazır · N parça" | `PageResponseChunkResponse`; hepsi `bge-m3@1024` | N≥22; `vector_dims` min=max=1024; tek model; `chunk_no` 0..N-1; `tsv` dolu | ET1, ET4 | geçti |
| F6 | Yeni sürüm | Diyalogla "2026.2" | 202; süren işte 409 `INGESTION_IN_PROGRESS` | eski id'ler siliniyor, `version` güncel, 2 iş satırı | ET3, ET6 | geçti |
| F7 | Kelime / anlam / hybrid arama | Anlam ve Hybrid: ilk 3'te "Yıllık izin"; Kelime (Ollama kapalıyken) çalışıyor | `SearchResultResponse` şemaya uydu; `vectorPlan` `idx_chunks_embedding` içeriyor | `idx_chunks_tsv` var | E3, ET1, ET5, OD1 | geçti |
| F8 | Arama kayıtları | `/arama-kayitlari` ilk satır son arama | `PageResponseSearchEventResponse` şemaya uydu | `mode`, `query`, `query_key` `yillik izin kaç gün?`, `result_count`≥1, `best_distance`≤0.49, `keyword_count` null | ET5 | geçti |
| F9 | İstatistikler | "Bugün" sayısı DB ile aynı | `SearchEventStatsResponse` şemaya uydu | Europe/Istanbul günü sayımı = API `todayCount` | ET5 | geçti |
| F10 | Sık aranan | liste sırası = REST sırası | `PopularQueriesResponse` şemaya uydu | gruplama `query_key` | E3, E4, ET5 | geçti |
| F11 | Belge detay | "Daha fazla göster" ile satır sayısı = `chunk_count` | `SourceResponse`, `PageResponseChunkResponse` | `chunk_count` | ET6 | geçti |
| F12 | Parça detayı ve eski parça | vurgu bildirimi; yeni sürümden sonra "Bu parça artık yok…" | 404 `CHUNK_NOT_FOUND` (`ProblemDetail`) | eski id yok; `top_chunk_heading` dolu kaldı | ET6 | geçti |
| Hata | Geçersiz istek, olmayan id, dosya, limit | Türkçe mesajlar (ad çakışması, 10 kaynak sınırı, boşluk arama) | 29 satırlık matris RFC 9457 + `errorCode` | hata sonrası tablo sayıları değişmedi | ET7, ET8, BT5 | geçti (22 MB hariç: B-04) |
| Ollama kapalı | arama ve yükleme | arama uyarısı + Kelime çalışıyor; yükleme "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" | 503 `EMBEDDING_UNAVAILABLE`; `latestJob` FAILED, kaynak READY | arama kaydı eklenmedi; eski parça id'leri aynı | OD1, OD2 | geçti |
| Eşzamanlılık | 3 yükleme, aynı ad, yükleme sürerken arama | ET4, ET3 | BT2, BT3 | tek kaynak/iş; her kaynak kendi parçası | BT2, BT3, ET3, ET4 | geçti |

## Mutation
Frontend'de Stryker kurulu değil. Backend'de PIT kurulu ama bu turda çalıştırılmadı: uygulama koduna dokunulmadı, yeni testler hata yakalama amaçlı ve her biri açık bir kırmızı/yeşil kanıta sahip. Tur 2'de düzeltmeler gelince PIT ilgili sınıflar için (`IngestionRecovery`, `EmbeddingModelGuard`) çalıştırılabilir.

## Çalıştırılamayanlar
- `down`/`up` kalıcılığı, `down -v`, backend yeniden başlatma sırasında iş (C5a/C5b), README ve `ollama.md` adımları: planda devops'un.
- Coverage yüzdesi yeni testlerle ölçülemedi (kırmızı testler komutu durduruyor). Tur 2'de.
- Not: Sistem şu an tester'ın bıraktığı veriyle açık (10 kaynak, ET testlerinin ürettiği parçalar ve arama kayıtları). devops kalıcılık (P1) için bunu kullanabilir.

## Düzeltme
**backend-coder**
- B-01: `IngestionRecovery.recoverInterruptedJobs` bu süreçte kuyruğa verilmiş işi kapatmamalı (önerilen yön: planın "Backend planı" H1 maddesi). Kırmızı test: `IngestionIntegrationTest#givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone`; mevcut `givenJobsLeftActive_whenRecoveryRuns_thenJobsFailedInterruptedAndSourcesRestored` yeşil kalmalı. Düzelince mevcut `givenLargeFileWithManyChunks_…` testinin tam sette geçmesi beklenir.
- B-02: Kolon boyutundan (`Chunk.EMBEDDING_DIMENSIONS`) farklı boyutlu vektör yazılmaya çalışılınca iş `FAILED` + `EMBEDDING_MODEL_MISMATCH` olmalı, `INTERNAL_ERROR` değil (arama tarafında da 409). Kırmızı test: `EmbeddingDimensionsIntegrationTest`.

**frontend-coder**
- B-03: `shared/api/httpClient.ts` `toApiError`: 5xx cevapta gövdede geçerli `errorCode` yoksa `ApiError(status, 'NETWORK_ERROR')`; problem gövdeli 5xx kodunu korur; 4xx gövdesiz `UNEXPECTED_ERROR` kalır. Kırmızı testler: `httpClient.test.ts` (500/502/504) ve `SearchPage.test.tsx#givenGatewayDown_…`.

**devops**
- B-04: `frontend/nginx.conf`: 413'te `application/problem+json` gövdesi `{"type":".../errors/file-too-large","title":"Content Too Large","status":413,"errorCode":"FILE_TOO_LARGE",…}` dönmeli (ör. `error_page 413` + `default_type`). Kırmızı test: `document-flow.spec.ts#givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails`. Aynı turda B-02 için README/`deployment/CLAUDE.md`'ye "boyut 1024 dışına çıkamaz" notu (R1).


## Düzeltme turu 1
Düzeltmeler: backend-coder (B-01, B-02), frontend-coder (B-03), devops (B-04 + README/deployment notu). Backend ve frontend imajları yeni kodla yeniden kuruldu (`up -d --build backend frontend`, healthy).

### Hata → test / sonuç
| ID | Yakalayan test | Sonuç |
|----|----------------|-------|
| B-01 | IngestionIntegrationTest#givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone (mevcut givenJobsLeftActive_whenRecoveryRuns… yeşil) | **DOĞRULANDI** |
| B-02 | EmbeddingDimensionsIntegrationTest#givenModelReturns768Dimensions_whenVectorSearchAndUpload_thenUploadFailsAsModelMismatchNotInternalError | **DOĞRULANDI** |
| B-03 | httpClient.test.ts#givenGatewayErrorWithoutProblemBody_whenRequest_thenNetworkErrorWithStatus (500/502/504); SearchPage.test.tsx#givenGatewayDown_whenSearchSubmitted_thenShowsServerUnreachableMessage | **DOĞRULANDI** |
| B-04 | document-flow.spec.ts#givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails (gerçek nginx, yeniden kurulan imaj) | **DOĞRULANDI** |

### Tur 1 notunun düzeltilmesi
Tur 1'de `givenLargeFileWithManyChunks_…` kırığını B-01'in yan etkisi diye yazmıştım; yanlıştı (backend-coder gösterdi). Gerçek neden test altyapısı: `PostgresIntegrationTest` her testte `TRUNCATE … RESTART IDENTITY` ile `chunks_id_seq`'i geri sarıyordu, Hibernate'in (`allocationSize = 50`) bellekte tuttuğu id aralığı yeniden dağıtılıyor ve tek transaction'da aynı `Chunk` id'si iki kez çıkıyordu (`DuplicateKeyException`). Yeni testler sıralamayı değiştirdiği için baseline'da görünmüyordu. Üretimde dizi geri sarılmaz, uygulama hatası değil. Test hatasıydı ve benim: `PostgresIntegrationTest` artık truncate öncesi `last_value`'yu okuyup `setval` ile geri koyuyor. Sonuç: test yeşil, tam sette de geçiyor.

### Komutlar ve sonuçlar (T-ALL, Tur 2)
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, **432 test, 0 hata**, "All coverage checks have been met." |
| `cd frontend && npm ci && npm run build && npm run lint` | geçti |
| `cd frontend && npm test` | **27 dosya / 411 test** geçti |
| `cd frontend && npm run test:coverage` | geçti, satır coverage **%96,37** (eşik %80) |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | **17 geçti, 2 atlandı** (`ollama-down`, `E2E_OLLAMA_DOWN` yok) |
| D4 override ile `E2E_OLLAMA_DOWN=1 npx playwright test e2e/ollama-down.spec.ts` | **2 geçti**; backend geri alındı (`OLLAMA_BASE_URL=http://host.docker.internal:11434`), HYBRID arama 200 |

Başlangıçtan fark: backend 417 → 432 (+15), frontend 405 → 411 (+6), E2E 8 → 17 + 2 Ollama-kapalı.

### Mutation (PIT, backend)
`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` → BUILD SUCCESS. 657 mutant üretildi, 569 öldürüldü (**%87**), test gücü **%89**, satır kapsamı %97 (mutasyona uğrayan sınıflar). Düzeltmeyle ilgili sınıflar: `IngestionRecovery` ve `EmbeddingModelGuard` için hayatta kalan mutant **yok**. Hayatta kalanlar (88): `OpenApiConfig` 20 (doküman metni), `TextChunker` 12, `CsvDocumentParser` 12, `GlobalExceptionHandler` 6, `IngestionJobRunner` 5, `PdfDocumentParser` 4, diğerleri 1-2. Bunlar bu task'ın kurallarıyla (TASK-008) ilgili değil, önceki task'ların kodu; kapatılmadı. Frontend: Stryker kurulu değil.

### Çalıştırılamayanlar
Kalıcılık (`down`/`up`, `down -v`), yeniden başlatma sırasında iş (C5a/C5b), README ve `ollama.md` adımları devops'ta. Yeni hata çıkmadı.


## Düzeltme turu 2 (qa B-05)
qa, nginx 413 gövdesindeki `"instance":"$uri"` alanının yolda `%22`, `%5C`, `%0A` olunca geçersiz JSON ürettiğini bildirdi. Test eklendi, mevcut nginx'e karşı kırmızı:

| ID | Belirti | Yakalayan test | Durum |
|----|---------|----------------|-------|
| B-05 | 22 MB gövde + yol `…/sources/x%22y/versions` (ve `x%5Cy`, `x%0Ay`) → 413 `application/problem+json` ama gövde `JSON.parse` edilemiyor ("Expected ',' or '}' after property value") | document-flow.spec.ts#givenOversizedBodyToPathWithEncodedQuote_whenPost_thenProblemDetailsBodyIsValidJson | **DOĞRULANDI** (Tur 3: nginx 413 gövdesinden `instance` çıkarıldı, imaj yeniden kuruldu, test yeşil) |

Ek test (qa'nın isteğe bağlı maddesi, yeşil): httpClient.test.ts#givenServerErrorWithJsonBodyWithoutErrorCode_whenRequest_thenNetworkError (502, 503). Frontend unit şimdi 27 dosya / **413 test**; `httpClient.test.ts` 16/16 geçti, lint geçti. E2E `-g EncodedQuote` ile koşuldu (1 kırmızı); tam E2E ve `global-setup` bu koşuda çalışma alanını boşalttı (sistem şimdi 0 belge).


### Tur 3 koşusu (T-ALL)
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, **432 test**, 0 hata, coverage eşiği karşılandı |
| `cd frontend && npm ci && npm run build && npm run lint` | geçti |
| `cd frontend && npm test` | **27 dosya / 413 test** geçti |
| `cd frontend && npm run test:coverage` | geçti, satır coverage **%96,37** |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | **18 geçti, 2 atlandı** (`ollama-down`); EncodedQuote testi dahil. İki kez koşuldu, ikisi de 18/18 |
| D4 override ile `E2E_OLLAMA_DOWN=1 npx playwright test e2e/ollama-down.spec.ts` | **2 geçti** |
| PIT | Yeniden koşulmadı: backend bu turda değişmedi (Tur 2 sonucu geçerli: %87, test gücü %89) |

Sistem sonda temiz kurulumla bırakıldı: `down -v` + `up --build -d`, 4 servis healthy, backend `OLLAMA_BASE_URL=http://host.docker.internal:11434` (override geri alındı), `GET /api/workspaces/42` → 0 kaynak, 0 parça.

Özet: B-01…B-05 hepsi DOĞRULANDI. Yeni hata yok.

STATUS: DONE
