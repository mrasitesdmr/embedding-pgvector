TASK: TASK-009
# Test raporu

## Kullanılan skill'ler
`writing-good-tests`, `frontend-conventions`, `react-testing`, `e2e-testing`.

## İlk işler (lead kararı)
- `src/test/fixtures.ts:8`: `ChunkResponse` import'u `../shared/chunk/types.ts`'e çevrildi. `npm run build` geçti.
- Kırılan 2 test planına göre güncellendi: `SearchResults.test.tsx` (SR1) ve `SearchPage.test.tsx` › `givenHitsAndSearchEventStats_whenSearchCompletes_thenResultIsKeptAfterVisitingSourceAndGoingBack` (planın listesinde yoktu; artık sonuç `button`, kaynağa panelin "Kaynakta göster" bağlantısıyla gidiliyor, geri dönüşte sonuç `button` olarak aranıyor).

## Senaryo → test eşlemesi
| # | Senaryo / Kural | Test (dosya#test) | Sonuç |
|---|---|---|---|
| HT1 | K2, K4 tam metin segmentleri | `features/search/model/searchHitText.test.ts#givenContentLongerThanSnippet_whenHitContentSegments_thenWholeContentWithMatchesAfterSnippetLimit` | geçti |
| HT2 | G4 Türkçe harf + emoji | `searchHitText.test.ts#givenTurkishLettersAndEmojiBeforeTerm_whenHitContentSegments_thenMarkIsExactWord` | geçti |
| HT+ | Başlık segmentleri (K2/K4); `HEADING`/`CONTENT` ayrımı; kaynak satırı | `searchHitText.test.ts#givenNullHeading…`, `#givenHeadingTermAndContentTerm…`, `#givenHeadingTermOnly…`, `#givenHit_whenHitSourceLine…` | geçti |
| DT1 | K6 dışına tıklama | `shared/components/Dialog.test.tsx#givenCloseOnBackdropClick_whenBackdropOrContentClicked_thenClosesOnlyOnBackdrop` (+ `…ButCannotClose…` eklendi) | geçti |
| DT2 | Varsayılanlar korunur | `Dialog.test.tsx#givenDefaultProps_whenBackdropClicked_thenStaysOpenAndHasNoCloseButton` | geçti |
| DT3 | × düğmesi | `Dialog.test.tsx#givenHasCloseButton_whenCloseButtonClicked_thenOnCloseCalled`, `#givenHasCloseButtonAndCannotClose_whenRendered_thenCloseButtonDisabled` | geçti |
| DT4 | D9 odak dönüşü | `Dialog.test.tsx#givenReturnFocusRef_whenDialogUnmounted_thenFocusMovesToRefElement` (+ `#givenNoReturnFocusRef…ReturnsToOpener`) | geçti |
| DT5 | K8 Tab döngüsü | `Dialog.test.tsx#givenOpenDialog_whenTabOnLastAndShiftTabOnFirst_thenFocusWraps` | geçti |
| DT+ | `title: ReactNode` ile erişilebilir ad | `Dialog.test.tsx#givenReactNodeTitle_whenRendered_thenDialogNamedByTitleText` | geçti |
| StrictMode | frontend-coder notu: `handleNativeClose` | `Dialog.test.tsx#givenAlwaysOpenDialogInStrictMode_whenCleanupCloseEventArrivesAfterReopen_thenOnCloseNotCalled` (jsdom taklidi `close` olayını eşzamanlı yolladığı için testte `close` olayı gerçek tarayıcı gibi microtask'a ertelenir) + `#givenOpenDialog_whenNativeCloseEventWhileClosed_thenOnCloseCalled` | geçti |
| HD1 | K2, K3 tam metin, başlık, kaynak satırı | `features/search/components/SearchHitDialog.test.tsx#givenLongMultiParagraphHit_whenOpened_thenShowsTitleSourceLineAndWholeContent` | geçti |
| HD2 | Başlıksız | `SearchHitDialog.test.tsx#givenHitWithoutHeading_whenOpened_thenTitleIsUntitledChunk` | geçti |
| HD3 | K4, S2 vurgu tam metinde + başlıkta | `#givenKeywordHitWithTermsBeyondSnippet_whenOpened_thenMarksAtAllOffsets` | geçti |
| HD4 | K5 etiketler | `#givenMatchedBy_%j_whenOpened_thenShowsMatchingTags` (it.each 3) | geçti |
| HD5 | K12 HTML metin olarak | `#givenHtmlInContent_whenOpened_thenRenderedAsText` | geçti |
| HD6 | K11, S4 parça yok | `#givenChunkNotFound_whenOpened_thenShowsChunkGoneMessageAndHidesText` | geçti |
| HD7 | `SOURCE_NOT_FOUND` (+ `WORKSPACE_NOT_FOUND` eklendi) | `#givenSourceNotFound_…`, `#givenWorkspaceNotFound_…` | geçti |
| HD8 | G3 kontrol başarısız | `#givenChunkCheckFails_%s_whenOpened_thenKeepsTextWithoutAlert` (it.each: 500, ağ hatası) | geçti |
| HD9 | K9 bağlantı + tek varlık isteği | `#givenOpenedHit_whenRendered_thenSourceLinkPointsToHighlightedChunkAndChecksExistenceOnce` (+ `#givenChunkUrlOfOtherSource…` istek yolu) | geçti |
| HD10 | K6 kapatma yolları | `#givenOpenedHit_whenClosedBy_%s_thenOnCloseCalledOnce` (it.each 4: Paneli kapat, Kapat, Escape, backdrop) + `#givenOpenedHit_whenClickingInsideText_thenDoesNotClose` | geçti |
| HD11 | AC4 axe | `#givenOpenedHit_%s_whenAxeRuns_thenNoViolations` (metin, parça yok) | geçti |
| SR1 | K1 sonuç düğmedir, adres değişmez | `SearchResults.test.tsx#givenHit_whenClicked_thenOpensChunkDialogWithoutChangingLocation` | geçti |
| SP1 | K7, AC5 sonuçlar/odak/arama yok | `features/search/pages/SearchPage.test.tsx#hit dialog › givenSearchResults_whenHitOpenedAndClosedWithEscape_thenResultsKeptFocusOnHitAndNoNewSearch` (POST sayacı 1) | geçti |
| SP2 | K1, K8, S5 klavye | `#…givenSearchResults_whenSecondHitOpenedWithKeyboard_thenFocusStaysInDialogAndReturns` | geçti |
| SP3 | K6 sayfada kapatma | `#…givenOpenedHit_whenClosedWith_%s_thenResultsStay` (it.each 3) | geçti |
| SP4 | K9, K10, S3 | `#…givenOpenedHit_whenSourceLinkClicked_thenSourceDetailWithFullHighlightedChunk` | geçti |
| SP5 | G2 çift tıklama | `#…givenHit_whenDoubleClicked_thenSingleDialog` | geçti |
| CT1 | K10 | `features/source/components/ChunkTable.test.tsx#givenHighlightedLongChunk_whenRendered_thenHighlightedFullAndOthersPreview` (+ `#givenNoHighlightedChunk…`) | geçti |
| CQ1 | D4 önbellek kapsamı | **`features/source/api/sourceQueries.test.ts`**#givenCachedChunk_whenInvalidateSourceData_thenChunkQueryInvalidated (plan `shared/chunk/` altında diyordu; `invalidateSourceData` `features/source`'ta olduğu için `shared/`'ın feature import etmeme kuralı gereği buraya konuldu; başka çalışma alanının önbelleğinin etkilenmediği de doğrulanır) | geçti |
| – | D3/K11 `isChunkGoneError` dalları (planda ayrı test yoktu, mutation noktası olarak eklendi) | `shared/chunk/chunkRules.test.ts` (it.each 7 + 2) | geçti |
| EP1 | S1, S3, K1-K3, K7-K10, AC3, AC5, G1, G5 | `e2e/document-flow.spec.ts#givenSampleSearch_whenHitOpenedAndClosed_thenFullTextShownResultsKeptAndNoNewSearchEvent` (md5 üçlüsü: panel `innerText` = REST = DB; 360×640 sığma/kayma; `scrollY` sabit; Esc+odak; DB `search_events` sayısı sabit; "Kaynakta göster"; geri tuşu) | geçti (dev ortamı) |
| EP2 | S2, K4 | `#givenKeywordSearch_whenHitOpened_thenTermsHighlightedBeyondSnippet` ("masraf beyanı" örnek PDF'te 190. karakterden sonra eşleşme var; ön koşul assertion'ı geçti) | geçti (dev ortamı) |
| EP3 | S5, K8 | `#givenKeyboardOnly_whenSecondHitOpened_thenFocusTrappedAndReturned` | geçti (dev ortamı) |
| EP4 | S4, K11 | `#givenSearchHitShown_whenSourceGetsNewVersionBeforeOpening_thenPanelShowsChunkGoneMessage` (mevcut test dönüştürüldü; yeni sürüm `page.request` ile) | geçti (dev ortamı) |

Yazım hatası düzeltmesi (plan metninde): HT1'de ikinci terim `307-313` olmalı ("masraf beyanı" 300'den başlar, "beyanı" 307'de); testte 307-313 kullanıldı.

## Mutation
Stryker kurulu değil (`frontend/CLAUDE.md`: "Henüz olmayanlar"). Rol kuralı gereği uygulama kodu elle bozulmadı. Elle mutasyon analizi (hangi testin yakalayacağı):
| Mutasyon | Yakalayan test |
|---|---|
| `isChunkGoneError`: status 404 koşulu kalkarsa / kod listesinden biri çıkarsa / `ApiError` kontrolü kalkarsa | `chunkRules.test.ts` (500 + `CHUNK_NOT_FOUND`, `INGESTION_JOB_NOT_FOUND`, düz `Error`) |
| `Dialog` `closeOnBackdropClick &&` / `canClose &&` / `target === currentTarget` koşulu kalkarsa | DT1 (içerik tıklaması kapatmaz), DT2, `…ButCannotClose…` |
| `returnFocusRef` dalı / `isConnected` kalkarsa | DT4 (ref ≠ opener), `…ReturnsToOpener` |
| `handleNativeClose`'ta `isOpen && !open` kalkarsa | StrictMode testi (erteleyen `close` olayı `onClose`'u çağırırdı) |
| `ChunkTable` vurgulu dal kalkarsa / yanlış satır | CT1 |
| `hitContentSegments` yerine snippet kullanılırsa | HT1, HD1, HD3 |
| Panel `matchedBy` etiketleri, `role="alert"` yerine metin, `Kaynakta göster` gone'da görünürse | HD4, HD6, HD7, HD11 |
| Panelde `sourceId`/`chunkId` karışırsa (varlık isteği/bağlantı) | HD9 + `…OfOtherSource…` |
| `sourceKeys.all` kapsamı `SOURCES_QUERY_SCOPE`'tan ayrılırsa | CQ1 |

## Komutlar ve sonuçları
`frontend/` içinde:
- `npm run build` → geçti (`tsc -b && vite build`, hata yok).
- `npm run lint` → geçti, 0 uyarı, 0 hata.
- `npm run test:coverage` → **32 dosya / 470 test, hepsi geçti** (TASK-008 sonrası 27 dosya / 413 test; +5 dosya, +57 test). Coverage: satır %96,47, statement %96,1, branch %93,17, function %96,15 (eşik %80). `Dialog.tsx` %97,6, `SearchHitDialog.tsx` %94 satır.
- E2E: `E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npx playwright test` (vite dev server + local backend 8080 + dev postgres) → **ilk koşuda 20 geçti, 2 atlandı (`ollama-down.spec.ts`), 1 kaldı**. Kalan test koşuya göre değişti: `givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails` (satır 909) ya da `givenOversizedBodyToPathWithEncodedQuote_whenPost_thenProblemDetailsBodyIsValidJson` (satır 930), hata `apiRequestContext.post: aborted` (← 413 alındıktan sonra 23 MB gövde gönderimi proxy'de kesiliyor). Bunlar TASK-007/008 testleri, nginx için yazıldı, TASK-009 değişikliğiyle ilgisi yok. TASK-009 E2E'leri (EP1-EP4) ve diğer önceki testler bu koşuda geçti.

## Çalıştırılamayanlar
- **Docker'daki site (nginx, http://localhost:3000) ile E2E:** `up --build` backend konteyneri 8080'e bağlanamadı, çünkü bu portta benim başlatmadığım bir local `java` süreci (PID 70068) çalışıyor; vite dev server da 5173'te (PID 69843). İzinsiz kill etmedim, tam sistemi `down` ettim, dev compose postgres'i yeniden açtım (veri silinmedi, E2E global-setup dev veritabanını sıfırladı). Lead'e soru gönderildi; yanıt gelirse tam sistemle yeniden koşulacak. Bu yüzden son iki 21 MB testin nginx'e karşı sonucu ve "site imajı yeni frontend kodunu içeriyor mu" doğrulaması qa'ya/tekrar koşuya kalır.
- `ollama-down.spec.ts` (2 test): normal koşuda atlanır; ayrı override gerektirir.
- Backend: bu task'ta değişmedi, `./mvnw verify` koşulmadı (plan: zorunlu değil).

## Düzeltme
Yok. Uygulama hatası bulunmadı.

## Tam sistem koşusu
Kullanıcı 8080/5173'teki kendi süreçlerini kapattıktan sonra (lead onayı), dev compose `down` edildi (veri silinmedi) ve:
- `docker compose -f deployment/docker-compose.yml up --build -d` → 4 servis `healthy`: `postgres`, `backend`, `frontend` (nginx), `pgadmin`. `http://localhost:3000/w/42/arama` → 200. Site imajı yeni frontend koduyla derlendi.
- İlk E2E koşusunda belge içe alma `EMBEDDING_UNAVAILABLE` ile `FAILED` oldu: Ollama o sırada kapalıydı (`localhost:11434` cevap vermiyordu; konteynerden `host.docker.internal:11434` ulaşılamadı). `ollama serve` arka planda başlatıldı (model `bge-m3` kurulu). Bu koşu geçersiz sayıldı (1 geçti, 1 kaldı, 19 koşmadı).
- Ollama açıkken ikinci koşu: 12 geçti, **test 13 (`givenVectorSearch_whenOpenSearchEventsPage…`) kaldı** (`expect(received).toBe(expected)`; ayrıntı yakalanmadı, tek başına koşusu veri olmadığı için anlamsız). Bu test TASK-009'dan bağımsız (hit paneli açmıyor).
- Üçüncü koşu, aynı komut: `cd frontend && npx playwright test` (`E2E_BASE_URL` varsayılan `http://localhost:3000`, nginx'e karşı) → **21 geçti, 2 atlandı (`ollama-down.spec.ts`), 0 kaldı** (1,5 dk). EP1-EP4 geçti. **21 MB gövde testleri nginx'te geçti**: `givenBodyOver21MbThroughSite_whenPostVersion_thenFileTooLargeProblemDetails` (445 ms) ve `givenOversizedBodyToPathWithEncodedQuote_whenPost_thenProblemDetailsBodyIsValidJson` (487 ms). Dev proxy'deki `aborted` hatası bu yüzden Vite proxy'ye özgüdür.
- Test 13'ün ikinci koşudaki tek seferlik düşüşü tekrar etmedi; nedeni bilinmiyor (olası: günlük sayaç/zamanlama yarışı), flaky şüphesi olarak not edilir. Tekrar ederse qa/lead'e bildirilmeli.
- Sistem qa için AÇIK bırakıldı (4 servis healthy). Ollama'yı ben başlattım (`ollama serve`, log: scratchpad üst dizini `/private/tmp/claude-501/ollama.log`); qa için açık kalıyor.

## Düzeltme turu 1
| Madde (qa) | Test / değişiklik | Sonuç |
|---|---|---|
| Test 13 (`givenVectorSearch_whenOpenSearchEventsPage…`, `document-flow.spec.ts`) önceki testlerin bıraktığı 500 parçalık "Uzun belge" yüzünden veri durumuna bağlı | Testin Arrange'ine `removeLargeSourcesLeftByEarlierTests()` eklendi: kaynak 10001 (örnek PDF) ve 10002 ("Deneme notu", test 19'un ad çakışması adımı ister) dışındaki kaynakları, parçalarını ve işlerini siler. İlk denemede sadece 10001'i bırakmıştım, bu test 19'u (`DENEME NOTU` ad çakışması) kırdı; 10002 korunarak düzeltildi | 3 tam koşu |
| `npx playwright test` üst üste 3 kez 0 kaldı | `cd frontend && npx playwright test` (nginx, :3000, Ollama açık) × 3 | 1. koşu: 21 geçti, 2 atlandı, 0 kaldı (1,8 dk). 2.: 21 geçti, 2 atlandı, 0 kaldı (2,0 dk). 3.: 21 geçti, 2 atlandı, 0 kaldı (1,9 dk) |

Not: Uygulama kodu değişmedi. `npx tsc -b` ve `npm run lint` temiz. Ürün tarafı FYI (qa'nın bulgusu): büyük, birbirine benzer parça kümesinde HNSW recall düşüşü TASK-009 kapsamı dışıdır; test artık bu duruma bağımlı değil, çünkü o veri test 13'ten önce temizleniyor.

STATUS: DONE

