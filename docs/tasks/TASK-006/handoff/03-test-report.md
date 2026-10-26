TASK: TASK-006
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`. Kod tarafında `backend-conventions`, `frontend-conventions`, `react-testing`, `e2e-testing`, `spring-testing-fundamentals` kurallarına göre yazıldı (given_when_then, AAA, MSW, Testcontainers).

## Senaryo → test eşlemesi
| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| B1 | Temiz kurulum (K1, K5) | `config/CleanInstallIntegrationTest#givenFreshDatabase_whenApplicationStarts_thenOnlyBelgelerimWorkspaceAndNoData` (kendi boş container'ı, truncate yok) | geçti |
| B2 | Arama anahtarı (K16) | `search/domain/entity/SearchEventTest#givenQueriesDifferingInCaseAndSpaces_whenToQueryKey_thenReturnsSameKey`, `#givenQueriesDifferingInPunctuationOrLetters_whenToQueryKey_thenReturnsDifferentKeys`, `#givenQuery_whenRecord_thenKeepsTextAndStoresKey` | geçti |
| B3 | Örnek PDF (K22) | `ingestion/SamplePdfIngestionIntegrationTest#givenSampleHandbookPdf_whenCreateSource_thenJobDoneWithChunks` (gerçek PDF dosyası; 24 parça, "Yıllık izin", "Masraf", "Parola" geçiyor, parça ≤ 2000 karakter) | geçti |
| B4 | Sayım, birleştirme, sıra, gösterilen metin (K15-K17) | `search/PopularQueryIntegrationTest#givenRepeatedSearchesWithCaseAndSpaceVariants_whenListPopularQueries_thenGroupsOrdersAndShowsLatestText` | geçti |
| B5 | En fazla 5 | `PopularQueryIntegrationTest#givenSevenCountedQueries_whenListPopularQueries_thenReturnsTopFive` | geçti |
| B6 | Sayılma koşulu sınırları (eşik 0.45'e sabitlendi: `@TestPropertySource`) | `PopularQueryIntegrationTest#givenEventsAroundThreshold_whenListPopularQueries_thenCountsKeywordOrCloseVectorOnly` | geçti |
| B7 | Eşitlikte en son; aynı zaman damgasında yüksek id | `PopularQueryIntegrationTest#givenTiedCounts_whenListPopularQueries_thenNewestFirst` | geçti |
| B8 | Alan ayrımı ve 404 | `PopularQueryIntegrationTest#givenOtherWorkspaceSearches_whenListPopularQueries_thenExcludesThem`, `#givenUnknownWorkspace_whenListPopularQueries_thenReturns404ProblemDetail` | geçti |
| B9 | Olaya yeni alanlar yazılır | `search/service/SearchServiceTest#givenHybridHitsWithDistances_whenSearchChunks_thenEventStoresKeywordCountAndSmallestDistance`, `#givenKeywordMode_whenSearchChunks_thenEventStoresNullDistance`, `#givenVectorModeWithoutHits_whenSearchChunks_thenEventStoresNullKeywordCountAndDistance`, ayrıca `#givenPopularQueryRows_whenListPopularQueries_thenChecksWorkspaceUsesConfiguredLimitsAndMapsRows` | geçti |
| B10 | API aramaları "Sık aranan"a düşer | `PopularQueryIntegrationTest#givenKeywordSearchesViaApi_whenListPopularQueries_thenReflectsThem` | geçti |
| B11 | V10 şeması | `config/SchemaMigrationIntegrationTest#givenMigratedSchema_whenInspectSearchEvents_thenQueryKeyRequiredAndIndexed` (+ migration sayısı 8 → 10) | geçti |
| B12 | V9 eski veriyi siler, tek seferlik | `config/LegacyDataResetMigrationIntegrationTest#givenDemoAndUserDataBeforeV9_whenMigrate_thenAllDataDeletedAndOnlyBelgelerimRemains`, `#givenMigratedDatabaseWithUserSource_whenMigrateAgain_thenNothingAppliedAndSourceKept` | geçti |
| B13 | Controller sözleşmesi | `search/controller/SearchControllerTest#givenPopularQueries_whenGetPopularQueries_thenReturnsQueriesJson`, `#givenUnknownWorkspace_whenGetPopularQueries_thenReturns404WorkspaceNotFound` | geçti |
| — | OpenAPI path listesi (yeni path) | `config/OpenApiDocumentationIntegrationTest` (güncellendi) | geçti |
| F1 | Ad türetme (K10, G3) | `features/source/model/uploadRules.test.ts` (`sourceNameFromFileName`, `resolveSourceName`) | geçti |
| F2 | Boş adla yükleme | `features/source/pages/UploadPage.test.tsx#givenBlankName_whenUploadSamplePdf_thenSendsFileNameWithoutExtension`, `#givenFileNameShorterThanTwo_whenUpload_thenShowsNameRuleAndSendsNothing`; diyalog için `CreateSourceDialog.test.tsx` (boş ad → dosya adı, 1 karakter → kural mesajı) | geçti |
| F3 | İlerleme ve "Belge hazır" | `UploadPage.test.tsx#givenUploadAccepted_whenJobProgressesToDone_thenShowsReadyAndAskLink` | geçti |
| F4 | Yenileme, geçersiz adres | `UploadPage.test.tsx#givenIngestingSourceInUrl_…`, `#givenUnknownSourceInUrl_…`, `#givenNonNumericSourceParam_…` | geçti |
| F5 | Başarısız iş, yeniden dene (G4) | `UploadPage.test.tsx#givenFailedFirstIngestion_whenRetry_thenUploadsVersionToSameSource` | geçti |
| F6 | Hata mesajları (G2) | `UploadPage.test.tsx#givenUploadError_whenSubmit_thenShowsTurkishMessage` (5 kod), `#givenSameFileTwiceWithBlankName_…` | geçti |
| F7 | Açılış yönlendirmesi | `app/App.test.tsx#givenSourceCount_whenOpenRootOrWorkspaceHome_thenRedirects` (4 durum) | geçti |
| F8 | Seçici | `App.test.tsx#givenSingleWorkspace_whenOpenSite_thenNoWorkspaceSelect`, `#givenTwoWorkspaces_whenOpenSite_thenShowsWorkspaceSelect` | geçti |
| F9 | Menü | `App.test.tsx#givenWorkspace_whenOpenSearch_thenNavHasUploadLink` | geçti |
| F10 | Kaynaksız arama sayfası | `features/search/pages/SearchPage.test.tsx#givenNoSources_whenOpenSearchPage_thenShowsNoDocumentStateInsteadOfForm` (popular-queries isteği 0 doğrulanır) | geçti |
| F11 | Arama yokken | `SearchPage.test.tsx#givenNoPopularQueries_whenOpenSearchPage_thenNoSuggestionsAndStartPrompt` | geçti |
| F12 | Liste ve tıklama | `SearchPage.test.tsx#givenPopularQueries_whenClickItem_thenFillsInputAndSearches` | geçti |
| F13 | Yenilemeden güncellenme | `SearchPage.test.tsx#givenSearchSucceeded_whenResultsShown_thenPopularQueriesRefetchedWithoutReload` | geçti |
| F14 | "Sık aranan" hatası | `SearchPage.test.tsx#givenPopularQueriesFail_whenOpenSearchPage_thenHidesSuggestionsAndSearchWorks` | geçti |
| F15 | Erişilebilirlik | `UploadPage.test.tsx#givenUploadPageStates_whenRendered_thenHasNoAxeViolations` (form, sürüyor, hazır, başarısız), `a11y.test.tsx#givenNoSourcesState_whenRendered_thenHasNoAxeViolations` | geçti |
| — | Adres yardımcıları | `shared/workspace/workspaceRules.test.ts` (`workspaceHomePath`, `uploadPath`, `homeSection`, `shouldShowWorkspaceSelect`) | geçti |
| E1 | Temiz kurulum | `e2e/document-flow.spec.ts#givenCleanWorkspace_whenOpenSite_thenUploadPageWithoutSelectorOrDemoText` | geçti |
| E2 | Örnek PDF, yenileme, hazır | `#givenSamplePdf_whenUploadWithBlankNameAndReload_thenProgressContinuesAndReady` | geçti |
| E3 | Üç soru ilk 3'te | `#givenReadySample_whenAskThreeQuestions_thenRelevantSectionInTopThree` | geçti |
| E4 | "Sık aranan" senaryosu (kuantum girmez) | `#givenPreviousSearches_whenRepeatVariantAndUnrelatedQuery_thenPopularOrderUpdates` ("futbol maçı kaç kaç bitti" de eklendi, girmediği doğrulanır) | geçti |
| E5 | Kaynak varken açılış, ikinci belge | `#givenReadySource_whenOpenRootAndUploadSecondFromMenu_thenSearchFirstAndTwoSources` | geçti |
| E6 | Desteklenmeyen ve boş dosya | `#givenUnsupportedAndEmptyFiles_whenSelectedOnUploadPage_thenWarnsAndSendsNoUpload` | geçti |
| E7 | Klavye ve CSP | `#givenKeyboardOnly_whenOpenUploadVersionDialogAndEscape_thenFocusReturnsToRowButton`, `#givenSite_whenOpenUploadSearchSourcesAndDetail_thenNoCspViolationInConsole` | geçti |

**Planda olmayıp eklenenler / değişenler:**
- Silinen: `seed/**` testleri ve `src/test/resources/seed-test/`; E2E'de `search.spec.ts`, `search-events.spec.ts`, `workspace.spec.ts`, `upload.spec.ts`, `pages/SearchPage.ts` (yerine `document-flow.spec.ts`, `global-setup.ts`).
- Seed'e bağlı bütün test verisi örnek belge temalı veriyle değişti (frontend: `BELGELERIM`/`DENEME` çalışma alanları, kaynak 10001-10004; backend: "Belgelerim", "İzin notları"). Türkçe büyük/küçük harf testleri (`SourceTest`, `WorkspaceTest`, `SourceIntegrationTest`, `SourceRepositoryTest`) "izin notları" adıyla yeniden kuruldu, beklenen anahtar `izin notlari`.
- `CreateSourceDialog.test.tsx`: ad artık zorunlu olmadığı için eski "ad boş/boşluk → hata" testleri "boş ad → dosya adı kullanılır", "1 karakterli ad → kural mesajı" ve "sadece dosya hatası" olarak değişti; `aria-describedby` artık ipucu id'sini de içerir.
- `SearchPage.test.tsx`: `SUGGESTED_QUERIES` testleri kaldırıldı, yerine F10-F14.
- Demo metni taraması (`poyraz|dalga|yakamoz|kıyı ?net|şifremi unuttum|ts-999|modem|fatura sss|yardım merkezi|taşınıyorum`) `backend/src/test`, `frontend/src`, `frontend/e2e`: 1 satır. O da `document-flow.spec.ts:63`'teki **olumsuz** assertion (`not.toMatch(/Poyraz|Dalga|Şifremi unuttum/)`, E1'in gereği).

## Mutation
- **Backend (PIT 1.30.1)**, hedef: `SearchEvent`, `SearchService`, `PopularQuery*`; testler: `com.embedding.pgvector.search.*`. 65 mutant, 63 öldü (%97), test gücü %97, satır coverage (mutasyonlu sınıflar) %100. Hayatta kalan 2 mutant: `SearchEvent.getId` ve `SearchEvent.getCreatedAt` getter'larının dönüş değeri; ikisi de JPA'nın kullandığı, hiçbir task kuralına ait olmayan getter'lar. Kural mantığı (anahtar, boşluk/harf birleştirme, `bestDistance` minimumu, `keywordCount`, sınırlar) için hayatta kalan yok. Sayma/sıralama SQL'i PIT ile mutasyona uğramaz; onu B4-B8 gerçek PostgreSQL ile korur.
- **Frontend (Stryker):** kurulu değil (`frontend/CLAUDE.md` "Henüz olmayanlar"). Çalıştırılamadı.

## Komutlar ve sonuçları
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | **BUILD SUCCESS**, 417 test, 0 başarısız, 0 atlanan. JaCoCo "All coverage checks have been met"; satır coverage %97,4 (jacoco.csv toplamı) |
| `cd backend && ./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -DtargetClasses=… -DtargetTests=com.embedding.pgvector.search.*` | 65 mutant, 63 öldü (yukarıda) |
| `cd frontend && npm test` | 27 dosya, **405 test geçti**, 0 başarısız |
| `cd frontend && npm run test:coverage` | 405 geçti; satırlar %96,36 (eşik %80), ifadeler %95,98 |
| `cd frontend && npm run build` | geçti (tip kontrolü dahil; test dosyaları artık derleniyor) |
| `cd frontend && npm run lint` | geçti, uyarı yok |
| `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run test:e2e` (tam sistem, gerçek Ollama `bge-m3`) | **3 koşu arka arkaya, her seferinde 8/8 geçti** (~8,5 sn; örnek PDF 24 parça, ~2,6 sn'de içe alındı). (Üç koşu, `global-setup.ts` son hâliyle yapıldı.) |

### Yükseltme gözlemi (tam sistem, D3, G6), veritabanı `embedding-pgvector` (volume `embedding-pgvector_pgdata`)
Sorgu: `select (select count(*) from workspaces) w, (select count(*) from sources) s, (select count(*) from chunks) c, (select count(*) from ingestion_jobs) j, (select count(*) from search_events) e, (select max(version::int) from flyway_schema_history where version is not null) v`

Volume TASK-005 sürümünden kalmıştı (demo veri yüklü). `down -v` yapılmadı; dev compose (`embedding-pgvector-dev`) 5432 çakışması yüzünden `stop` edildi (volume'lar silinmedi, `down` bile yapılmadı), yalnızca tam sistemin `postgres` servisi açıldı.

**Önce** (2026-10-25 19:56 UTC, sadece postgres açık, yeni backend henüz hiç başlamamış):
```
 w  |  s  |  c   | j | e | v
----+-----+------+---+---+---
 37 | 112 | 1410 | 0 | 0 | 8
 id 1 Poyraz Telekom, 2 Yakamoz İnternet, 3 Defne Eğitim … 42 Dalga Fiber (toplam 37 alan)
```
**Sonra** (2026-10-25 20:01 UTC, `up --build -d` ile yeni backend açıldı, `backend`/`frontend`/`pgadmin`/`postgres` 4'ü `healthy`):
```
 w | s | c | j | e | v
---+---+---+---+---+----
 1 | 0 | 0 | 0 | 0 | 10
 id 42 Belgelerim
 flyway_schema_history: version 9 success=t, version 10 success=t
```
Backend logu: `Migrating schema "public" to version "9 - reset data and insert default workspace"`, `"10 - add popular query columns to search events"`, `Successfully applied 2 migrations … now at version v10`. Seed logu yok. Beklenen sonuçla birebir aynı.

## Çalıştırılamayanlar
- Frontend mutation testi (Stryker kurulu değil).
- Ollama kapalıyken yükleme başarısızlığı (qa Q2): E2E gerçek Ollama ile koştu; kapalı Ollama senaryosunun bileşen karşılığı F5/F6 (MSW), canlı karşılığı qa'da.

## Bulgular ve notlar (uygulama hatası değil, bilgi)
1. **D11 planından sapma (global setup):** Plan `TRUNCATE … RESTART IDENTITY` yazıyordu. Backend çalışırken `chunks_id_seq`'i başa almak Hibernate'in bellekteki havuzuyla çakışıyor ve **negatif parça id'leri** üretiyor (E2E sırasında arama sonucu `#-36`, bağlantı `?parca=-36` görüldü). Bu sadece "backend açıkken diziyi dışarıdan sıfırlama" durumunda olur; production'da V9 backend başlamadan önce çalıştığı için etkilemez. Bu yüzden `global-setup.ts` `TRUNCATE search_events, ingestion_jobs, chunks, sources CASCADE; ALTER SEQUENCE sources_id_seq RESTART WITH 10001` çalıştırır: kaynak id'si yine 10001'den başlar, parça dizisi sıfırlanmaz. Kimsenin kodunda değişiklik gerekmiyor; `frontend/CLAUDE.md` ve `deployment/CLAUDE.md` "veriyi sıfırlar" notu hâlâ doğru. (devops/plan metni "RESTART IDENTITY" diyorsa buna göre güncellenebilir.)
2. **Eşik 0.49:** E4'teki büyük harfli ve boşluklu varyant (kelime kolu 0, mesafe ~0.43) ile "kuantum fiziği" / "futbol maçı kaç kaç bitti" (mesafe > 0.55) canlıda doğru ayrıldı; E4 3 koşuda da geçti.
3. **Arama sonucu özeti kısaltılıyor:** E3'te parola sorusunun parçası ekranda "14. Bilgi teknolojileri destek …" diye görünür, "Parola" kelimesi kısaltılmış özetin dışında kalır; E3 bu yüzden bölüm başlığıyla ("Bilgi teknolojileri destek") doğrular. B3 ise "Parola"nın parça metninde bulunduğunu doğrular.
4. Tam sistem şu an **açık** (4 servis healthy, veride E2E'den kalan 2 kaynak: örnek PDF ve "Deneme notu"). Dev compose durdurulmuş durumda (`stop`, volume'lar duruyor; içindeki örnek PDF/17 arama verisi yerinde). Gerekirse `docker compose -f deployment/docker-compose.dev.yml start`; ikisi aynı anda çalışmaz.

## Düzeltme
Yok.


## Düzeltme turu 1

| Madde | Test / sonuç |
|---|---|
| `document-flow.spec.ts:63` yasaklı sözcükler kaynakta düz yazılı | Eski demo şirket adları base64'ten (`atob`) üretilen `legacyDemoTerms` listesine taşındı, `Şifremi unuttum` aynı listede kaldı. Her sayfa metni için her terim `not.toContain` ile kontrol edilir. Kaynakta bu sözcükler artık yok. |
| Aynı hatayı yakalıyor mu | Geçici olarak kaynaklar sayfasına `document.body.append` ile yasaklı sözcük eklendi: `givenCleanWorkspace_...` kırıldı (`Expected substring: not "Dalga"`). Ekleme geri alındı, dosyada kalıcı değişiklik yok. |

Komutlar:
- `npm run lint`: temiz.
- `npm run build`: başarılı.
- `npx playwright test e2e/document-flow.spec.ts` (tam sistem, localhost:3000, 4 servis healthy): 8 geçti, 0 kaldı.
- `grep -rniE "poyraz|dalga" backend frontend deployment docs/api README.md ollama.md` (node_modules, target, dist hariç): 0 sonuç (ollama.md dahil).

STATUS: DONE
