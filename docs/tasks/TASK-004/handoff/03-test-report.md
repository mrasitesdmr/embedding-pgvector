TASK: TASK-004
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `backend-conventions`, `frontend-conventions`, `react-testing`. (Planın isteği doğrultusunda `spring-testing-fundamentals`, `spring-mvc-testing` ve `spring-jpa-testing` kurallarına uyuldu: `MockMvcTester`, `@MockitoBean`, `@DataJpaTest` + flush/clear.)

## Senaryo → test eşlemesi
Uygulama koduna dokunulmadı. Beklenen değerler plandan ve TASK-004 örnek senaryolarından alındı.

### Backend
| # | Senaryo / Kural | Test (dosya#metot) | Sonuç |
|---|-----------------|--------------------|-------|
| B1 | Kaynak başına en büyük `id`'li iş (D1) | `IngestionJobRepositoryTest#givenJobsOfSeveralSources_whenFindLatestBySourceIds_thenReturnsHighestIdPerSource`. A: FAILED, DONE, DONE; B: EMBEDDING; C: iş yok. Beklenen: A'nın son işi + B'nin işi, C boş (flush + clear sonrası) | Geçti |
| B2 | Liste ve detayda `latestJob` en yeni iş (K13) | `SourceIntegrationTest#givenSourceWithTwoJobs_whenListAndGetSource_thenLatestJobIsNewest` (ilk yükleme FAILED, ikincisi DONE `3.2`; seed 301'de `"latestJob": null` anahtarı var) | Geçti |
| B3 | Aktif iş ilerlemesi | `SourceIntegrationTest#givenJobEmbeddingNinetySixOfTwoHundredFourteen_whenGetSource_thenLatestJobShowsProgress` (`status INGESTING`, `progress` ≈ 0,458879 `within(1e-6)`, `statusUrl`) | Geçti |
| B4 | Başarısız sürüm yüklemesi (K12, D12) | `SourceIntegrationTest#givenVersionUploadFailsWithEmbeddingUnavailable_whenGetSource_thenStatusReadyAndLatestJobFailed` (`READY`, eski `3.1`, `FAILED`, `EMBEDDING_UNAVAILABLE`) | Geçti |
| B5 | Tek sorgu (N+1 yok) | `SourceServiceTest#givenPageOfThreeSources_whenListSources_thenLoadsLatestJobsOnce`, `#givenEmptyPage_whenListSources_thenDoesNotQueryJobs`, `#givenSourceWithJob_whenGetSource_thenQueriesBySingleIdAndAttachesJob`, `#givenSourceWithoutJob_whenGetSource_thenLatestJobIsNull`. Ek (PIT mutantı için): `#givenUnknownWorkspace_whenGetSource_thenThrowsWorkspaceNotFoundWithoutLookingUpSource` | Geçti |
| B6 | JSON şekli | `SourceControllerTest#givenSourceWithoutJob_whenGetSource_thenLatestJobIsNull`, `#givenSourceWithJob_whenGetSource_thenLatestJobHasContractFields` (14 alan) | Geçti |
| B6 | Sözleşme | `OpenApiDocumentationIntegrationTest#givenApplication_whenGetApiDocs_thenVersionAndLatestJob` (yeniden adlandırıldı): `info.version = 0.4.0`; `latestJob.anyOf[0].$ref = #/components/schemas/IngestionJobResponse`, `anyOf[1].type = null`, `latestJob`'ta kardeş `type` yok, `required`'da yok; `errorCode` enum'u 21 değer | Geçti |
| B13 | Beklenen kırılmalar | `SourceControllerTest` 10→11 parametreli `SourceResponse` düzeltildi; `OpenApiDocumentationIntegrationTest` `0.4.0` ve `latestJob` kontrolüne güncellendi | Geçti |

### Frontend (Vitest + RTL + MSW)
| # | Senaryo / Kural | Test (dosya#test) | Sonuç |
|---|-----------------|-------------------|-------|
| F1, F2 | Dosya ve kaynak türü kuralı (K2, K7, G1, G2, G4) | `uploadRules.test.ts` (`givenFile_whenValidateUploadFile_thenReturnsRuleMessage`, `givenSourceKind_...`, `acceptAttribute`). 20.971.520 B kabul, +1 B red, 0 B boş, `KILAVUZ.PDF`, `Şifre notları.markdown`, `arsiv.pdf.zip`, `README` | Geçti |
| F3 | Ad ve sürüm (D15) | `uploadRules.test.ts` (`validateSourceName`, `validateVersionLabel`: 80/81 karakter, boşluklu girdi, 40/41) | Geçti |
| F4 | Aşama metni, yüzde (K9) | `ingestionRules.test.ts` (`ingestionStageText`, `progressPercent`) | Geçti |
| F5 | Toast metni (D13) | `ingestionRules.test.ts` (`ingestionDoneMessage`: sürümlü, sürümsüz, 1.410 gruplama) | Geçti |
| F6 | İş hata nedenleri (AC-6) | `errorMessages.test.ts#ingestionFailureReasonFor` (7 kod + `null`/`undefined`/bilinmeyen), `ingestionRules.test.ts#ingestionFailureLine` | Geçti |
| F7 | Dosya boyutu | `numberFormat.test.ts#formatFileSize` (830 B, 1,5 KB, 2,4 MB, 20 MB, sınırlar) | Geçti |
| F8 | Multipart (D10) | `httpClient.test.ts#givenFormDataBody_whenRequest_thenSendsMultipartWithoutJsonContentType` | Geçti |
| K3 | API hatası → alan | `uploadFormErrors.test.ts` (VALIDATION_FAILED alanları, SOURCE_NAME_TAKEN, 4 dosya kodu, form kodları, ağ hatası) | Geçti |
| F10 | Yeni kaynak (K1, K4, S-3) | `CreateSourceDialog.test.tsx#givenValidNameAndMarkdown_whenAddSource_thenPostsMultipartClosesDialogAndListsIngestingSource` (ad kırpılmış, dosya adı ve içerik, odak "Kaynak adı"nda, "İşleniyor" + "Sıraya alındı") | Geçti |
| F11 | Geçersiz dosya (K2, S-4) | `CreateSourceDialog.test.tsx#givenInvalidFile_whenSelected_thenShowsWarningUnderFieldAndSendsNothing` (docx / 25 MB / 0 B) | Geçti |
| F12 | Boş ad | `#givenWhitespaceName_whenAddSource_thenShowsNameRuleAndFocusesName`; ek: `#givenNothingFilled_whenAddSource_thenAsksForNameAndFile` | Geçti |
| F13 | Backend hataları (K3) | `#givenBackendRejects_whenAddSource_thenShowsTurkishErrorInDialog` (SOURCE_NAME_TAKEN, UNSUPPORTED_FILE_ENCODING, FILE_TOO_LARGE, VALIDATION_FAILED+violations, SOURCE_LIMIT_REACHED), `#givenNetworkFailure_...`. `detail` ekranda yok; seçilen dosya ve ad korunuyor | Geçti |
| D3 | Gönderim sürerken | `#givenUploadInFlight_whenEscapeOrCancelPressed_thenDialogStaysOpenAndButtonsAreDisabled` | Geçti |
| F22 | Esc / Vazgeç / odak / Tab döngüsü (K15-K17) | `CreateSourceDialog.test.tsx#givenDialogOpenedByButton_whenEscapeOrCancel_thenClosesAndFocusReturnsToButton`, `#givenFocusOnLastOrFirstElement_whenTab_thenFocusWrapsInsideDialog` (Uygulama notu 2), `#givenReopenedDialog_whenOpened_thenFormIsReset`; `UploadVersionDialog.test.tsx#givenDialogOpenedFromRowButton_...` (odak satırdaki butona döner, sayfa detaya gitmez, etiketler `getByLabelText`) | Geçti |
| F14 | Buton görünürlüğü (K5, S-8) | `SourcesPage.test.tsx#givenSourceKinds_whenRendered_thenUploadVersionOnlyOnFileSources`; `SourceDetailPage.test.tsx#givenWebCrawlSource_whenOpenDetail_thenNoUploadVersionButton`. **Ters çevrilen eski kontroller:** F29 `givenDalgaSources_whenOpenSources_thenShowsKindsStatusesFooterAndCreateButton`, F37 (`givenPdfSource_whenOpenDetail_...` içinde buton artık var) | Geçti |
| F15 | Yeni sürüm diyaloğu (K6) | `UploadVersionDialog.test.tsx#givenPdfSource_whenUploadVersionWithLabel_thenDialogShowsInfoAndPostsFileAndVersion`, `#givenEmptyVersionLabel_whenUploadVersion_thenNoVersionPartIsSent` | Geçti |
| F16 | Türü uymayan dosya (G1) | `#givenPdfSource_whenSelectMarkdown_thenShowsKindRuleAndSendsNothing` (`accept=".pdf"`); ek: `#givenNoFile_whenSubmit_...` | Geçti |
| F17 | İş sürerken pasif (K8, S-7) | `UploadVersionDialog.test.tsx` (`with an active latest job`: liste ve detay), `#givenInProgressConflict_whenUploadVersion_thenShowsInProgressMessageAndRefreshesList`, ek: SOURCE_KIND_MISMATCH / VALIDATION_FAILED(version) / EMBEDDING_MODEL_MISMATCH | Geçti |
| F18 | İlerleme ve bitiş (K9-K11) | `IngestionProgress.test.tsx#givenJobAdvancingThroughStages_whenPolled_thenShowsEachStageThenToastHighlightAndStops` (5 aşama sırayla, `aria-valuenow=46`, toast, `isJustCompleted` 3 sn, toast 5 sn, toplam job isteği 5 ve artmaz) | Geçti |
| F19 | Başarısız iş (K12, S-5, S-6) | `#givenJobFails_whenPolled_thenShowsFailureReasonInRowAndStopsWithoutToast` (NO_TEXT_EXTRACTED, EMBEDDING_UNAVAILABLE, EMBEDDING_MODEL_NOT_FOUND) | Geçti |
| F20 | Yenileme / yeni sekme (K13, G5) | `#givenListWithActiveLatestJob_whenPageOpens_thenShowsProgressAndPollsJob`, `#givenListWithFinishedLatestJobs_whenPageOpens_thenNoPollingNoToastButFailureShown` | Geçti |
| F21 | Başka sayfadayken bitiş (K14) | `#givenActiveJob_whenUserGoesToSearchAndJobFinishes_thenToastOnSearchPageAndSearchWorks` | Geçti |
| F23 | Erişilebilirlik (K16) | `a11y.test.tsx` `upload and ingestion states` (5 test: yeni kaynak diyaloğu + dosya hatası, yeni sürüm diyaloğu, ilerleme + başarısızlık satırları, detay ilerlemesi, toast) | Geçti |
| F24 | Detay yeni sürüme geçer (K11) | `IngestionProgress.test.tsx#givenDetailOpenDuringJob_whenJobDone_thenInfoBoxesAndChunksShowNewVersion` | Geçti |
| F25 | Bağlantı kopması (G3, D16) | `#givenPollingNetworkError_whenBackendReturns_thenKeepsLastStageThenShowsInterrupted`; ek: `#givenJobNotFound_whenPolled_thenStopsPollingAndRefreshesList` | Geçti |

Planda olmayıp eklenenler: `isSourceIngesting` / `displayedSourceStatus` / `freshestJob` birim testleri (D11), 80 karakter ad sınırı, diyalogun her açılışta sıfırlanması, istek sürerken Esc'in kapatmaması (D3), `uploadFormErrors` tablosu.

### E2E (Playwright, gerçek sistem: Docker Compose + Ollama `bge-m3`)
| # | Senaryo | Test (`frontend/e2e/upload.spec.ts`) | Sonuç |
|---|---------|---------------------------------------|-------|
| E1 | Yeni sürüm + yenileme (AC-2, S-1) | `givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady` | Geçti |
| E2 | Yeni kaynak + kelime araması (S-3) | `givenWorkspaceWithFreeSlot_whenAddMarkdownSource_thenReadyAndKeywordSearchFindsIt` | Geçti |
| E3 | Geçersiz dosya (S-4) | `givenUnsupportedAndEmptyFiles_whenSelectedInNewSourceDialog_thenWarnsAndSendsNoUpload` | Geçti |
| E4 | Klavye, gerçek tarayıcıda Tab/Shift+Tab döngüsü, Esc, odak dönüşü (S-9) | `givenKeyboardOnly_whenOpenDialogTabAroundAndEscape_thenFocusStaysInsideAndReturns`; ek: `givenKeyboardOnly_whenOpenUploadVersionDialogAndEscape_thenFocusReturnsToRowButton` | Geçti |
| E5 | CSP (K19, AC-7) | `givenSite_whenOpenSearchSourcesAndDetail_thenNoCspViolationInConsole` (3000'e karşı; konsolda hiçbir error/warning ve pageerror yok, CSP başlığı değişmemiş) | Geçti |
| TASK-003 | E1, E3, E4, E5 | `search-events.spec.ts`, `search.spec.ts`, `workspace.spec.ts` (4 test) | Geçti |

## Mutation
- **Backend (PIT):** `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` ile `SourceService`, `SourceResponse`, `OpenApiConfig` hedeflendi: 35 mutant, 11 öldü.
  - `SourceService`: tek hayatta kalan `getExistingSource` içindeki `ensureWorkspaceExists` çağrısının silinmesiydi. `SourceServiceTest#givenUnknownWorkspace_whenGetSource_thenThrowsWorkspaceNotFoundWithoutLookingUpSource` eklendi (yeniden PIT koşusu yapılmadı, test geçiyor).
  - `SourceResponse`: tüm mutantlar öldü.
  - `OpenApiConfig` (24 mutant hayatta, `nullableLatestJobCustomizer` 5'i dahil): `OpenApiDocumentationIntegrationTest` Spring context'ini önbellekte tuttuğu için PIT'in sınıf değiştirmeleri context'e ulaşmıyor. Bu PIT + Spring context sınırıdır. `latestJob` şeması dolaylı olarak o test tarafından doğrulanıyor ama mutant bazında değil.
- **Frontend (Stryker):** Kurulu değil (`node_modules/@stryker-mutator` yok, `package.json`'da yok). Mutation skoru yok.

## Komutlar ve sonuçları
| Komut | Sonuç |
|-------|-------|
| `cd backend && ./mvnw verify` | Geçti. 377 test, 0 başarısız, 0 hata, 0 atlanan; JaCoCo "All coverage checks have been met"; BUILD SUCCESS |
| `cd frontend && npm test` (`vitest run`) | Geçti. 26 dosya, 361 test (önceki 208). Üst üste 12 tam koşu: 0 başarısız |
| `cd frontend && npm run test:coverage` | Geçti. 361 test; satır %96,21, ifade %95,8, dal %92,39 (eşik %80) |
| `cd frontend && npm run lint` | Geçti. Uyarı ve hata 0 |
| `cd frontend && npm run build` | Geçti. `tsc -b` + `vite build` |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | Geçti. 10 test (4 TASK-003 + 6 yeni). Üst üste 5 tam koşu: 0 başarısız |

### Ortam
- Ollama kapalıydı; `ollama serve` ile ben başlattım (`bge-m3` yüklü, günlük `/tmp/ollama-serve.log`). Sistem `docker compose -f deployment/docker-compose.yml up --build -d` ile açık bırakıldı (3 servis `healthy`). Veritabanı ilk koşuda temiz değildi (302 zaten `3.1`, hacim eskiydi); E2E her koşuda 302'ye `3.2` yükler ve bir kaynak ekler (D17). qa temiz veritabanı için `down -v` yapmalı.
- E2E testleri testlerin çalıştığı 5 koşu boyunca çalışan çalışma alanlarına 5 kaynak ekledi.

### Yenileme testinin süresi (plan riski)
- Örnek PDF (33 KB) ile gerçek Ollama'da yükleme yaklaşık **1,4 sn** sürüyor (5 koşuda 1,375-1,411 sn).
- Yenileme sonrası ilerleme çubuğu gönderimden yaklaşık **50-105 ms** sonra görünüyor. İş bitmeden yenileme penceresi yaklaşık 1,3 sn.
- Makine daha hızlıysa ya da Ollama modeli önceden ısınmışsa pencere daralır ve E1 kırılgan olabilir. Plan riski gerçek ama bu makinede 6 koşuda tutarlı geçti. Dar pencereyi kapatmak için uygulamada değişiklik gerekmiyor; kalıcı çözüm yavaşlatılmış bir embedding olurdu (kapsam dışı).

## Testlerde yapılan altyapı değişiklikleri
- `src/test/setup.ts`:
  - jsdom'da olmayan `HTMLDialogElement.showModal/close` taklidi (sadece `open` özniteliği + `close` olayı) ve belgedeki `Escape` tuşunu açık diyaloğa `cancel` olayı olarak ileten dinleyici. Gerçek odak tuzağı ve Esc davranışı E4'te gerçek tarayıcıda doğrulandı.
  - jsdom `File`'ı Node'un `fetch`'iyle çalışmıyor (`FormData`'daki dosya `[object Blob]` olarak gidiyordu). `fetch` MSW'den sonra sarılıp `FormData` Node `FormData`/`File`'a çevriliyor. Bu sadece test ortamı uyumudur; uygulama kodu gerçek tarayıcıda E2E'de doğrulandı.
- `src/test/multipart.ts`: multipart gövdesini ayrıştıran yardımcı (undici'nin `formData()` ayrıştırıcısı jsdom `File` ile çalışmıyor).
- `src/test/nodeBuffer.d.ts`: `node:buffer` tipi (`@types/node` tsconfig'de yok; `npm run build` için gerekli).
- `src/test/fixtures.ts`: `source()` varsayılanına `latestJob: null`, yeni `ingestionJob()`. `src/test/handlers.ts`: `POST .../sources`, `POST .../versions`, `GET .../ingestion-jobs/:jobId` (varsayılan 404 `INGESTION_JOB_NOT_FOUND`).

## `App.test` yönlendirme testi: kök neden
- `givenWorkspaceRootPath_whenOpen_thenRedirectsToSearch` ara sıra kalıyordu. Kök neden **testteydi, uygulamada değil**: `src/test/LocationProbe.tsx` URL'i `useEffect` (pasif effect) ile yazıyordu; başlık DOM'a girince `findByRole` hemen çözülüyor, effect henüz çalışmamışsa `currentUrl()` eski değeri (`/w/19`) okuyordu. Layout'a eklenen kaynak listesi isteği bu sırayı kaydırdı.
- Doğrulama: `useEffect` ile 15 koşuda 13'ü kaldı; `useLayoutEffect` ile 25 koşuda 0 (sonra tam takımın 12 koşusunda da 0). `LocationProbe` `useLayoutEffect`'e çevrildi. Uygulamada yarış ya da hata yok: başlık ancak `/w/19/arama` rotasında görünüyor.

## Çalıştırılamayanlar
Yok. E2E gerçek sisteme karşı çalıştırıldı (yukarıdaki ortam notlarına bakın). Stryker kurulu olmadığı için frontend mutation çalıştırılamadı. Ollama'nın kapalı olduğu durum (S-5) ve görsel PDF (S-6) E2E'de değil, qa canlı kontrollerinde (Q5, Q6) ele alınacak; bileşen düzeyinde F19 ve `errorMessages` testleri örtüyor.

## Düzeltme
Yok. Uygulama hatası bulunmadı.

STATUS: DONE
