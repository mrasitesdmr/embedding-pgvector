TASK: TASK-004
# Frontend uygulama raporu

## Kullanılan skill'ler
`frontend-conventions`, `react-patterns`, `react-state-management`, `frontend-a11y`, `frontend-design`, `frontend-security`, `vite-patterns` (planın "Frontend planı"ndaki liste).

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| D10: multipart | `shared/api/httpClient.ts` | Gövde `FormData` ise `Content-Type` eklenmez, tarayıcı `multipart/form-data; boundary=…` koyar. Diğer gövdelerde davranış aynı |
| D7, K12, AC-6: iş hata nedenleri | `shared/api/errorMessages.ts` | `INGESTION_FAILURE_REASONS` (7 kod, plandaki metinler birebir) + `ingestionFailureReasonFor(code)`. `null` ya da bilinmeyen kod için "beklenmeyen bir hata oluştu" |
| `formatFileSize` | `shared/format/numberFormat.ts` | 1024 tabanlı, `tr-TR`, en fazla 1 ondalık: 830 → "830 B", 1536 → "1,5 KB", 2.516.582 → "2,4 MB", 20.971.520 → "20 MB" |
| Tipler (sözleşme 0.4.0) | `features/source/model/types.ts` | `INGESTION_STAGES`, `IngestionStage`, `INGESTION_ERROR_CODES`, `IngestionErrorCode`, `IngestionJobResponse`, `SourceResponse.latestJob?: IngestionJobResponse \| null`. Ek: `UPLOADABLE_SOURCE_KINDS` / `UploadableSourceKind`, `CreateSourceInput`, `UploadSourceVersionInput` |
| K2, K7, D6, D15, G1, G2, G4 | `features/source/model/uploadRules.ts` | Sabitler, `fileExtension`, `acceptAttribute`, `validateUploadFile` (sıra: dosya yok → genel uzantı → kaynak türü → boş → boyut), `validateSourceName` / `normalizeSourceName`, `validateVersionLabel` / `normalizeVersionLabel`. Mesajlar plandaki gibi |
| K5: `isUploadableKind` | `features/source/model/sourceRules.ts` | Tip koruyucu (`kind is UploadableSourceKind`) |
| K3: API hatası → alan | `features/source/model/uploadFormErrors.ts` | `toUploadFieldErrors(error)` plandaki tabloya göre. `VALIDATION_FAILED`'da bilinen alan yoksa `form` alanına `messageFor('VALIDATION_FAILED')` |
| K9, K11, D11, D13 | `features/source/model/ingestionRules.ts` | `INGESTION_POLL_INTERVAL_MS = 1000`, `COMPLETED_HIGHLIGHT_MS = 3000`, `INGESTION_STAGE_LABELS`, `isJobActive`, `isJobFailed`, `isSourceIngesting` (D11: iş aktif ya da `status === 'INGESTING'`), `displayedSourceStatus`, `freshestJob`, `ingestionStageText`, `progressPercent`, `ingestionDoneMessage`, `ingestionFailureLine` |
| API | `features/source/api/sourceApi.ts` | `createSource` (`name` kırpılmış + `file`), `uploadSourceVersion` (`version` kırpılıp boş değilse eklenir), `getIngestionJob` |
| Query hook'ları | `features/source/api/sourceQueries.ts` | `sourceKeys.ingestionJob`, `useCreateSourceMutation`, `useUploadSourceVersionMutation` (`INGESTION_IN_PROGRESS`'te liste + detay invalidate), `useIngestionJobsPolling` (`useQueries`, `initialData: job`, `refetchInterval` aktifken 1000 ms, 4xx'te durur, `refetchIntervalInBackground: true`, `retry: false`), `useIngestionJobQuery` (`skipToken` + `initialData: latestJob`). Yardımcılar: `invalidateSourceData`, `invalidateSourceListAndDetail`, `isClientError` |
| D5, K15-K17: diyalog | `shared/components/Dialog.tsx` (+css) | Native `<dialog>` + `showModal()`. Odak `initialFocusRef`'e (yoksa ilk odaklanabilir öğeye), Esc `cancel` → `preventDefault` + `canClose` ise `onClose`, kapanınca odak açılıştaki `document.activeElement`'e döner, arka plana tıklamak kapatmaz, `aria-labelledby` başlığa (`<h2>`). Başlık ve içerik sadece açıkken render edilir (her açılışta form sıfırlanır). Tab döngüsü: bkz. "Plandan sapmalar" |
| D8: toast | `shared/toast/ToastProvider.tsx` (+css), `shared/toast/useToast.ts` | `showToast(message)`, `TOAST_DURATION_MS = 5000`, bölge `role="status"` + `aria-live="polite"`, alt ortada `position: fixed`, lab `.toast` görünümü, `prefers-reduced-motion`'da animasyon yok. Zamanlayıcılar unmount'ta temizlenir |
| K1, K4, D3: yeni kaynak | `features/source/components/CreateSourceDialog.tsx`, `pages/SourcesPage.tsx` (+css) | Başlık satırının sağında "Yeni kaynak ekle" (birincil, liste yüklenmeden de görünür). Diyalog: açıklama, "Kaynak adı" (ilk odak, `maxLength=80`), `FileField` "Dosya". Gönderimde istemci kontrolü, hata varsa ilk hatalı alana odak. 202'de diyalog kapanır, `sourceKeys.list` + `workspaceKeys.all` invalidate. Sunucu reddederse diyalog açık kalır, seçilen dosya kaybolmaz |
| K5, K6, K7, K8: yeni sürüm | `components/UploadVersionButton.tsx`, `components/UploadVersionDialog.tsx` | Buton sadece `isUploadableKind` ise render edilir; iş sürerken `disabled` + `title="Yükleme sürüyor"`; tıklama `stopPropagation`. Diyalog: özet satırı, `Callout` bilgi kutusu, `FileField` (`accept` kaynak türüne göre, ilk odak), "Sürüm etiketi (isteğe bağlı)" (`maxLength=40`) |
| K6: dosya alanı | `components/FileField.tsx` (+css) | Native `<input type="file">` + görünür `<label>`. Seçimde `validateUploadFile(file, kind)`; hata alanın altında `role="alert"`, input'ta `aria-invalid` + `aria-describedby`. Seçilen dosya kutusu (lab `.file`): ad (kalın, düz metin) + `formatFileSize` |
| K9: ilerleme | `components/IngestionProgress.tsx` (+css) | Aşama metni + `role="progressbar"` (`aria-valuemin=0`, `aria-valuemax=100`, `aria-valuenow`, `aria-valuetext` = aşama metni, `aria-label` = "`<ad>` yükleme ilerlemesi"), inline genişlik, `prefers-reduced-motion`'da geçiş yok |
| K12, D12: başarısızlık | `components/IngestionFailure.tsx` (+css) | "Son yükleme başarısız: …", son iş `FAILED` olduğu sürece |
| D4, K10, K11, K13, K14, D16, G3, G5: sorgulama | `components/IngestionWatcherProvider.tsx`, `hooks/useRecentlyCompletedSourceIds.ts` | Liste (`useSourcesQuery`) içindeki aktif `latestJob`'lar `useIngestionJobsPolling` ile sorgulanır. Aktif görülen `jobId` `ref` set'ine girer. Aynı iş `DONE`/`FAILED` görülünce bir kez işlenir (`handled` set'i). DONE: toast + 3 sn vurgu + kaynak verisi ve `workspaceKeys.all` invalidate. FAILED: liste + detay + `workspaceKeys.all` invalidate, toast yok. 4xx: o işin sorgusu durur, liste invalidate. Ağ/5xx: sorgulama sürer, son aşama kalır |
| D11: satır ve detay okuması | `hooks/useSourceIngestion.ts` | `(workspaceId, source) → { job, isIngesting }`. İş cache'i ile `source.latestJob`'tan güncel olanı seçer (`freshestJob`) |
| K5, K9, K11, K12: satır | `components/SourceRow.tsx` (+css) | Ad hücresinde ilerleme / başarısızlık / "Yeni sürüm yükle". Durum rozeti `displayedSourceStatus`. Vurgu sınıfı `isJustCompleted`. Satır tıklaması `a`, `button` ve `dialog` içinden gelirse gezinmez |
| D11: detay rozeti | `components/SourceInfoBoxes.tsx` | Yeni `status` prop'u (rozet D11'e göre) |
| K5, K11: detay sayfası | `pages/SourceDetailPage.tsx` (+css) | Başlık satırında sağda `UploadVersionButton`, başlık altında `IngestionProgress` / `IngestionFailure`. İş DONE olunca detay ve parçalar yeniden yüklenir |
| D4: provider'lar | `app/WorkspaceLayout.tsx` | `ToastProvider` → `LastSearchProvider key` → `IngestionWatcherProvider key={workspaceId}` → `Outlet` |
| K19, D9 | `vite.config.ts` | `build: { assetsInlineLimit: 0 }`. Build sonrası `dist/assets/*.css`'te `data:` URI sayısı 0; yazı tipleri `/assets/*.woff2` ve `*.woff` dosyaları |
| Görsel dil | `index.css`, `shared/components/button.module.css` | Yeni token'lar: `--color-progress-track`, `--color-file-line`, `--color-backdrop`, `--radius-xl`, `--shadow-dialog`, `--shadow-toast`. Lab `.btn` / `.btn.out.sm` karşılığı ortak buton sınıfları (`primary`, `secondary`, `small`). Component CSS'inde ham hex yok |
| `frontend/CLAUDE.md` | `frontend/CLAUDE.md` | `assetsInlineLimit` notu, klasör yapısı (`hooks/`, `toast/`, `Dialog`), sorgulama yapısı, jsdom `<dialog>` tuzağı ve dosya alanlı formlarda `useActionState` kullanılmaması. Yeni komut ve paket yok |

## Değişen dosyalar
Hepsi `frontend/` altında. **Yeni paket yok.**

**Yeni:**
- `src/shared/components/Dialog.tsx`, `Dialog.module.css`, `button.module.css`
- `src/shared/toast/ToastProvider.tsx`, `ToastProvider.module.css`, `useToast.ts`
- `src/features/source/model/uploadRules.ts`, `uploadFormErrors.ts`, `ingestionRules.ts`
- `src/features/source/hooks/useRecentlyCompletedSourceIds.ts`, `useSourceIngestion.ts`
- `src/features/source/components/`:
  - `CreateSourceDialog.tsx`, `UploadVersionDialog.tsx`, `uploadDialog.module.css` (iki diyaloğun ortak form stili)
  - `UploadVersionButton.tsx`
  - `FileField.tsx`, `FileField.module.css`
  - `IngestionProgress.tsx`, `IngestionProgress.module.css`
  - `IngestionFailure.tsx`, `IngestionFailure.module.css`
  - `IngestionWatcherProvider.tsx`

**Değişen:**
- `src/index.css`, `src/app/WorkspaceLayout.tsx`
- `src/shared/api/httpClient.ts`, `src/shared/api/errorMessages.ts`, `src/shared/format/numberFormat.ts`
- `src/features/source/model/types.ts`, `sourceRules.ts`
- `src/features/source/api/sourceApi.ts`, `sourceQueries.ts`
- `src/features/source/components/SourceRow.tsx`, `SourceRow.module.css`, `SourceInfoBoxes.tsx`
- `src/features/source/pages/SourcesPage.tsx`, `SourcesPage.module.css`, `SourceDetailPage.tsx`, `SourceDetailPage.module.css`
- `vite.config.ts`, `CLAUDE.md`

**Silinen:** yok.

## Ekran metinleri
**Bilgi kaynakları sayfası**
- Buton: "Yeni kaynak ekle"

**Yeni kaynak diyaloğu** (`role="dialog"`, ad "Yeni kaynak ekle")
- Başlık: "Yeni kaynak ekle"
- Açıklama: "Dosya yüklenince parçalara bölünür ve embedding'e çevrilir. İş bitene kadar kaynak listede İşleniyor görünür."
- Label'lar: "Kaynak adı" (placeholder "ör. Fatura SSS"), "Dosya"
- Butonlar: "Vazgeç", "Kaynağı ekle" (istek sürerken "Yükleniyor…", ikisi de `disabled`)

**Yeni sürüm diyaloğu** (`role="dialog"`, ad "Yeni sürüm yükle")
- Satır ve detay butonu: "Yeni sürüm yükle" (iş sürerken `disabled`, `title="Yükleme sürüyor"`)
- Başlık: "Yeni sürüm yükle"
- Özet: "`<ad>` · şu an `<v3.1 | sürüm yok>` · `<N>` parça" (ör. "Dalga T5 modem kullanım kılavuzu · şu an v3.1 · 5 parça")
- Bilgi kutusu: "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür."
- Label'lar: "Dosya", "Sürüm etiketi (isteğe bağlı)" (placeholder "ör. 3.2")
- Butonlar: "Vazgeç", "Yeni sürümü yükle" (istek sürerken "Yükleniyor…")

**Alan uyarıları** (alanın altında, `role="alert"`)
- "Yüklenecek dosyayı seç."
- "Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç."
- "Bu kaynağa sadece PDF dosyası yüklenebilir." / "Bu kaynağa sadece TXT, MD ya da MARKDOWN dosyası yüklenebilir." / "Bu kaynağa sadece CSV dosyası yüklenebilir."
- "Dosya boş. İçinde metin olan bir dosya seç."
- "Dosya 20 MB'tan büyük olamaz."
- "Kaynak adı 2-80 karakter olmalı."
- "Sürüm etiketi en fazla 40 karakter olabilir."

**Backend hataları**
- Alan altında: `SOURCE_NAME_TAKEN` (ad), `FILE_TOO_LARGE` / `UNSUPPORTED_FILE_TYPE` / `UNSUPPORTED_FILE_ENCODING` / `SOURCE_KIND_MISMATCH` (dosya).
- Diğerleri diyalog içinde `ErrorAlert` (`role="alert"`), "Tekrar dene" butonu yok.
- Metinler `errorMessages.ts`'teki mevcut eşlemeden gelir.

**Seçilen dosya kutusu:** dosya adı + boyut (ör. "2,4 MB").

**Satır ve detay**
- Aşama metinleri: "Sıraya alındı", "Dosya okunuyor", "Parçalara bölünüyor", "Embed ediliyor · 96/214 parça" (`totalChunks` null ise "Embed ediliyor · 0/… parça"), "Yazılıyor".
- İlerleme çubuğu: `role="progressbar"`, `aria-label` = "`<ad>` yükleme ilerlemesi".
- Başarısızlık: "Son yükleme başarısız: `<neden>`". Nedenler planın D7 tablosundaki metinler birebir.
- Rozet: iş aktifse "İşleniyor".
- Vurgu: satırda `isJustCompleted` sınıfı, 3 sn.

**Toast** (`role="status"`)
- "`<ad>` v`<sürüm>` hazır · `<N>` parça" (ör. "Dalga T5 modem kullanım kılavuzu v3.2 hazır · 214 parça").
- Sürüm yoksa: "Yardım notları hazır · 12 parça".

## Plandan sapmalar
1. **Diyalogda Tab döngüsü (D5).**
   - **Ne:** D5 "tarayıcı Tab'ı diyalog içinde tutar" diyor. Chromium'da Playwright ile denedim: `showModal()` sayfanın geri kalanını `inert` yapıyor ama son öğeden sonraki Tab odağı diyalogdan çıkarıyor (gerçek tarayıcıda adres çubuğuna gider; headless'ta `document.activeElement` = `BODY`). Bu K15 ve E4'ü bozar.
   - **Yapılan:** `Dialog.tsx`'e küçük bir `onKeyDown` Tab döngüsü eklendi: son öğede Tab → ilk öğe, ilk öğede Shift+Tab → son öğe. Native `<dialog>`, Esc ve `inert` davranışı aynı kaldı. Yeni paket yok.
   - **Kimle konuşuldu:** lead'e ve planner'a mesajla bildirildi. İkisi de onayladı. Planner bunu `01-plan.md`'ye "Uygulama notu 2" olarak yazdı ve F22'ye şu beklentiyi ekledi: "Kaynağı ekle"de Tab → "Kaynak adı", ilk öğede Shift+Tab → son öğe. Tester E4'te gerçek tarayıcıda doğrulayacak.
   - **Planner'ın şartları ve karşılanma biçimi:**
     - *Döngü sadece diyalog açıkken çalışır:* `onKeyDown` `<dialog>` üzerinde; odak diyalogdayken, yani diyalog açıkken tetiklenir.
     - *Pending sırasında pasif butonlar atlanır:* seçici `button:not([disabled])` ve `input:not([disabled])` kullanır; "Vazgeç" ve gönder butonu `disabled` iken listeye girmez.
     - *Liste her tuş basışında yeniden hesaplanır:* odaklanabilir öğeler her Tab'da `querySelectorAll` ile toplanır; dosya seçimi ya da hata metni öğeleri değiştirse de güncel liste kullanılır.
2. **Küçük uygulama ayrıntıları** (davranış planla aynı, dosya listesinde olmayan parçalar):
   - **İnvalidate kapsamı:** İş `DONE` olunca `sourceKeys.all` yerine `invalidateSourceData` çağrılıyor. Bu, `sourceKeys.all` altındaki her şeyi, `ingestion-jobs` anahtarları hariç, invalidate eder. Sebep: biten işin sorgusu invalidate edilirse bir job isteği daha gider, F18'deki "toplam 5 job isteği, sonra artmaz" tutmaz. Detay invalidate'i `exact: true` ile yapılıyor; yükleme başlarken parça listesi boşuna yeniden istenmiyor.
   - **Polling sorgusunun `staleTime`'ı:** Polling sorgusuna `staleTime: INGESTION_POLL_INTERVAL_MS` verildi. Böylece "ilk job isteği açılıştan 1 sn sonra" kuralı QueryClient varsayılanından bağımsız (test client'ında `staleTime` 0).
   - **Ek stil dosyaları:** Ortak `shared/components/button.module.css` ve `components/uploadDialog.module.css` eklendi. `IngestionFailure.module.css` de eklendi (planda sadece `.tsx` yazıyordu).
   - **Etiket tablosu:** `INGESTION_STAGE_LABELS`'a tip bütünlüğü için `DONE` → "Hazır" ve `FAILED` → "Başarısız" da eklendi. Ekranda gösterilmezler, çünkü ilerleme sadece aktif işte render edilir.
   - **Ek model fonksiyonları:** `isJobFailed`, `displayedSourceStatus` ve `freshestJob` eklendi. `freshestJob`, liste yenilenince satırın eski iş cache'inde takılı kalmasını önler.
   - **Yeni sürüm diyaloğundaki özet:** Kaynak adı, sürüm ve parça sayısı diyalogda açıklama paragrafı olarak gösteriliyor.
   - **Formlar:** `useActionState` yerine `onSubmit` + mutation kullanıldı. React form action'ı bitince form alanlarını sıfırlıyor; backend hatasında seçilen dosya kaybolurdu (D3 "seçimi kaybetmeden").

## Doğrulama
- **`cd frontend && npm run build`:** geçti (`tsc -b` + `vite build`). Build sonrası `grep -o "data:[a-z/+-]*" dist/assets/*.css | wc -l` → 0. Yazı tipleri `dist/assets/*.woff2` / `*.woff` dosyaları olarak çıkıyor.
- **`npm run lint` (oxlint):** geçti. Uyarı ve hata 0, çıkış kodu 0.
- **`npm test`:** 208 testten 205 geçti, 3 kaldı. 20 dosyanın 17'si geçti.
  - `SourcesPage.test.tsx › givenDalgaSources_whenOpenSources_thenShowsKindsStatusesFooterWithoutUploadButtons`: **beklenen**. Planda F29 "yükleme butonu yok" kontrolü ters dönecek (F14). Hücre metninde artık "Yeni sürüm yükle" butonu var.
  - `SourceDetailPage.test.tsx › givenPdfSource_whenOpenDetail_thenShowsInfoBoxesAndFirstTwentyChunks`: **beklenen**. Planda F37 "yükleme butonu yok" kontrolü ters dönecek (F14).
  - `App.test.tsx › givenWorkspaceRootPath_whenOpen_thenRedirectsToSearch`: **zamanlamaya bağlı, ara sıra kalıyor** (6 koşudan 5'inde kaldı). Ayrıntı "Tester için notlar"da. Uygulama davranışı doğru, URL `/w/19/arama` oluyor.
- **Gerçek tarayıcıda duman testi:**
  - **Kurulum:** Vite dev server (5199) + Chromium (Playwright). API `page.route` ile sahtelendi. Script ve ekran görüntüleri scratchpad'de, projeye dosya eklenmedi.
  - **Sonuç:** 27 kontrolün hepsi geçti; konsolda hata yok.
  - **Liste:**
    - Başarısızlık satırı ve "Embed ediliyor · 96/214 parça" görünüyor.
    - "Yeni sürüm yükle" butonları sadece PDF ve CSV satırlarında; iş süren satırda `disabled`.
  - **Yeni kaynak diyaloğu, klavye:**
    - Enter ile açılınca odak "Kaynak adı"nda.
    - 10 Tab ve 6 Shift+Tab boyunca odak diyalog içinde kalıyor.
    - Esc → kapanıyor, odak "Yeni kaynak ekle"ye dönüyor.
  - **Yeni kaynak diyaloğu, gönderim:**
    - `.docx` → tür uyarısı.
    - Boşluk ad → ad uyarısı, odak ad alanında, POST yok.
    - Geçerli ad ve `.md` → POST `multipart/form-data; boundary=…`, ad kırpılmış gidiyor, diyalog kapanıyor.
  - **İlerleme ve bitiş:**
    - Satırda aşamalar ilerliyor.
    - Bitişte "Yardım notları hazır · 12 parça" toast'u çıkıyor, satır `isJustCompleted` sınıfını alıyor.
    - DONE'dan sonra job isteği artmıyor.
  - **Yeni sürüm diyaloğu:**
    - Satırdaki "Yeni sürüm yükle" sayfayı detaya götürmüyor.
    - Özet "Dalga T5 modem kullanım kılavuzu · şu an v3.1 · 5 parça" ve `accept=".pdf"`.
    - `.md` seçilince "Bu kaynağa sadece PDF dosyası yüklenebilir."
    - Esc → odak satırdaki butona dönüyor.
  - **Dar ekran:** 360 px'te diyalog ekrana sığıyor.
- **Backend ile canlı deneme yapılmadı:** backend ve compose açık değildi. Uçtan uca doğrulama tester (E1-E5) ve qa adımında.

## Tester için notlar
- **jsdom ve `<dialog>`:** jsdom 30'da `HTMLDialogElement.prototype.showModal` ve `close` yok (`typeof` → `undefined`). `Dialog.tsx` açılınca `dialog.showModal()` çağırdığı için diyalog testlerinden önce `src/test/setup.ts`'e taklit gerekir (plandaki gibi). Taklidin yapması gerekenler:
  - `showModal`: `open` özniteliğini koymak.
  - `close`: `open`'ı kaldırıp `close` olayını göndermek.
  - Esc testi: jsdom Esc'ten `cancel` üretmez. `fireEvent(dialog, new Event('cancel', { cancelable: true }))` ya da taklitte keydown Escape → `cancel` gönderilmesi gerekir. `Dialog` `cancel`'da `onClose` çağırır; `close` olayında da açıksa `onClose` çağırır.
- **Kapalı diyalog DOM'da boş durur.** `<dialog>` her satırda vardır; başlık ve form sadece açıkken render edilir. Hücre metni karşılaştırmalarında kapalı diyalog metin eklemez, ama satırdaki "Yeni sürüm yükle" butonunun metni hücre metnine girer (F29 güncellenirken dikkat).
- **MSW handler'ları gereken endpoint'ler:**
  - `POST /api/workspaces/:workspaceId/sources` (202 + `IngestionJobResponse`).
  - `POST /api/workspaces/:workspaceId/sources/:sourceId/versions` (202).
  - `GET /api/workspaces/:workspaceId/ingestion-jobs/:jobId`.
  - **Önemli:** `IngestionWatcherProvider` `WorkspaceLayout`'ta olduğu için **her sayfada** (Arama dahil) `GET .../sources` isteği atılır. Listede aktif `latestJob` varsa (ör. F17'de 302 `EMBEDDING`) 1 sn'de bir job isteği de gider. `onUnhandledRequest: 'error'` olduğundan bu testlerde job handler'ı gerekir.
- **Sorgulama zamanlaması:**
  - Polling sorgusunun `staleTime`'ı 1000 ms. İlk job isteği açılıştan 1 sn sonra gider, sonra her 1 sn'de bir; QueryClient'ın `staleTime` ayarından bağımsız.
  - DONE/FAILED, 4xx ya da liste yenilendikten sonra iş aktif değilse sorgu durur.
  - DONE sonrası job sorgusu invalidate edilmez; F18'deki "toplam 5, 3 sn sonra artmaz" buna dayanır.
- **Toast ve vurgu:**
  - Sadece bu sayfa açıkken aktif görülen iş bittiğinde çıkar.
  - Ayrıca yükleme mutation'ının cache'e yazdığı iş `QUEUED` iken liste doğrudan `DONE` getirirse de çıkar.
  - Listede ilk açılışta zaten DONE/FAILED olan iş için çıkmaz (G5).
  - Toast 5 sn, vurgu 3 sn sonra kalkar. Sahte zamanlayıcıyla `advanceTimersByTimeAsync` kullanılabilir.
- **`App.test.tsx › givenWorkspaceRootPath_whenOpen_thenRedirectsToSearch` zamanlaması:**
  - **Belirti:** Test başlık görünür görünmez `currentUrl()`'ı okuyor. `currentUrl()` `LocationProbe`'un passive effect'iyle yazılıyor. Layout'a eklenen kaynak listesi isteği (D4) render sırasını değiştirdiği için effect, başlık DOM'a girdikten sonra çalışabiliyor ve test `/w/19` görüyor.
  - **Kontrol:** Watcher kaldırılınca test 5/5 geçti. Sadece job polling'i kaldırınca yine kaldı; yani etken liste isteği.
  - **Öneri:** `await waitFor(() => expect(currentUrl()).toBe('/w/19/arama'))`. Uygulama tarafında bir hata yok.
- **Satır tıklaması:** `button`, `a` ve `dialog` içinden gelen tıklamalar satır gezinmesini tetiklemez. F22'de "sayfa detay'a gitmez" kontrolü buna dayanır.
- **`FileField`:**
  - `FileField` dosyayı seçim anında doğrular ve hatayı alanın altında gösterir.
  - F11'deki 25 MB dosya için `File` nesnesinin `size` özelliği `Object.defineProperty` ile tanımlanabilir.
  - Dosya kutusundaki boyut UTF-8 bayt sayısıdır. Ör. `# Yardım\nŞifre sıfırlama adımları.\n` 41 B.
- **Native dosya seçici metni:** "Choose File" / "Dosya Seç" tarayıcı dilinden gelir; uygulama metni değildir. Sorgulamada label ("Dosya") kullanılmalı.
- **Detay sayfası:** İş aktifse başlık altında ilerleme çubuğu, `FAILED` ise başarısızlık satırı görünür. Durum kutusundaki rozet `displayedSourceStatus`'tan gelir.

STATUS: DONE
