TASK: TASK-003
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`, `spring-testing-fundamentals`, `spring-mvc-testing`, `frontend-conventions`, `react-testing`, `e2e-testing`.

## Önce yapılan düzeltmeler (backend test derlemesi)
- Eski constructor çağrıları yeni imzalara uyarlandı: `SearchControllerTest` (`SearchHitResponse` + `chunkNo`, `SearchEventResponse` + `limit`), `SearchServiceTest` (`SearchProperties` + `ZoneId`, `RetrievedHit` + `chunkNo` ×3, `SearchEvent.record` + `resultLimit`), `WorkspaceControllerTest` (`WorkspaceResponse` + `embeddingModels`).
- `SchemaMigrationIntegrationTest`: migration sayısı 6 → 7. `OpenApiDocumentationIntegrationTest`: `info.version` 0.3.0 (B13 olarak yeniden yazıldı).
- Beklenen değerler plandan geldi, koda bakarak değiştirilmedi.

## Senaryo → test eşlemesi
Backend (`backend/src/test/java/com/embedding/pgvector/...`):
| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| B1 (K18) | chunkNo üç modda | `search/SearchIntegrationTest#givenChunkNoSeven_whenSearchChunksInEachMode_thenHitCarriesChunkNoSeven` (KEYWORD/VECTOR/HYBRID) | geçti |
| B2 (K26) | kayıtta limit | `search/SearchIntegrationTest#givenSearchWithLimitFive_whenListSearchEvents_thenEventLimitIsFive` | geçti |
| B3 (K25, S-6, G4) | gün sınırları, İstanbul takvim günü | `search/SearchEventStatsIntegrationTest#givenEventsAroundIstanbulDayBoundaries_whenGetSearchEventStats_thenCountsByIstanbulCalendarDays` (sabit saat: yeni `support/FixedClockTestConfiguration`) | geçti |
| B4 | veri yok | `SearchEventStatsIntegrationTest#givenNoEvents_whenGetSearchEventStats_thenZeroCountsAndNullRates` | geçti |
| B5 | olmayan alan | `search/controller/SearchControllerTest#givenUnknownWorkspace_whenGetSearchEventStats_thenReturns404WorkspaceNotFound` (+ entegrasyon karşılığı `SearchEventStatsIntegrationTest#givenUnknownWorkspace_...`) | geçti |
| B6 | JSON şekli, null oranlar JSON'da var | `SearchControllerTest#givenStats_whenGetSearchEventStats_thenReturnsContractFields` | geçti |
| B7 (D2) | Clock'tan gün sınırı | `search/service/SearchServiceTest#givenClockAtIstanbulLateEvening_whenGetSearchEventStats_thenTodayStartsAtPreviousUtcEvening` (+ `givenUnknownWorkspace_whenGetSearchEventStats_thenThrowsWorkspaceNotFoundWithoutQuery`, `givenSevenEventsInWindow_..._thenRatesAreCountsOverWindowAndNullWhenEmpty`) | geçti |
| B10 (D5) | alanın modelleri | `workspace/WorkspaceIntegrationTest#givenChunksWithTwoModelTags_whenListWorkspaces_thenEmbeddingModelsSortedDistinct` | geçti |
| B11 (D4, G1) | tek parça (4 test + eklenen 5.) | `chunk/ChunkIntegrationTest#givenChunkOfSource_whenGetChunk_thenReturnsChunk`, `#givenChunkOfOtherSource_..._thenReturns404ChunkNotFound`, `#givenSourceOfOtherWorkspace_..._thenReturns404SourceNotFound`, `#givenReplacedChunk_..._thenReturns404ChunkNotFound`, eklenen: `#givenUnknownWorkspace_whenGetChunk_thenReturns404WorkspaceNotFound` | geçti |
| B12 | controller şekli | `chunk/controller/ChunkControllerTest#givenMissingChunk_whenGetChunk_thenReturns404ProblemDetail` (+ `#givenChunk_whenGetChunk_thenReturnsContractFields`) | geçti |
| B13 (AC-5) | sözleşme dokümanı | `config/OpenApiDocumentationIntegrationTest#givenApplication_whenGetApiDocs_thenContainsNewPathsAndChunkNotFound` (0.3.0, iki yeni path, 21 kod, `latestJob` yok, yeni alanlar/required) | geçti |
| B14 (D3, G4) | eski kayıt `limit` null | `search/SearchIntegrationTest#givenEventStoredWithoutLimit_whenListSearchEvents_thenLimitIsNull` | geçti |

Frontend (Vitest, dosya test ettiği dosyanın yanında; `frontend/src/...`):
| # | Senaryo / Kural | Test (dosya) | Sonuç |
|---|-----------------|--------------|-------|
| F1 (K12, D16) | arama metni kuralı | `features/search/model/searchRules.test.ts` (`canSearch`, tablo) | geçti |
| F2 (K16) | ayar kuralları | `searchRules.test.ts` (`validateEfSearch`, `validateLimit`, `toSearchRequest`) | geçti |
| F3 | snippet | `features/search/model/searchHitText.test.ts` (`buildSnippet`) | geçti |
| F4 (K7, G5) | vurgu segmentleri | `searchHitText.test.ts` (`toHighlightSegments`: Türkçe harf, taşan terim, HEADING/CONTENT, çakışma) | geçti |
| F5 (K26, G4) | rozet tonu | `features/search/model/resultCountTone.test.ts` | geçti |
| F6 (D21) | biçimler | `shared/format/numberFormat.test.ts`, `dateFormat.test.ts` | geçti |
| F7 (K6, AC-6) | 21 kod + NETWORK_ERROR + fallback | `shared/api/errorMessages.test.ts` | geçti |
| F8 (D14) | httpClient | `shared/api/httpClient.test.ts` (problem, violations, düz 500, errorCode'suz JSON, ağ hatası, Content-Type) | geçti |
| F9 (D8) | ayar saklama | `features/search/model/searchSettings.test.ts` | geçti |
| F10 (S-1, K3, K5, K17) | açılış | `app/App.test.tsx#givenRootPath_whenAppOpens_thenRedirectsToDalgaSearchWithEmptyState` | geçti |
| F11 (K10, G3, S-12) | olmayan / geçersiz alan | `App.test.tsx#givenUnknownWorkspaceId_...`, `#givenInvalidWorkspaceId_whenOpenSearch_thenShowsNotFoundWithoutRequest` (abc ve 0) | geçti |
| F12 (K9, S-11) | alan değiştirme | `App.test.tsx#givenSourcesPage_whenSelectKiyiNet_thenNavigatesAndListsKiyiNetSources` (+ detay ve kayıtlar sayfasından geçiş) | geçti |
| F13 (K9) | sonuç silinir | `App.test.tsx#givenSearchResult_whenWorkspaceChanged_thenResultIsCleared` | geçti |
| F14 (K11, K12, K23, D15) | gönderim | `features/search/pages/SearchPage.test.tsx#givenValidQuery_whenPressEnter_thenPostsFullRequestWithAgentChannel`, `#givenKeywordMode_...OmitsVectorSettings`, `#givenWhitespaceQuery_...` | geçti |
| F15 (K13) | arama sürerken | `SearchPage.test.tsx#givenPendingSearch_whenSubmittingAgain_thenSendsSingleRequest` | geçti |
| F16 (K14) | sık arananlar | `SearchPage.test.tsx#givenSuggestions_whenClickTs999_thenSearchesThatQuery` | geçti |
| F17 (K15) | bilgi satırı | `SearchPage.test.tsx#givenDefaultAndChangedSettings_whenRendered_thenInfoLineShowsModeLimitAndModel` | geçti |
| F18 (K16, K43) | gelişmiş ayarlar | `features/search/components/SearchSettingsPanel.test.tsx` (kapalı başlar, Kelime'de pasifler, geçersiz ef_search, sıfırlama, yeniden mount, bozuk storage) | geçti |
| F19 (K18, S-3) | sonuç listesi | `features/search/components/SearchResults.test.tsx#givenHybridResult_whenRendered_thenShowsRankTitleTagsSnippetAndSourceLine` | geçti |
| F20 (K22) | bağlantı | `SearchResults.test.tsx#givenHit_whenRendered_thenLinksToSourceDetailWithChunk` | geçti |
| F21 (K19, S-2) | sonuç yok | `SearchPage.test.tsx#givenKeywordModeNoHits_...`, `#givenHybridModeNoHits_...` | geçti |
| F22 (K20, S-4) | kısa kaldı | `SearchResults.test.tsx#givenShortVectorBranchWithPostFilter_...`, `#givenShortVectorBranchWithIterativeScan_...`, `features/search/model/searchNotices.test.ts` | geçti |
| F23 (K21, S-3) | teknik detay | `features/search/components/SearchTechnicalDetails.test.tsx` | geçti |
| F24 (K6, K24, S-10) | arama hataları | `SearchPage.test.tsx#givenSearchError_whenSearch_thenShowsTurkishMessage` (409/503/503/500), `#givenNetworkFailure_...`, `#givenValidationErrorOnQuery_...` | geçti |
| F25 (K7, G5) | düz metin | `SearchResults.test.tsx#givenHtmlInContentAndName_whenRendered_thenShownAsText` (+ sorgu) | geçti |
| F26 (K25, K26, S-6) | arama kayıtları | `features/search/pages/SearchEventsPage.test.tsx#givenStatsAndEvents_...`, `#givenNoEventsInLast7Days_whenOpen_thenRatesShowDash` | geçti |
| F27 (K26) | sayfalama | `SearchEventsPage.test.tsx#givenTwoPagesOfEvents_whenClickNext_thenRequestsSecondPage` | geçti |
| F28 (K27) | sayaç güncellenir | `SearchEventsPage.test.tsx#givenSearchCompleted_whenBackOnSearchEvents_thenTabCountAndTableUpdated` | geçti |
| F29 (K28, K29, D19, D20, G2) | kaynak listesi | `features/source/pages/SourcesPage.test.tsx` | geçti |
| F37 (K35-K37) | kaynak detayı | `features/source/pages/SourceDetailPage.test.tsx#givenPdfSource_...`, `#givenMoreChunks_whenClickShowMore_thenAppendsNextTwenty` | geçti |
| F38 (K22, K38, G1) | vurgulu parça | `SourceDetailPage.test.tsx#givenParcaQuery_...`, `#givenReplacedChunk_...` | geçti |
| F39 (K10) | olmayan kaynak | `SourceDetailPage.test.tsx#givenUnknownSource_whenOpenDetail_thenShowsSourceNotFound` | geçti |
| F40 (K43) | axe | `src/a11y.test.tsx` (arama sonuçlu + paneller açık, boş arama, kayıtlar, kaynaklar, detay, bulunamadı) | geçti |
| F41 (K1, K43) | sekmeler | `App.test.tsx#givenSearchEventsPath_whenRendered_thenTabMarkedCurrentAndKeyboardNavigable` | geçti |
| F42 (K1) | logo | `App.test.tsx#givenSourcesPage_whenClickLogo_thenGoesToSearch` | geçti |

E2E (Playwright, `frontend/e2e/`, sadece Chromium; gerçek sistem gerekir):
| # | Senaryo | Test | Sonuç |
|---|---------|------|-------|
| E1 | hybrid + kaynağa git + geri/ileri/yenile (plan düzeltme turu 1 sırası) | `e2e/search.spec.ts#givenDalgaWorkspace_whenHybridSuggestionAndOpenHit_thenSourceDetailHighlightsChunkAndBackShowsResults` | **koşulmadı** |
| E3 | alan değiştirme | `e2e/workspace.spec.ts#givenDalgaSelected_whenSwitchToKiyiNet_thenUrlSourcesAndSearchScopedToKiyiNet` | **koşulmadı** |
| E4 | olmayan alan | `e2e/workspace.spec.ts#givenUnknownWorkspaceUrl_whenOpen_thenShowsNotFound` | **koşulmadı** |
| E5 | kayıt sayısı | `e2e/search-events.spec.ts#givenSearchFromSite_whenOpenSearchEvents_thenTopRowIsAgentPanelAndCountIncreased` | **koşulmadı** |

Planda testi unutulmuş kurallar için ek testler: `GET chunk` bilinmeyen alan (B11 eki), bozuk/geçersiz `?parca=` (istek atılmaz), vurgulu parçanın listede tekrar etmemesi, `/w/:id` → `arama` yönlendirmesi, `/yok` → "Sayfa bulunamadı", workspace yükleme hatası + "Tekrar dene", kaynak yok boş durumu, birden çok embedding modeli altbilgisi, Kelime modunda geçersiz ef_search aramayı engellemez, `totalCount` biçimi (`1.410`).

## Mutation
- Backend: PIT (`pitest-maven`) yeni/değişen sınıflara (`SearchEventStatsResponse`, `ChunkService`, `SearchService`, `WorkspaceService`) ve yeni testlere sınırlı çalıştırıldı: 59 mutant üretildi, 58 öldü (%98), test gücü %100. Hayatta kalan yok; 1 mutant kapsamsız (yeni kural değil, `SearchService` içinde mevcut bir satır).
- Frontend: Stryker kurulu değil (planda D27: bu task'ta kurulmaz). Çalıştırılmadı. Yerine elle kontrol: axe testinin gerçekten ihlal yakaladığı (etiketsiz input) ve MSW'nin işlenmemiş isteği hataya çevirdiği geçici testlerle doğrulandı (geçici dosyalar silindi).

## Komutlar ve sonuçları
- `cd backend && ./mvnw verify` → **BUILD SUCCESS**, 366 test, 0 başarısız, 0 hata; JaCoCo eşiği geçti (önce 345 → +21 yeni test).
- `cd frontend && npm run build` → geçti (tip kontrolü dahil, test dosyaları ve `axe.d.ts` dahil).
- `cd frontend && npm run lint` → geçti, 0 uyarı.
- `cd frontend && npm test` → 20 dosya, **208 test geçti**, 0 başarısız.
- `cd frontend && npx vitest run --coverage` → satır **%94,64** (eşik %80), statement %94,27, branch %93,09, function %94,35.
- `cd frontend && npx playwright test --list` → 4 e2e test listelendi; e2e dosyaları ayrıca `tsc --strict` ile tip kontrolünden geçti.

## Çalıştırılamayanlar
- **E2E (E1, E3, E4, E5) koşulamadı.** Gerçek sistem gerekiyor (PostgreSQL + backend + Ollama + Vite/nginx). Ollama makinede açık (`bge-m3` yüklü), Docker çalışıyor, ama `deployment/` altındaki `.env` kontrolü ile `docker compose ... up -d postgres` içeren komutum izin sistemi tarafından reddedildi; reddi aşmaya çalışmadım. Çalıştırma yolu: `docker compose -f deployment/docker-compose.yml up -d postgres` → `cd backend && POSTGRES_PASSWORD=<.env değeri> ./mvnw spring-boot:run` → seed bitince (`Seed data completed`) `cd frontend && npm run dev` → `E2E_BASE_URL=http://localhost:5173 npx playwright test` (Chromium için gerekirse `npx playwright install chromium`). devops/qa compose'daki `frontend`'e (3000) karşı koşar. Beklenti: E1'de "Online İşlemler parolanı sıfırlama" ilk sonuçlarda "anlam" etiketiyle çıkar (TASK-002 S-2 ile aynı veri). Selector'lar component testlerinde doğrulanmış metin/role'lere dayanıyor; yine de ilk gerçek koşuda ince ayar gerekebilir.
- Frontend mutation (Stryker): kurulu değil.

## Bulgular (hata değil, bilgi)
- **msw 3 API değişikliği:** `server.listen({ onUnhandledRequest: 'error' })` msw 3'te yok; seçenek `onUnhandledFrame`. `src/test/setup.ts` bunu kullanıyor (işlenmemiş istek test düşürüyor, doğrulandı). `react-testing` skill'indeki örnek eski API'yi gösteriyor; skill metnini kullanıcı/lead güncelleyebilir.
- `vitest-axe` 0.1.0 tipleri `Vi.Assertion` genişletiyor, Vitest 5'te çalışmıyor; `src/test/axe.d.ts` ile `toHaveNoViolations` tipi `vitest` modülüne eklendi.
- Sekme erişilebilir adı bir boşluk içeriyor: `Arama kayıtları 0`. Testler `/^Arama kayıtları\s*0$/` kullanıyor.
- Uygulama kodunda bu turda hata bulunmadı.

## Düzeltme
Yok.

## Düzeltme turu 1
Kaynak: devops raporu `## Düzeltme`. E2E devops'un açık bıraktığı sistemde (`E2E_BASE_URL=http://localhost:3000`) koşuldu.

| Madde | Düzeltme | Sonuç |
|-------|----------|-------|
| E5 sayaç çakışması | `frontend/playwright.config.ts` → `workers: 1` (testler ortak backend durumuna bağlı) | geçti |
| E1 strict mode (2 öğe) | `e2e/pages/SearchPage.ts` `hitWithTitle` ilk eşleşmeyi alır (`.first()`) | geçti |
| E3 satır biçimi | UI plana ve koda uygun (`${kaynak} · parça N · #id`). Hata testteydi: `span` locator'ı iç içe span'ın birleşik metnini de alıyordu (başlıkla başlayan). Locator `span:not(:has(span))` yapıldı; UI'da sorun yok, frontend-coder sorumlu değil | geçti |

- `E2E_BASE_URL=http://localhost:3000 npx playwright test` → 4 test, **4 geçti**; `--repeat-each=3` → 12 koşu, hepsi geçti.
- Unit/component (208) ve backend (366) testleri bu turda değişmedi. `npm run lint` temiz.
- Çalıştırılamayan kalmadı (frontend Stryker hariç, D27).

STATUS: DONE
