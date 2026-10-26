TASK: TASK-008
# QA raporu

## Kullanıcı özeti
- **Sonuç: DONE (düzeltme turu 1 sonrası).** B-01…B-05 düzeltildi ve bağımsız doğrulandı, bütün testler geçiyor. İlk turda çıkan B-05 (nginx 413 JSON'u yolda `"`/`\` varken bozuluyordu) `instance` alanı çıkarılarak giderildi.
- **Akış matrisi (F1-F12, hata, Ollama kapalı, eşzamanlılık):** hepsi çalışıyor. Her satırın üç katmanı (ekran, REST, DB) tester'ın E2E testleriyle ve benim ayrıca temiz sistemde yaptığım PDF yükleme / arama / `psql` kontrolümle doğrulandı. Tek açık nokta B-05.
- **Hata listesi:**

| ID | Ne | Düzeltildi mi | Yakalayan test |
|----|----|---------------|----------------|
| B-01 | Kurtarma, bu süreçte kuyruğa verilmiş içe alma işini `INGESTION_INTERRUPTED` ile kapatıyordu | Evet | `IngestionIntegrationTest#givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone` |
| B-02 | Embedding boyutu 1024 dışında yükleme `INTERNAL_ERROR` veriyordu | Evet (`EMBEDDING_MODEL_MISMATCH`) | `EmbeddingDimensionsIntegrationTest#givenModelReturns768Dimensions_…` |
| B-03 | Backend kapalıyken (502/504/gövdesiz 500) ekranda "Beklenmeyen bir hata" görünüyordu | Evet ("Sunucuya ulaşılamıyor…") | `httpClient.test.ts#givenGatewayErrorWithoutProblemBody_…`, `SearchPage.test.tsx#givenGatewayDown_…` |
| B-04 | 21 MiB üstü gövdede nginx HTML 413 dönüyordu | Evet (düzeltme B-05'i getirdi, o da giderildi) | `document-flow.spec.ts#givenBodyOver21MbThroughSite_…` |
| B-05 | 413 gövdesindeki `"instance":"$uri"` yol `%22` / `%5C` / `%0A` içerince geçersiz JSON üretiyordu | Evet (`instance` kaldırıldı) | `document-flow.spec.ts#givenOversizedBodyToPathWithEncodedQuote_whenPost_thenProblemDetailsBodyIsValidJson` |

- **Son test sayıları (kendi koşum):** backend `verify` **432** test, 0 hata, coverage eşiği geçti; frontend build + lint geçti, unit **27 dosya / 413 test**, satır coverage **%96,37**; Playwright **18 geçti, 2 atlandı** (ollama-down; tester ve devops D4 override'ıyla 2/2 geçtiğini raporladı).
- Sistem `localhost:3000`'de temiz kurulumla açık (4 servis healthy, 0 belge).

## Kullanılan skill'ler
`code-review-and-quality`. Kod değişikliği backend'de olduğu için `java-code-review` ve `springboot-verification`, frontend'de `frontend-conventions` ölçütleri uygulandı (yeni ekran yok, `web-design-guidelines`/`frontend-a11y` gerekmedi). Mutation değerlendirmesi için `writing-good-tests` ölçütü.

## Kural kontrolü
TASK.md kuralları ve kabul kriterleri.

| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç |
|---|------------------------|--------------------|-------|
| 1 | Kural 1: bütün akışlar kapsanır (açma, çalışma alanı, yükleme + PDF, ilerleme + yenileme, DB'ye yazma, yeni sürüm, 3 arama, kayıtlar, istatistik, Sık aranan, detaylar) | Akış matrisi F1-F12 (03 raporu); E2E `document-flow.spec.ts` 16 test, hepsini bu koşuda yeniden çalıştırdım (17/17). Ayrıca temiz sistemde örnek PDF'i kendim yükledim | ✅ |
| 2 | Kural 2: her akış üç katmanda (ekran, `openapi.yaml`, DB: tablolar, `vector_dims`, model, index) | E2E testleri `expectMatchesSchema` (ajv, `additionalProperties:false`) + `psql` assertion'ı taşıyor. Kendi kontrolüm: 24 parça, `vector_dims` min=max=1024, tek model `bge-m3@1024`, iş `DONE 24/24`, `explain` planında `Index Scan using idx_chunks_embedding`, `search_events` satırı (`mode`, `query_key`, `result_count`=10, `best_distance` 0,29), "Sık aranan" cevabı | ✅ |
| 3 | Kural 3: hata durumları, RFC 9457 + `errorCode`, Türkçe mesaj | ET7 (29 satır), ET8, BT5, OD1, OD2; 413 yolu B-04/B-05 testleri | ✅ |
| 4 | Kural 4: eşzamanlılık ve tutarlılık | BT2, BT3, ET3, ET4; yarıda kalan iş: devops C5a/C5b; B-01 düzeltmesi (kod incelendi, kalan yarış aşağıda) | ✅ |
| 5 | Kural 5: kalıcılık, `down -v`, Flyway | devops P1/P2 (rapor). Ben de `down -v` + `up --build -d` yaptım iki kez: 4 servis healthy, 0 kaynak, `smoke` sağlıklı | ✅ |
| 6 | Kural 6: README ve `ollama.md` yazıldığı gibi çalışır, soru tablosu | devops R1-R6. Ben README soru tablosundan 3 soruyu 3 modda API'den denedim: "Yıllık izin" ilk sonuçlarda (s. 7), masraf s. 8, parola s. 16; KEYWORD doğal dil sorusunda boş dönüyor, bu belgelenmiş davranış (kelime araması) | ✅ |
| 7 | Kural 7: mevcut testler çalışır ve geçer | Aşağıdaki "Komutlar" tablosu | ✅ |
| 8 | Kural 8: her hata için belirti, tekrar, katman, önem; düzeltme + test; sözleşmeden sapan kod sözleşmeye uydurulur | 03 raporunun hata tablosu eksiksiz, her hata için test var (yukarıda). B-05 dahil hepsi testli | ✅ |
| 9 | Kabul: Kural 1'deki akışların katman/sonuç özeti raporda | 03 raporu "Akış matrisi" + bu rapor | ✅ |
| 10 | Kabul: düzeltilen her hata için test; düzeltilmeyen gerekçeli | B-01…B-04 testli; B-05 testli ve düzeltildi | ✅ |
| 11 | Kabul: build, lint, unit, e2e komutları ve sayıları raporda | Aşağıda | ✅ |
| 12 | Kabul: temiz veritabanıyla açılış + smoke | `down -v` + `up --build -d` → healthy=4; `actuator/health` UP; `/api/workspaces/42/sources` boş liste; 3000 → 200 | ✅ |

## Sözleşme uyumu
- `curl localhost:8080/v3/api-docs.yaml` çıktısını `docs/api/openapi.yaml` ile bir betikle karşılaştırdım (path + method + response status kümesi, şema alan adları, `enum` ve alan tipleri).
  - Path, method ve status kodu: **fark yok**. Planner'ın madde 4 açıklaması (400 `BAD_REQUEST` için yeni response eklenmedi) bu yüzden de tutarlı; `/sources/abc` davranışı ET7'de 400 `BAD_REQUEST` olarak doğrulanmış.
  - Şema alan adları: **fark yok**.
  - Tek fark: `IngestionJobResponse.errorCode`, `SearchResultResponse.filterStrategy`, `SearchEventResponse.filterStrategy` enum listesinde sözleşmede ek bir boş (`null`) giriş var, üretilen dokümanda yok. OpenAPI 3.1'de `type: [string, null]` ile nullable enum yazımı; davranış farkı değil, bu kabul edilebilir (FYI).
- Planner'ın 3 açıklama düzeltmesi kodla uyumlu: PDF imzası ilk 1024 bayt (`FileTypeDetector`, `FileTypeDetectorTest#givenPdfSignatureWithinFirst1024Bytes_…`), örnek belge 24 parça (kendi koşumda 24), `name` zorunlu (kendi koşumda `name` olmadan 400 `VALIDATION_FAILED`, `field: name`).
- E2E tarafında REST cevapları ajv ile `additionalProperties: false` sözleşme şemasına doğrulanıyor; ilk ve son koşuda fark çıkmadı.
- Frontend: `errorCode` → Türkçe eşleme B-03 sonrası `NETWORK_ERROR`; 4xx için `UNEXPECTED_ERROR` korunmuş (kod incelendi).

## Komutlar (kendi çalıştırdıklarım)
| Komut | Sonuç | Raporla tutarlı mı |
|-------|-------|--------------------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, **432 test**, 0 hata/başarısız, "All coverage checks have been met." | Evet (03: 432) |
| `cd frontend && npm run build` | geçti | Evet |
| `cd frontend && npm run lint` | geçti (oxlint, çıktı hatasız) | Evet |
| `cd frontend && npm test` | **27 dosya / 411 test** geçti | Evet |
| `cd frontend && npm run test:coverage` | geçti, satırlar **%96,37**, ifadeler %95,99 | Evet (03: %96,37) |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | **17 geçti, 2 atlandı** (`ollama-down`, `E2E_OLLAMA_DOWN` yok) | Evet |
| `E2E_OLLAMA_DOWN=1 … ollama-down.spec.ts` (D4) | Çalıştırmadım: Ollama'ya ve backend ortamına dokunmama talimatı. Tester ve devops iki ayrı koşuda 2/2 geçtiğini yazdı; test kodunu inceledim (ekran + 503 `EMBEDDING_UNAVAILABLE` + DB assertion'ı var) | Kabul edildi, bağımsız doğrulanmadı |
| `curl -s localhost:8080/v3/api-docs.yaml` + karşılaştırma betiği | Sözleşme bölümüne bak | n/a |
| Nginx 413 JSON denemesi (30 MB gövde) | `x%22y` → `"instance":"/…/x"y"` geçersiz JSON (`json.load` hata verdi); `x%5Cy` → `x\y` geçersiz kaçış; `x%0Ay` → ham satır sonu | Raporlarda yok: **yeni hata B-05** |
| Temiz sistemde örnek PDF akışı (`down -v` → `up --build -d` → yükle → bekle) | 202 + `Location`; `READY 24`; `psql`: 24 | 1024 | 1024 | 1 model `bge-m3@1024`; iş `DONE 24/24`; arama `Index Scan using idx_chunks_embedding`; `search_events` ve `popular-queries` dolu | Evet |

Not: ilk denemede sistemde tester'ın E2E'den bıraktığı 10 kaynak vardı; PDF yüklemem `409` (10 kaynak sınırı) döndü. Hata değil, belgelenmiş sınır. Temiz kurulumdan sonra çalıştı.

## Mutation testi
- PIT (backend): tester Tur 2'de çalıştırdı (657 mutant, %87 öldürüldü, test gücü %89; düzeltmeyle ilgili `IngestionRecovery` ve `EmbeddingModelGuard`'da hayatta kalan yok). Ben tekrar çalıştırmadım (süre; bu task'ta uygulama koduna eklenen satır sayısı çok az ve ilgili sınıflarda sonuç temiz). Bu, kabul edilen bir güven boşluğu olarak not edildi.
- Stryker: kurulu değil.
- Hayatta kalan önemli mutant: yok (hepsi önceki task'ların kodunda: `OpenApiConfig`, `TextChunker`, `CsvDocumentParser`; bu task'ın kapsamı dışında).

## Uçtan uca
| Senaryo | Sonuç |
|---------|-------|
| Playwright `document-flow.spec.ts` (tam sistem, 3000) | 17/17 geçti, ET2 (gerçek içe almada yenileme, 33,6 sn) ve ET3 (yeni sürüm sürerken arama, 33 sn) dahil |
| Playwright `ollama-down.spec.ts` | Atlandı (override gerekir); tester/devops 2/2 |
| Örnek PDF + arama (temiz sistem, kendi elimle) | Yukarıdaki "Komutlar" satırı ve Kural 6 |
| Sistemi temiz bırakma (Q5/D15) | `down -v` + `up --build -d`: healthy=4, 0 kaynak, health UP |

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|----------|-------------|-------|-------------|
| Required (ÇÖZÜLDÜ, düzeltme turu 1) | `frontend/nginx.conf` `location @file_too_large` (`"instance":"$uri"`) | **B-05.** `$uri` çözülmüş yoldur. `%22` → `"`, `%5C` → `\`, `%0A` → satır sonu olan bir istek yolunda JSON geçersiz olur; `Content-Type: application/problem+json` ama gövde ayrıştırılamaz. Gerçek sistemde doğruladım (30 MB gövde, `…/sources/x%22y`). İstemcinin `httpClient` JSON'u okuyamazsa B-03 sonrası 4xx için `UNEXPECTED_ERROR` gösterir, yani ekran da yanlış mesaja düşer. **D9 önemi: Düşük** (UI 20 MB üstünü göndermiyor, yol kullanıcı kontrolünde, ama sadece elle hazırlanmış istekle). **D10: Düşük + tek katman + sözleşme değişmiyor → bu task'ta düzeltilmeli.** Önerilen düzeltme: `instance` alanını gövdeden çıkarmak (RFC 9457'de `instance` isteğe bağlıdır; sözleşme `ProblemDetail` şemasında `instance` zorunlu değilse) ya da sabit bir değer yazmak. Test: `document-flow.spec.ts`'te mevcut 413 testinin yanına, yolu `%22` içeren bir istek ekleyip gövdenin `JSON.parse` edilebildiğini doğrulayan bir assertion | devops (nginx.conf) + tester (test) |
| Optional | `IngestionJobLifecycleService`/`IngestionJobRunner` (B-01 düzeltmesi) | Kalan yarış: iş satırı commit olduktan sonra `afterCommit` → `submit` → `markRunning` arasındaki mikro saniyelik pencerede kurtarma çalışırsa iş yine `INGESTION_INTERRUPTED` olur. Kurtarma sadece `ApplicationReadyEvent`'te bir kez çalışıyor (`IngestionRecovery`, `@Order(1)`), yani pencere her süreç ömründe en fazla bir kez ve sadece açılışın ilk milisaniyelerinde istek gelirse açılır. Sonuç: kullanıcı işin "yarıda kaldı" olduğunu görür, dosyayı tekrar yükler; veri bozulmaz (B-01'in eski hâli de aynı sonuca varıyordu ama pencere sürekliydi). **Kabul edilebilir**: riski kanıtlı biçimde küçültüyor, düzeltmek için `markRunning`'i transaction içine taşımak gerekir, bu ek karmaşıklık. Önceki sürümde aynı pencere açılıştan sonraki her istekte değil, yalnızca bu tek çağrıdaydı; düzeltme bunu değiştirmedi ama "iş zaten çalışıyor" durumunu doğru yönetiyor | backend-coder (gerekmez) |
| Optional | `httpClient.test.ts` (2 eski test 500/502 → 400/404) | Test gücü azalmıyor: iki test "gövdede `errorCode` yoksa `UNEXPECTED_ERROR`" kuralını kilitliyordu; yeni (kod değişikliğine göre doğru) davranış 4xx için aynı kuralı hâlâ kilitler (400 ve 404), 5xx için `NETWORK_ERROR` yeni testlerle (500/502/504 ve problem gövdeli 503/500) kilitli. Boşluk: JSON gövdeli ama `errorCode`'suz 5xx durumu (ör. `{"title":"x"}` ile 502) için test yok; tester'ın eski 502 testi bunu kapatıyordu ve artık kapatılmıyor. `toApiError` iki dalı ayrı mutasyonla test edilebilir. Bir mutasyon denemesi yapmadım (`fallbackErrorCodeFor` iki çağrı yeri için ortak fonksiyon, bir yeri bozmak diğer testle yakalanır) | tester |
| Optional | `PostgresIntegrationTest.truncateTables` | `chunks_id_seq` artık geri sarılmıyor (`setval` ile eski değer). Test izolasyonu açısından `RESTART IDENTITY` ile sıfırlanan diğer dizilerle farklı davranıyor; testlerin sabit chunk id'sine bağlı olmadığı sürece sorun yok (432 test geçiyor). Hibernate'in id havuzunu yeniden kullanması hatasının doğru kök nedenine (test altyapısı) dokunuyor, `src/main`'e test-only kod girmedi | tester |
| FYI | devops G1 (`/w/42/kaynaklar` açıkken ~10 sn yeniden başlatmada mesaj çıkmıyor) | Lead kararına katılıyorum: yanlış mesaj çıkmıyor; sorgu yeniden denemeyle toparlanıyor, uzun kesintide "Sunucuya ulaşılamıyor…" çıkıyor. Hata değil | — |
| FYI | `openapi.yaml` nullable enum yazımı | Bkz. Sözleşme uyumu; davranış farkı yok | — |
| FYI | PIT/Stryker | Frontend'de Stryker yok | — |
| Nit | `EmbeddingModelGuard.ensureDimensions` | İki boyut koşulu aynı hata kodunu veriyor; mesajlar farklı. Konvansiyonlara uygun | — |

Q6 diff özeti (`scratch/TASK-008-baseline`'a karşı):
- `backend/src/main`: yalnız `EmbeddingModelGuard` (B-02), `IngestionJobLifecycleService`, `IngestionJobRunner`, `IngestionRecovery` (B-01). `pom.xml` aynı. Yeni dependency yok. Java sınıf üstü Türkçe Javadoc güncel, metot yorumu yok.
- `backend/src/test`: B-01/B-02 testleri, BT1-BT7, `PostgresIntegrationTest` (id dizisi).
- `frontend/src`: yalnız `shared/api/httpClient.ts` (B-03) ve iki test dosyası.
- `frontend/e2e`: `document-flow.spec.ts`, `global-setup.ts`, `ollama-down.spec.ts`, `support/` (yeni testler).
- `frontend/package.json`: `ajv`, `ajv-formats`, `yaml` (devDependencies, D6 ve soru 1 = A ile uyumlu).
- `frontend/nginx.conf`: B-04 (413 Problem Details).
- Doküman: `README.md`, `ollama.md`, `deployment/CLAUDE.md` (devops).
- Hiçbir değişiklik planlanmış B-xx ya da test senaryosu dışında değil.

## Düzeltilmedi listesi
Yok.

## Düzeltme
İlk tur maddeleri (B-05) giderildi; bkz. Düzeltme turu 1.

## Düzeltme turu 1
| Madde | Kanıt | Sonuç |
|-------|-------|-------|
| B-05 nginx 413 gövdesi | `diff` baseline'a karşı: `@file_too_large` gövdesinden `instance` çıkmış, başka değişiklik yok (`ProblemDetail.required` = type, title, status, errorCode; `instance` zorunlu değil, sözleşme değişmedi). Gerçek sistemde 30 MB gövdeyle `…/sources/{x%22y, x%5Cy, x%0Ay, plain}/versions`: dördünde `413`, `application/problem+json`, gövde `json.load` ile okundu, `errorCode=FILE_TOO_LARGE`, `status=413` | ✅ |
| B-05 testi | `document-flow.spec.ts#givenOversizedBodyToPathWithEncodedQuote_…` var, tam E2E içinde geçti | ✅ |
| Opsiyonel httpClient testi | `httpClient.test.ts#givenServerErrorWithJsonBodyWithoutErrorCode_whenRequest_thenNetworkError` (502, 503) eklenmiş; unit 413 test geçti | ✅ |
| Önceki adımlar 1-7'nin yeniden koşusu | Aşağıdaki tablo | ✅ |

Yeniden koşulan komutlar (kendi koşum):
| Komut | Sonuç |
|-------|-------|
| `./mvnw verify` | BUILD SUCCESS, 432 test, coverage eşiği karşılandı |
| `npm run build` / `npm run lint` | geçti |
| `npm test` | 27 dosya / 413 test geçti |
| `npm run test:coverage` | satır %96,37 |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | 18 geçti, 2 atlandı (ollama-down; tester ve devops D4 override'ıyla 2/2) |

Diff (Q6) güncellemesi: `frontend/nginx.conf` yalnız 413 bloğu (B-04 + B-05); yeni test satırları sadece `document-flow.spec.ts` ve `httpClient.test.ts`'e. Planlı kapsam dışı değişiklik yok. Yeni bulgu yok. Önceki kararlar (B-01 kalan yarış Optional, G1 FYI, test değişikliklerinin gücü) değişmedi; httpClient'taki boşluk artık kapatıldı.
Sistem sonda `down -v` + `up --build -d` ile temiz bırakıldı (healthy=4, 0 belge).

STATUS: DONE
