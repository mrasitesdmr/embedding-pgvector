TASK: TASK-004
# Plan

## Özet
**Mevcut durum (TASK-003 sonrası):**
- Backend'de yükleme (`POST .../sources`, `POST .../sources/{sourceId}/versions`, 202 + `IngestionJobResponse`) ve iş durumu (`GET .../ingestion-jobs/{jobId}`) TASK-002'den beri var.
- `SourceResponse` kaynağın işini bilmiyor. Sayfa yenilenince süren işin `jobId`'si bulunamıyor. Yeni sürüm yüklemesi başarısız olursa kaynak `READY`'ye döndüğü için hata görünmüyor.
- Frontend'de kaynak listesi ve detayı sadece görüntüleme yapıyor. Yükleme butonu, diyalog, toast ve polling yok. `httpClient` her gövdeye `Content-Type: application/json` koyuyor (multipart çalışmaz).
- Üretim build'inde `@fontsource/jetbrains-mono`'nun küçük alt kümeleri CSS'e `data:` URI olarak gömülüyor. nginx CSP'si (`font-src 'self'`) bunları engellediği için konsolda hata çıkıyor (TASK-003 QA, Optional).

**Yeni durum:**
1. **Backend (küçük ekleme, davranış değişmez):** `SourceResponse.latestJob` (kaynağın en yeni işi, `IngestionJobResponse` şekliyle ya da `null`). Sözleşme **0.4.0**. Migration yok, yeni `errorCode` yok.
2. **Frontend:**
   - Bilgi kaynakları sayfasında "Yeni kaynak ekle" butonu ve diyaloğu.
   - Dosyadan gelen kaynakların satırında ve detay başlığında "Yeni sürüm yükle" butonu ve diyaloğu.
   - Satırda aşama metni + ilerleme çubuğu, 1 sn'de bir iş durumu sorgusu.
   - İş bitince listenin yenilenmesi, satır vurgusu ve toast. İş başarısızsa satırda neden.
   - Yenileme ya da yeni sekmeden sonra süren işin `latestJob`'dan bulunması.
   - `httpClient` multipart desteği. İş hata kodlarının Türkçesi.
3. **Kural 19 (CSP):** `vite.config.ts`'te `build.assetsInlineLimit: 0`. Yazı tipleri ayrı dosya olarak `/assets/`'ten gelir, CSP değişmez.

**Değişen mevcut parçalar (eski ad → yeni ad yok; sadece genişleme):**
- Backend:
  - `SourceResponse` + `latestJob`; `SourceResponse.from(Source)` → `SourceResponse.from(Source, @Nullable IngestionJob)`.
  - `SourceService` + `IngestionJobRepository` bağımlılığı.
  - `IngestionJobRepository` + `findLatestBySourceIds`.
  - `OpenApiConfig.API_VERSION = "0.4.0"`.
- Frontend:
  - `httpClient.request`: `FormData` gövdeye `Content-Type` koymaz.
  - `errorMessages.ts` + iş hata nedenleri.
  - `SourceResponse` tipi + `latestJob`.
  - `SourceRow`, `SourcesPage`, `SourceDetailPage`, `SourceInfoBoxes` (durum rozeti), `WorkspaceLayout` (provider'lar).
  - `numberFormat.ts` + `formatFileSize`.
- Var olan testlerden şunlar tester tarafından güncellenir:
  - F29 ve F37 "yükleme butonu yok" kontrolleri ters döner.
  - B13 `latestJob` yok / 0.3.0 kontrolü.
  - Fixture'lara `latestJob` eklenir.

**Bölme:** Task bölünmüyor. Backend eklemesi, diyaloglar, ilerleme ve CSP düzeltmesi aynı kullanıcı akışının parçaları; sadece yükleme diyaloğu ya da sadece ilerleme tek başına teslim edilemez. Kural 19 tek satırlık bir config değişikliği.

**Uygulama sırası:** backend-coder ve frontend-coder paralel (frontend sözleşmeye göre yazar). Frontend içinde sıra:
1. `httpClient` + tipler + kurallar.
2. `Dialog` ve `ToastProvider`.
3. Yükleme diyalogları.
4. İlerleme ve `IngestionWatcherProvider`.
5. Detay sayfası.
6. Vite ayarı.

Ardından tester (backend + component + E2E), qa (compose `--build` + tarayıcı senaryoları).

## Çalışacak roller
- **planner:** bu plan; sözleşme `docs/api/openapi.yaml` 0.4.0.
- **backend-coder:** `latestJob` alanı, repository sorgusu, `OpenApiConfig` sürümü, `backend/CLAUDE.md` komut satırı.
- **frontend-coder:** `frontend/src/**` (testler hariç), `vite.config.ts` (`assetsInlineLimit`), `frontend/CLAUDE.md`.
- **tester:**
  - backend testleri;
  - frontend component testleri, `src/test/**` (fixture, MSW handler'ları);
  - Playwright testleri (`frontend/e2e/**`). Yeni testler ve bozulan eski testlerin güncellenmesi bu rolün işi.
- **qa:** compose'u `--build` ile açar, E2E'yi 3000'e karşı çalıştırır, TASK'taki 9 örnek senaryoyu tarayıcıdan dener, sözleşme uyumunu ve konsolda CSP hatası olmadığını kontrol eder.
- **Atlanan: devops.**
  - Docker, nginx ve compose dosyalarında değişiklik yok. CSP sorunu nginx'te değil, Vite build ayarında çözülüyor (D9).
  - Yükleme için gereken nginx ayarları (`client_max_body_size 21m`, `proxy_read_timeout 120s`) TASK-003'te zaten kondu.
  - Bu yüzden qa tester'a bağlıdır. qa sistemi `docker compose -f deployment/docker-compose.yml up --build -d` ile kendisi açar.
- WebSocket yok (TASK kapsam dışı); `docs/api/websocket.md` açılmaz.

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | "Yeni kaynak ekle" butonu → diyalog: kaynak adı (zorunlu) + dosya | `SourcesPage` başlığı, `CreateSourceDialog` | F10, F22, E2 |
| K2 | Kabul edilen uzantılar; desteklenmeyen / >20 MB / boş dosya ya da boş ad → yükleme yok, alan altında uyarı | `uploadRules.ts` (`validateUploadFile`, `validateSourceName`), `FileField` | F1, F3, F11, F12, E3 |
| K3 | Backend reddederse `errorCode`'a göre Türkçe | `uploadFormErrors.ts` (`toUploadFieldErrors`), `errorMessages.ts` | F13 |
| K4 | Yükleme başlayınca diyalog kapanır; yeni kaynak "İşleniyor" | Mutation `onSuccess` → kapanış + `sourceKeys.list` invalidate (D3) | F10, E2 |
| K5 | "Yeni sürüm yükle" sadece PDF / Metin-Markdown / CSV'de; satırda ve detay başlığında | `isUploadableKind`, `SourceRow`, `SourceDetailPage` | F14 |
| K6 | Diyalog: ad, mevcut sürüm, parça sayısı; dosya adı ve boyutu; isteğe bağlı sürüm etiketi; "arama mevcut sürümü döndürür" bilgisi | `UploadVersionDialog`, `FileField`, `formatFileSize` | F7, F15 |
| K7 | Dosya kuralları K2 ve K3 gibi | Aynı `validateUploadFile` + kaynak türü kontrolü (D6) | F2, F16 |
| K8 | İş sürerken buton pasif; yine de `INGESTION_IN_PROGRESS` gelirse Türkçe | `isSourceIngesting(source, job)`; dialog alert | F17 |
| K9 | Satırda aşama metni + ilerleme çubuğu; durumda "İşleniyor"; 5 aşama metni | `IngestionProgress`, `ingestionStageText` | F4, F18 |
| K10 | ~1 sn'de bir sorgu; `DONE`/`FAILED`'da durur | `IngestionWatcherProvider` + `useIngestionJobsPolling` (`INGESTION_POLL_INTERVAL_MS = 1000`) | F18, F19 |
| K11 | `DONE`: liste yenilenir, satır kısa vurgulanır, "<Ad> <sürüm> hazır · N parça" toast; detay açıksa sürüm, parça sayısı, parçalar yeni | `handleJobFinished` (D4), `ingestionDoneMessage`, `ToastProvider`, invalidate | F5, F18, F21, F24, E1 |
| K12 | `FAILED`: satırda "Son yükleme başarısız: <neden>"; bütün iş hata kodlarının Türkçesi | `IngestionFailure`, `ingestionFailureReasonFor` (`errorMessages.ts`) | F6, F19, F25 |
| K13 | Başka sayfadan dönüş, yenileme, yeni sekme → süren iş görünür; backend'den son iş öğrenilir | `SourceResponse.latestJob` + watcher | B1-B5, F20, E1 |
| K14 | Yükleme sürerken arama, kayıtlar ve diğer sayfalar normal | Watcher `WorkspaceLayout`'ta, sayfadan bağımsız (D4) | F21 |
| K15 | Diyalog: odak içeri, Esc / "Vazgeç" kapatır, odak açan butona döner, Tab dışarı çıkmaz | `shared/components/Dialog` (native `<dialog>` + `showModal`, D5) | F22, E4 |
| K16 | Her form alanının etiketi var; uyarılar ekran okuyucuya duyurulur | `<label htmlFor>`, hata `role="alert"`, `aria-invalid`, `aria-describedby`; toast bölgesi `role="status"` | F22, F23 |
| K17 | Akışın tamamı klavyeyle | Native buton, input, dialog; satırdaki buton satır tıklamasını tetiklemez | F22, E4 |
| K18 | Son iş bilgisi backend'e ve sözleşmeye eklenir; var olan davranış değişmez | `SourceResponse.latestJob`, `openapi.yaml` 0.4.0 | B1-B6; mevcut backend testleri |
| K19 | Üretim build'inde CSP `font-src` hatası yok; CSP gevşetilmeden | `vite.config.ts` `build.assetsInlineLimit: 0` (D9) | E5; qa Q-CSP |
| S-1 | Modem kılavuzu 3.2: aşamalar, yenilemede devam, 3.2 + yeni parça sayısıyla Hazır, toast | Tümü | E1; qa Q1 |
| S-2 | Yükleme sürerken arama eski parçalardan; bitince yenilerden | Backend (TASK-002), değişmez | qa Q2 |
| S-3 | `.md` ile yeni kaynak → İşleniyor → Hazır → kelime aramasında bulunur | Tümü | F10, E2; qa Q3 |
| S-4 | `.docx`, 25 MB PDF, boş `.txt` → yükleme yok, uyarı | `validateUploadFile` | F1, F11, E3; qa Q4 |
| S-5 | Ollama kapalı → Başarısız + "embedding servisine ulaşılamıyor"; eski sürüm aranabilir | `ingestionFailureReasonFor('EMBEDDING_UNAVAILABLE')` | F6, F19; qa Q5 |
| S-6 | Sadece görsel PDF → "dosyada metin yok" | `NO_TEXT_EXTRACTED` | F6, F19; qa Q6 |
| S-7 | İş sürerken buton pasif | K8 | F17; qa Q7 |
| S-8 | Web sitesi / Talep geçmişinde buton yok | K5 | F14; qa Q8 |
| S-9 | Klavyeyle aç, Esc, odak geri | K15 | F22, E4; qa Q9 |
| AC-1 | `npm run build`, `npm run lint`, frontend testleri, `./mvnw verify` | Tüm kod | tester + qa |
| AC-2 | E2E: yeni sürüm + ilerleme + yenileme, yeni kaynak, geçersiz dosya; TASK-003 E2E'leri geçmeye devam | `frontend/e2e/upload.spec.ts` + eskiler | E1-E5 + TASK-003 E1, E3, E4, E5 |
| AC-3 | Compose sonrası 3 servis `healthy` | Değişiklik yok; qa `--build` ile doğrular | qa Q0 |
| AC-4 | Örnek senaryoların hepsi gerçek sistemde, qa raporunda | qa | Q1-Q9 |
| AC-5 | Yeni/değişen endpoint sözleşmede; backend ve frontend birebir | `openapi.yaml` 0.4.0 | B6; qa `/v3/api-docs.yaml` karşılaştırması + `model/types.ts` |
| AC-6 | Bütün `errorCode`'ların (iş kodları dahil) Türkçesi | `errorMessages.ts` (21 HTTP + 7 iş kodu) | F6 + TASK-003 F7 |
| AC-7 | Üretim build'inde konsolda CSP hatası yok | D9 | E5; qa Q-CSP |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | Yeni sürüm diyaloğunda kaynağın türünden farklı bir dosya (PDF kaynağa `.md`) | Dosya seçici sadece o türün uzantılarını önerir. Yine de seçilirse "Bu kaynağa sadece PDF dosyası yüklenebilir." uyarısı çıkar, istek gitmez. Backend'in `SOURCE_KIND_MISMATCH`'i de dosya alanının altında gösterilir | F2, F16 |
| G2 | Büyük harfli uzantı (`KILAVUZ.PDF`), Türkçe karakterli dosya adı (`Şifre notları.markdown`), uzantısız ya da çift uzantılı ad (`arsiv.pdf.zip`) | Uzantı büyük/küçük harf duyarsız; Türkçe ad olduğu gibi gönderilir ve gösterilir; `arsiv.pdf.zip` ve uzantısız ad desteklenmeyen tür | F1 |
| G3 | Sorgulama sırasında backend'e geçici olarak ulaşılamıyor (backend yeniden başlıyor) | Sorgulama durmaz, satırda son bilinen aşama kalır. Backend açılınca iş `INGESTION_INTERRUPTED` ile Başarısız olur ve "iş yarıda kaldı" yazar | F25 |
| G4 | Sınır boyutlar: tam 20 MB (20.971.520 bayt), 20 MB + 1 bayt, 0 bayt | 20 MB kabul; +1 bayt "20 MB'tan büyük olamaz"; 0 bayt "Dosya boş" | F1 |
| G5 | Daha önce bitmiş bir işi olan sayfa yeniden açılıyor (yenileme, yeni sekme) | Tamamlanmış iş için toast ve vurgu tekrar çıkmaz, sorgulama başlamaz. Son iş başarısızsa neden satırda yazmaya devam eder | F20 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | `SourceResponse.latestJob`: kaynağın en büyük `id`'li işi, tam `IngestionJobResponse` şekliyle; işi yoksa `null`. Hem `listSources` hem `getSource` doldurur. Liste sayfası için tek sorgu (`findLatestBySourceIds`, N+1 yok). Sözleşmede nullable, `required` listesinde değil (`version` gibi) | TASK-003 D1'in öngördüğü alan. Tek alan iki ihtiyacı da karşılar: yenilemeden sonra süren işin `jobId`'si ve başarısız yüklemenin `errorCode`'u (versiyon yüklemesi başarısızsa `status` `READY`'ye döner) | Ayrı `GET .../sources/{sourceId}/ingestion-jobs?latest`: liste sayfasında kaynak başına istek (N+1); `SourceResponse.activeJobId` + `lastErrorCode`: iki alan, iş bitince ilerleme bilgisi yine ayrı istek ister |
| D2 | Yükleme ve ilerleme kodu `features/source` içinde. `features/ingestion` açılmaz | Yükleme diyalogları kaynak ekranlarında. İş bitince `sourceKeys` invalidate edilir; ayrı bir feature `source` tiplerini ve key'lerini import edemezdi | `features/ingestion`: kaynak tipi ve query key'leri `shared/`'a taşınmak zorunda kalırdı |
| D3 | Diyalog, sunucu yüklemeyi kabul edince (202) kapanır. İstek sürerken birincil buton "Yükleniyor…" ve `disabled`; "Vazgeç" ve Esc de pasif. Sunucu reddederse diyalog açık kalır ve hata diyalogda gösterilir | K3 ile K4'ün birlikte sağlanması: reddedilen dosyanın hatası kullanıcının seçimini kaybetmeden gösterilir. Yerel sistemde 20 MB'lık yükleme kısa sürer | Gönderince hemen kapanmak: 409/415 hatasının gösterileceği yer kalmaz, kullanıcı formu yeniden doldurur |
| D4 | Sorgulama `WorkspaceLayout` içinde, sayfadan bağımsız bir `IngestionWatcherProvider`'da. Watcher `useSourcesQuery(workspaceId)` listesindeki aktif `latestJob`'ları `useQueries` ile 1 sn'de bir sorgular. İş `DONE`/`FAILED` olunca o işin sorgusu durur. Toast ve vurgu sadece bu sayfa açıkken aktif görülüp sonra biten işler için çıkar (watcher'ın gördüğü `jobId`'ler `ref`'te). İş başka sekmede başlatıldıysa sayfa yenilenince görünür (TASK kapsam dışı maddesi) | K11 toast'u ve K14 (başka sayfadayken iş bitebilir); K13 yenileme ve yeni sekme; G5 eski işler için tekrar bildirim yok. Satırlar aynı query key'inden okur, ikinci sorgu yok | Sorguyu satırda yapmak: Arama sayfasına geçince sorgu durur, iş orada biterse toast çıkmaz; kaynak listesini 1 sn'de bir yenilemek: TASK "iş durumu sorgulanır" diyor, liste daha ağır |
| D5 | Diyalog native `<dialog>` + `showModal()` (`shared/components/Dialog.tsx`). Tarayıcı Tab'ı diyalog içinde tutar (sayfanın geri kalanı `inert`), Esc `cancel` olayıyla kapatır. Açılınca odak ilk form alanına gider; kapanınca odak açılıştaki `document.activeElement`'e (açan buton) açıkça geri verilir. Arka plana tıklamak kapatmaz | Odak tuzağı, Esc ve üst katman tarayıcıdan gelir; yeni paket gerekmez | Elle yazılmış `role="dialog"` + odak tuzağı: daha çok kod; diyalog kütüphanesi: yeni dependency |
| D6 | Yeni sürüm diyaloğunda dosya kaynağın türüne göre de kontrol edilir: PDF → `.pdf`; MARKDOWN → `.txt`, `.md`, `.markdown`; CSV → `.csv`. `accept` özniteliği de buna göre | Backend aynı durumu `SOURCE_KIND_MISMATCH` ile reddediyor; istek atmadan söylemek daha iyi deneyim (G1) | Sadece backend'e bırakmak: kullanıcı 20 MB'ı gönderip sonra hata görür |
| D7 | İş hata nedenleri `shared/api/errorMessages.ts`'te ayrı bir eşleme (`INGESTION_FAILURE_REASONS`, `ingestionFailureReasonFor`). HTTP mesajlarından farklı: küçük harfle başlar, "Son yükleme başarısız: " ardına eklenir, "Kelime yöntemiyle ara" önerisi yoktur | Aynı kod (ör. `EMBEDDING_UNAVAILABLE`) aramada ve yüklemede farklı şey önerir. Convention: `errorCode` metinleri tek dosyada | HTTP mesajlarını tekrar kullanmak: yükleme satırında "Kelime yöntemiyle ara" yazardı |
| D8 | Toast ortak bileşen: `shared/toast/ToastProvider.tsx` + `shared/toast/useToast.ts` (`showToast(message)`), `WorkspaceLayout`'ta. Bölge `role="status"` (`aria-live="polite"`), alt ortada, `lab.html` `.toast` görünümü. Her toast `TOAST_DURATION_MS = 5000` sonra kalkar. `FAILED` için toast yok; neden satırda yazar | K11. Ekran okuyucu duyurusu; lab'daki 3,2 sn okumak için kısa | Toast'u source feature'ında tutmak: genel UI parçası, `shared/`'a ait |
| D9 | CSP: `vite.config.ts`'te `build.assetsInlineLimit: 0`. Hiçbir asset `data:` URI olarak gömülmez, yazı tipleri `/assets/*.woff2` dosyası olur. nginx CSP'si aynen kalır | TASK "politika gevşetilmeden tercih edilir". QA bulgusunun iki çözümünden CSP'yi değiştirmeyen | CSP'ye `font-src 'self' data:`: politika gevşer, devops rolü gerekir |
| D10 | `httpClient.request`: gövde `FormData` ise `Content-Type` eklenmez (tarayıcı `multipart/form-data; boundary=…` koyar). Diğer gövdelerde davranış aynı | TASK-003 D14'te sonraya bırakılan multipart desteği | Ayrı `requestMultipart`: aynı hata eşlemesini iki kez yazmak |
| D11 | Rozet ve buton durumu: `isSourceIngesting(source, job)` = (iş varsa aktif aşamada) ya da `source.status === 'INGESTING'`. Satırda ve detayda iş aktifse rozet "İşleniyor". İş bittiyse rozet kaynağın kendi `status`'u | Watcher işin bittiğini liste yenilenmeden önce görebilir; o arada rozet ve buton işten okunur | Sadece `source.status`: liste yenilenene kadar buton yanlış durumda kalır |
| D12 | Başarısızlık satırı son iş `FAILED` olduğu sürece görünür: sonraki yükleme başlayınca ya da başarılı olunca kalkar. Yenilemeden sonra da görünür (`latestJob`) | "Son yükleme başarısız" metni ve S-5 (neden kalıcı olmalı, kullanıcı başka sayfaya gidip dönebilir) | Sadece o oturumda göstermek: yenileyince neden kaybolur |
| D13 | Toast metni `` `${sourceName}${job.version ? ' v' + job.version : ''} hazır · ${formatInteger(job.totalChunks ?? 0)} parça` ``. Sürüm etiketi yoksa sürüm kısmı yazılmaz | `lab.html` "Modem kılavuzu v3.2 hazır · 214 parça" biçimi; sürüm etiketi isteğe bağlı. Yeni parça sayısı işin `totalChunks`'ıdır (DONE'da yazılan parça sayısı); ek istek gerekmez | Toast'tan önce kaynağı yeniden okumak: ek istek, gecikme |
| D14 | Satır vurgusu: iş `DONE` görülünce satır `COMPLETED_HIGHLIGHT_MS = 3000` boyunca `isJustCompleted` sınıfını taşır (lab `.hl` rengi). `prefers-reduced-motion`'da geçiş animasyonu yok | K11 "kısa bir süre" | — |
| D15 | Ad ve dosya kuralları backend ile aynı: ad kırpılıp 2-80 karakter, `maxLength=80`; sürüm etiketi kırpılıp en fazla 40, `maxLength=40`, boşsa gönderilmez; dosya en fazla 20.971.520 bayt, 0 bayt değil. Dosya seçilince hemen, ad ve sürüm gönderimde kontrol edilir. Gönderimde hata varsa ilk hatalı alana odaklanılır | Backend doğruluk kaynağı (`openapi.yaml`). Tek karakterlik ad backend'de de geçersiz | Sadece "boş olmasın": tek karakter backend'den `VALIDATION_FAILED` döner |
| D16 | Polling hatası (ağ, 5xx) satırda gösterilmez; sorgulama aralıkla sürer, son bilinen aşama kalır. 4xx (`INGESTION_JOB_NOT_FOUND`) gelirse o işin sorgusu durur ve kaynak listesi invalidate edilir | G3. Geçici kopukluk kullanıcıya gürültü; iş backend'de sürer ya da `INGESTION_INTERRUPTED` olur | Hata gösterip sorguyu durdurmak: backend açılınca ilerleme kaybolur |
| D17 | E2E yeni kaynak testi her koşuda bir kaynak ekler (silme yok). Test, `sourceCount < 10` olan en yüksek id'li çalışma alanını API'den seçer, adı `E2E notları <Date.now()>`. Yeni sürüm testi her koşuda 302'ye `3.2` yükler | Kaynak sınırı 10; 42 ve 19 diğer E2E'lerde kullanılıyor; tekrar koşulabilir olmalı | Sabit alan 42: 6 koşudan sonra `SOURCE_LIMIT_REACHED` |

## Sözleşme değişiklikleri
`docs/api/openapi.yaml` (`info.version` 0.3.0 → **0.4.0**). Path, method, status ve hata davranışı aynı. Yeni `errorCode` yok (21 HTTP kodu + 7 iş kodu).
- **Eklenen alan:** `SourceResponse.latestJob`: `anyOf: [$ref IngestionJobResponse, {type: 'null'}]`, `required` dışında, açıklamalı (D1). `GET .../sources` ve `GET .../sources/{sourceId}` ikisi de döner.
- **Örnek:** `getSource` 200'e iki örnek (`newVersionEmbedding`: `INGESTING` + `latestJob.stage EMBEDDING 96/214`; `seedSource`: `latestJob: null`).
- qa karşılaştırmada path, method, status, `errorCode`, alan adları ve `required` listelerine bakar. `null` yazılış farkı (`anyOf` ↔ SpringDoc çıktısı) TASK-003'teki gibi şekil olarak eşdeğerse sorun değildir.

**Uygulama notu 1 (backend-coder'ın bildirimi; sözleşme değişmedi):**
- **Sorun:** SpringDoc OAS 3.1'de `@Schema(nullable = true)` ile `{"type": "null", "$ref": ...}` üretiyor. 3.1'de bu iki anahtar birlikte sağlanmak zorunda olduğu için şema sözleşmedeki `anyOf` ile eşdeğer değil.
- **Çözüm:** Backend, `OpenApiConfig`'e küçük bir `OpenApiCustomizer` ekler. Bu customizer `SourceResponse.properties.latestJob`'u `{description, anyOf: [$ref IngestionJobResponse, {type: 'null'}]}` olarak yazar; alan `required` dışında kalır. Böylece `/v3/api-docs` sözleşmeyle birebir aynı olur.
- **Testlere etkisi:** B6 (`givenApplication_whenGetApiDocs_thenVersionAndLatestJob`) şunları da doğrular:
  - `latestJob`'da `anyOf` var;
  - ilk öğe `$ref` `#/components/schemas/IngestionJobResponse`, ikinci öğe `type: null`;
  - alan `required`'da yok.

**Uygulama notu 2 (frontend-coder'ın bildirimi; sözleşme ve kart değişmedi):**
- **Sorun:** D5'teki native `<dialog>` + `showModal()` Chromium'da sayfanın geri kalanını `inert` yapıyor. Ama son odaklanabilir öğeden sonra Tab, odağı diyalogdan çıkarıp tarayıcı arayüzüne bırakıyor (headless'ta `document.activeElement` = `BODY`). K15 ve E4 bu hâliyle tutmazdı.
- **Çözüm:** `shared/components/Dialog.tsx` `onKeyDown`'da Tab / Shift+Tab'ı yakalar ve odağı diyaloğun ilk ve son odaklanabilir öğesi arasında döndürür. Yeni paket yok.
- **Testlere etkisi:**
  - F22'ye bir beklenti eklenir: son odaklanabilir öğede ("Kaynağı ekle") Tab → odak ilk öğeye ("Kaynak adı"); ilk öğede Shift+Tab → odak son öğeye.
  - E4'teki "10 kez Tab → `activeElement` diyalog içinde" beklentisi aynen geçerli.

## Backend planı
Yüklenecek skill'ler: `backend-conventions`, `jpa-patterns`, `spring-boot-openapi-documentation`, `springboot-patterns`.
Yeni dependency yok. Migration yok. Mevcut `idx_ingestion_jobs_source_id` index'i yeterli.

| Sınıf | Değişiklik ve imza |
|-------|--------------------|
| `IngestionJobRepository` | + `List<IngestionJob> findLatestBySourceIds(@Param("sourceIds") Collection<Long> sourceIds)`, açık JPQL (`getSourceId()` türetilmiş getter, derived query yazılmaz): `select j from IngestionJob j where j.source.id in :sourceIds and j.id = (select max(j2.id) from IngestionJob j2 where j2.source.id = j.source.id)`. Boş koleksiyonla çağrılmaz (service kontrol eder) |
| `SourceResponse` | + son bileşen `@Nullable IngestionJobResponse latestJob` (açıklama sözleşmedekinin İngilizce kısaltması). Şema `OpenApiConfig`'teki bir `OpenApiCustomizer` ile `anyOf: [$ref IngestionJobResponse, {type: 'null'}]` olarak yazılır (bkz. "Sözleşme değişiklikleri", uygulama notu 1). `static SourceResponse from(Source source, @Nullable IngestionJob latestJob)`; eski tek parametreli `from(Source)` kaldırılır (çağıran yerler güncellenir). SpringDoc çıktısı alanı `IngestionJobResponse` referansı ve null'a izinle vermeli; `required`'da olmamalı |
| `SourceService` | Constructor'a `IngestionJobRepository`. `listSources`: sayfadaki kaynakların id'leriyle tek `findLatestBySourceIds` → `Map<Long, IngestionJob>` (`IngestionJob::getSourceId`) → `SourceResponse.from(source, map.get(id))`. Sayfa boşsa sorgu atılmaz. `getSource`: aynı metot tek id'yle. `getExistingSource` değişmez |
| `OpenApiConfig` | `API_VERSION = "0.4.0"`; `ERROR_CODES` değişmez |
| `backend/CLAUDE.md` | Komutlara bir satır: `curl -s localhost:8080/api/workspaces/42/sources/302` → `latestJob` ile kaynak (yenilemeden sonra süren işi bulmak için) |

- `SourceService` → `IngestionJobRepository` bağımlılığı paket yönünden `source → ingestion` ekler. `IngestionJob` zaten `Source`'a bağlı; bean döngüsü yok (`IngestionService` → `SourceService` aynı kalır).
- `IngestionService`, `IngestionJobRunner`, `Source` değişmez.
- Log eklenmez (okuma).

## Frontend planı
Yüklenecek skill'ler: `frontend-conventions`, `react-patterns`, `react-state-management`, `frontend-a11y`, `frontend-design`, `frontend-security`, `vite-patterns`.
Yeni paket yok.

### Dosyalar
```
src/
├── app/
│   └── WorkspaceLayout.tsx            ToastProvider (en dışta) + LastSearchProvider key + IngestionWatcherProvider key={workspaceId} + Outlet
├── shared/
│   ├── api/httpClient.ts              D10
│   ├── api/errorMessages.ts           + INGESTION_FAILURE_REASONS, ingestionFailureReasonFor (D7)
│   ├── components/Dialog.tsx (+css)   D5
│   ├── toast/ToastProvider.tsx (+css) D8; context + bölge render'ı
│   ├── toast/useToast.ts              { showToast(message: string): void }
│   └── format/numberFormat.ts         + formatFileSize
└── features/source/
    ├── api/sourceApi.ts               + createSource, uploadSourceVersion, getIngestionJob
    ├── api/sourceQueries.ts           + key, mutation'lar, job sorguları
    ├── components/
    │   ├── CreateSourceDialog.tsx (+css)
    │   ├── UploadVersionDialog.tsx (+css)
    │   ├── UploadVersionButton.tsx    buton + diyalog; satır ve detay başlığında aynı component
    │   ├── FileField.tsx (+css)       etiketli file input + seçilen dosya kutusu (ad, boyut) + hata
    │   ├── IngestionProgress.tsx (+css)  aşama metni + ilerleme çubuğu
    │   ├── IngestionFailure.tsx       "Son yükleme başarısız: …"
    │   ├── IngestionWatcherProvider.tsx  D4
    │   ├── SourceRow.tsx              + buton, ilerleme, başarısızlık, vurgu
    │   └── SourceInfoBoxes.tsx        durum rozeti D11
    ├── hooks/
    │   ├── useRecentlyCompletedSourceIds.ts  watcher context'inden ReadonlySet<number>
    │   └── useSourceIngestion.ts      (workspaceId, source) → { job, isIngesting }
    ├── pages/SourcesPage.tsx          + "Yeni kaynak ekle" butonu (başlık satırında, sağda)
    ├── pages/SourceDetailPage.tsx     + başlıkta UploadVersionButton; altında IngestionProgress / IngestionFailure
    └── model/
        ├── types.ts                   + IngestionStage, IngestionErrorCode, IngestionJobResponse, SourceResponse.latestJob
        ├── uploadRules.ts             dosya, ad, sürüm kuralları
        ├── uploadFormErrors.ts        API hatası → alan hataları
        └── ingestionRules.ts          aşama metni, yüzde, aktiflik, toast metni
```

### Tipler (sözleşmeyle birebir, `features/source/model/types.ts`)
- `INGESTION_STAGES = ['QUEUED','PARSING','CHUNKING','EMBEDDING','WRITING','DONE','FAILED'] as const`, `IngestionStage`.
- `INGESTION_ERROR_CODES = ['NO_TEXT_EXTRACTED','FILE_UNREADABLE','EMBEDDING_UNAVAILABLE','EMBEDDING_MODEL_NOT_FOUND','EMBEDDING_MODEL_MISMATCH','INGESTION_INTERRUPTED','INTERNAL_ERROR'] as const`, `IngestionErrorCode`.
- `IngestionJobResponse = { jobId, workspaceId, sourceId: number; version?: string | null; fileName: string; stage: IngestionStage; progress: number; embeddedChunks: number; totalChunks?: number | null; errorCode?: IngestionErrorCode | null; createdAt: string; startedAt?: string | null; finishedAt?: string | null; statusUrl: string }`.
- `SourceResponse` + `latestJob?: IngestionJobResponse | null`.

### API ve query hook'ları
| Dosya | Fonksiyon / hook | Not |
|-------|------------------|-----|
| `sourceApi.ts` | `createSource(workspaceId, { name, file }): Promise<IngestionJobResponse>` | `POST /api/workspaces/${workspaceId}/sources`, `FormData` parçaları `name` (kırpılmış), `file` |
| | `uploadSourceVersion(workspaceId, sourceId, { file, version }): Promise<IngestionJobResponse>` | `POST .../sources/${sourceId}/versions`; `version` kırpılıp boş değilse eklenir |
| | `getIngestionJob(workspaceId, jobId): Promise<IngestionJobResponse>` | `GET /api/workspaces/${workspaceId}/ingestion-jobs/${jobId}` |
| `sourceQueries.ts` | `sourceKeys.ingestionJob(workspaceId, jobId) = [...sourceKeys.all(workspaceId), 'ingestion-jobs', jobId]` | `sourceKeys.all` invalidate'i işleri de kapsar |
| | `useCreateSourceMutation(workspaceId)` | `onSuccess(job)`: `setQueryData(ingestionJob key, job)`; invalidate `sourceKeys.list(workspaceId)`, `workspaceKeys.all` |
| | `useUploadSourceVersionMutation(workspaceId, sourceId)` | `onSuccess(job)`: `setQueryData`; invalidate `sourceKeys.list`, `sourceKeys.detail(workspaceId, sourceId)`. `onError` `INGESTION_IN_PROGRESS` ise aynı iki invalidate (buton pasifleşir) |
| | `useIngestionJobsPolling(workspaceId, jobs: IngestionJobResponse[])` | `useQueries`: her iş için key `ingestionJob`, `initialData: job`, `refetchInterval: (query) => isJobActive(query.state.data) ? INGESTION_POLL_INTERVAL_MS : false`, `refetchIntervalInBackground: true`, `retry: false` (D16; aralık zaten tekrar dener) |
| | `useIngestionJobQuery(workspaceId, latestJob: IngestionJobResponse \| null)` | Satır ve detay için: aynı key, `initialData: latestJob`, `enabled: false` (sorgu watcher'dan gelir); `data ?? latestJob` |

### Kurallar ve sabitler
**`uploadRules.ts`**
- Sabitler:
  - `ACCEPTED_EXTENSIONS = ['.pdf', '.txt', '.md', '.markdown', '.csv']`
  - `KIND_EXTENSIONS: Record<'PDF' | 'MARKDOWN' | 'CSV', string[]>`
  - `MAX_FILE_SIZE_BYTES = 20_971_520`
  - `SOURCE_NAME_MIN_LENGTH = 2`, `SOURCE_NAME_MAX_LENGTH = 80`, `VERSION_LABEL_MAX_LENGTH = 40`
- Fonksiyonlar:
  - `fileExtension(fileName): string` (son noktadan sonrası, `toLocaleLowerCase('tr')` değil `toLowerCase()`; uzantılar ASCII).
  - `acceptAttribute(kind?: SourceKind): string`.
  - `validateUploadFile(file: File | null, kind?: SourceKind): string | null`. Sıra: dosya yok → tür (genel uzantı listesi) → kaynak türü (verilmişse) → boş → boyut.
  - `validateSourceName(raw: string): string | null`, `normalizeSourceName = trim`.
  - `validateVersionLabel(raw: string): string | null`.
- Mesajlar:
  - `FILE_REQUIRED_MESSAGE = 'Yüklenecek dosyayı seç.'`
  - `FILE_TYPE_RULE_MESSAGE = 'Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.'`
  - `fileKindRuleMessage(kind)` → `` `Bu kaynağa sadece ${UPLOAD_KIND_LABELS[kind]} dosyası yüklenebilir.` ``; `UPLOAD_KIND_LABELS = { PDF: 'PDF', MARKDOWN: 'TXT, MD ya da MARKDOWN', CSV: 'CSV' }`
  - `FILE_EMPTY_RULE_MESSAGE = 'Dosya boş. İçinde metin olan bir dosya seç.'`
  - `FILE_SIZE_RULE_MESSAGE = "Dosya 20 MB'tan büyük olamaz."`
  - `SOURCE_NAME_RULE_MESSAGE = 'Kaynak adı 2-80 karakter olmalı.'`
  - `VERSION_RULE_MESSAGE = 'Sürüm etiketi en fazla 40 karakter olabilir.'`

**`uploadFormErrors.ts`:** `toUploadFieldErrors(error: unknown): { name?: string; file?: string; version?: string; form?: string }`

| Gelen hata | Nereye | Metin |
|------------|--------|-------|
| `VALIDATION_FAILED` + `violations[].field` = `name` / `file` / `version` | O alan | `SOURCE_NAME_RULE_MESSAGE` / `FILE_EMPTY_RULE_MESSAGE` / `VERSION_RULE_MESSAGE` |
| `SOURCE_NAME_TAKEN` | `name` | `messageFor` |
| `FILE_TOO_LARGE`, `UNSUPPORTED_FILE_TYPE`, `UNSUPPORTED_FILE_ENCODING`, `SOURCE_KIND_MISMATCH` | `file` | `messageFor` |
| Diğerleri (`SOURCE_LIMIT_REACHED`, `INGESTION_IN_PROGRESS`, `EMBEDDING_MODEL_MISMATCH`, `WORKSPACE_NOT_FOUND`, `SOURCE_NOT_FOUND`, `NETWORK_ERROR`, `INTERNAL_ERROR`, bilinmeyen) | `form` | `messageForError`; diyalog içinde `role="alert"` |

**`ingestionRules.ts`**
- Sabitler: `INGESTION_POLL_INTERVAL_MS = 1000`, `COMPLETED_HIGHLIGHT_MS = 3000`.
- `isJobActive(job: IngestionJobResponse | null | undefined): boolean` (`DONE`/`FAILED` dışı).
- `isUploadableKind(kind): boolean` (`PDF`, `MARKDOWN`, `CSV`). Bu fonksiyon `sourceRules.ts`'e konur.
- `isSourceIngesting(source, job)` (D11).
- `INGESTION_STAGE_LABELS`:
  - `QUEUED` → "Sıraya alındı"
  - `PARSING` → "Dosya okunuyor"
  - `CHUNKING` → "Parçalara bölünüyor"
  - `WRITING` → "Yazılıyor"
- `ingestionStageText(job)`: `EMBEDDING` → `` `Embed ediliyor · ${formatInteger(embeddedChunks)}/${totalChunks == null ? '…' : formatInteger(totalChunks)} parça` ``.
- `progressPercent(job) = Math.round(job.progress * 100)`.
- `ingestionDoneMessage(sourceName, job)` (D13).
- `ingestionFailureLine(job)` = `` `Son yükleme başarısız: ${ingestionFailureReasonFor(job.errorCode)}` ``.

**`errorMessages.ts`'e eklenecek iş hata nedenleri (D7)**
| İş `errorCode` | Neden metni |
|----------------|-------------|
| `NO_TEXT_EXTRACTED` | dosyada metin yok (sadece görsel içeren PDF ya da sadece başlık satırı olan CSV olabilir) |
| `FILE_UNREADABLE` | PDF okunamıyor (dosya bozuk ya da parola korumalı olabilir) |
| `EMBEDDING_UNAVAILABLE` | embedding servisine (Ollama) ulaşılamıyor |
| `EMBEDDING_MODEL_NOT_FOUND` | embedding modeli Ollama'da yüklü değil (ollama pull bge-m3) |
| `EMBEDDING_MODEL_MISMATCH` | model uyuşmazlığı: embedding modeli beklenenden farklı boyutta vektör döndürdü |
| `INGESTION_INTERRUPTED` | iş yarıda kaldı (sunucu iş sürerken durdu); dosyayı tekrar yükle |
| `INTERNAL_ERROR` | sunucuda beklenmeyen bir hata oluştu |
| `null` ya da bilinmeyen | beklenmeyen bir hata oluştu |

**`numberFormat.ts`:** `formatFileSize(bytes)`, 1024 tabanlı, `tr-TR`, en fazla 1 ondalık:
- < 1024 → `"830 B"`
- < 1024² → `"1,5 KB"`
- diğer → `"2,4 MB"`; 20.971.520 → `"20 MB"`

### Ekran davranışı
| Component | Davranış |
|-----------|----------|
| `Dialog` | **Props:** `{ title: string; isOpen: boolean; onClose(): void; canClose?: boolean; initialFocusRef?: RefObject<HTMLElement \| null>; children }`.<br>**Açılış:** `isOpen` true → `showModal()`; odak `initialFocusRef`'e, yoksa ilk odaklanabilir öğeye.<br>**Esc:** `cancel` olayında `preventDefault()`; `canClose !== false` ise `onClose()`.<br>**Kapanış:** `close()` ve odak açılıştaki öğeye.<br>**Öznitelikler ve görünüm:** `aria-labelledby` başlığa (`<h2>`). Görünüm `lab.html` `.dlg` (genişlik `min(420px, 100%)`), backdrop `::backdrop` yarı saydam |
| `SourcesPage` | Başlık satırının sağında "Yeni kaynak ekle" butonu (birincil). Liste yüklenmeden de görünür |
| `CreateSourceDialog` | **Başlık ve açıklama:** başlık "Yeni kaynak ekle"; açıklama "Dosya yüklenince parçalara bölünür ve embedding'e çevrilir. İş bitene kadar kaynak listede İşleniyor görünür."<br>**Alanlar:** "Kaynak adı" (`maxLength=80`, placeholder "ör. Fatura SSS", ilk odak); `FileField` etiket "Dosya", `accept=acceptAttribute()`.<br>**Butonlar:** "Vazgeç", "Kaynağı ekle" (pending → "Yükleniyor…").<br>**Gönderim:** başarıda diyalog kapanır (D3); her açılışta form sıfırlanır |
| `UploadVersionButton` | **Görünürlük:** `isUploadableKind(source.kind)` değilse hiçbir şey render etmez.<br>**Buton:** metin "Yeni sürüm yükle", ikincil/küçük. `isSourceIngesting` → `disabled` + `title="Yükleme sürüyor"`.<br>**Satır içinde:** tıklama satır gezinmesini tetiklemez (`stopPropagation`; `SourceRow.handleRowClick` `button`'ları da yok sayar) |
| `UploadVersionDialog` | **Başlık ve bilgi:** başlık "Yeni sürüm yükle"; özet `` `${name} · şu an ${versionLabel(version)} · ${formatInteger(chunkCount)} parça` ``; bilgi kutusu (callout) "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür."<br>**Alanlar:** `FileField` (`accept=acceptAttribute(kind)`, ilk odak); "Sürüm etiketi (isteğe bağlı)" (`maxLength=40`, placeholder "ör. 3.2").<br>**Butonlar:** "Vazgeç", "Yeni sürümü yükle" (pending → "Yükleniyor…") |
| `FileField` | **Input:** native `<input type="file">` + görünür `<label>`.<br>**Seçilen dosya kutusu:** dosya seçilince altında lab `.file` kutusu: dosya adı (kalın) + `formatFileSize(size)`.<br>**Kontrol:** seçimde `validateUploadFile`; hata alanın altında `role="alert"`, input'ta `aria-invalid` + `aria-describedby`. Dosya adı düz metin |
| `IngestionProgress` | **Aşama metni:** lab `.prog`: üstte `ingestionStageText(job)`.<br>**Çubuk:** `role="progressbar"`, `aria-valuemin=0`, `aria-valuemax=100`, `aria-valuenow=progressPercent`, `aria-valuetext` = aşama metni, `aria-label` = `` `${sourceName} yükleme ilerlemesi` ``. Genişlik inline `style={{ width: '46%' }}`; `transition` `prefers-reduced-motion`'da kapalı |
| `IngestionFailure` | Kırmızı küçük metin `ingestionFailureLine(job)`. Son iş `FAILED` olduğu sürece (D12) |
| `SourceRow` | **Ad hücresi:** ad + dosya satırı; altında sırasıyla `IngestionProgress` (iş aktifse), `IngestionFailure` (iş `FAILED` ise), `UploadVersionButton`.<br>**Durum:** iş aktifse "İşleniyor" rozeti (D11).<br>**Vurgu:** `useRecentlyCompletedSourceIds().has(sourceId)` → `isJustCompleted` sınıfı |
| `SourceDetailPage` | **Başlık:** başlık satırında sağda `UploadVersionButton`.<br>**İlerleme ve hata:** başlık altında `IngestionProgress` / `IngestionFailure`.<br>**Durum:** `SourceInfoBoxes` durum rozeti D11'e göre.<br>**İş `DONE` olunca:** `sourceKeys.all` invalidate edildiği için sürüm, parça sayısı ve parça listesi yeniden yüklenir |
| `IngestionWatcherProvider` | **Props:** `{ workspaceId, children }`.<br>**Sorgulama:** `useSourcesQuery(workspaceId)` + `useIngestionJobsPolling` (aktif `latestJob`'lar).<br>**`jobId` takibi:** bir `jobId` aktif görüldüğünde `ref` `Set`'e eklenir. Aynı `jobId` sonra `DONE`/`FAILED` görülürse bir kez `handleJobFinished` çalışır (StrictMode'da çift çalışmaya karşı `handled` set'i).<br>**DONE:** `showToast(ingestionDoneMessage(source.name, job))`, `sourceId` vurgu set'ine eklenir ve 3 sn sonra çıkarılır (timer cleanup'ta temizlenir), invalidate `sourceKeys.all(workspaceId)` + `workspaceKeys.all`.<br>**FAILED:** invalidate `sourceKeys.list`, `sourceKeys.detail(workspaceId, sourceId)`, `workspaceKeys.all`; toast yok.<br>**Liste yenilenince:** watcher yeni aktif işleri görür (yükleme mutation'ı listeyi invalidate eder) |
| `ToastProvider` | Toast listesi; `showToast` id'li ekler, 5 sn sonra kaldırır. Bölge `role="status"`, `aria-live="polite"`, `position: fixed` alt orta; lab `.toast` görünümü; `prefers-reduced-motion`'da giriş animasyonu yok |

### `vite.config.ts`
- `build: { assetsInlineLimit: 0 }` (D9).
- Diğer ayarlar aynı.

### Görsel dil
`lab.html`'deki `.dlg`, `.file`, `.prog`, `.toasts`/`.toast`, `.btn.out.sm`, `tr.hl` stilleri, mevcut token'larla (`--color-*`, `--space-*`) taşınır. Ham hex yazılmaz; gereken yeni token (ör. `--color-toast-bg`) `index.css`'e eklenir.

## Test senaryoları
**Backend:** TASK-002/003 altyapısı (`PostgresIntegrationTest`, `TestcontainersConfiguration`, fixture'lar, `FakeEmbeddingClient`, `FixedClockTestConfiguration`).

**Frontend:** `src/test/` altına tester şunları ekler:
- MSW handler'ları:
  - `POST /api/workspaces/:workspaceId/sources` (202, `IngestionJobResponse`, `Location`);
  - `POST /api/workspaces/:workspaceId/sources/:sourceId/versions`;
  - `GET /api/workspaces/:workspaceId/ingestion-jobs/:jobId`.
- Fixture'lar:
  - bütün kaynaklara `latestJob: null`;
  - iş fixture'ları sözleşmedeki bütün alanlarla.
- jsdom'da `HTMLDialogElement.prototype.showModal`/`close` yoksa `setup.ts`'te en küçük taklit (sadece `open` özniteliği + `close` olayı). Odak tuzağı ve Esc gerçek tarayıcıda E4 ile doğrulanır.
- Sorgulama testleri sahte zamanlayıcıyla (`vi.useFakeTimers({ shouldAdvanceTime: true })` + `vi.advanceTimersByTimeAsync(1000)`) yazılır; sabit bekleme yok.

| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| B1 | En yeni iş (D1) | repository `IngestionJobRepositoryTest` (`@DataJpaTest`) | `givenJobsOfSeveralSources_whenFindLatestBySourceIds_thenReturnsHighestIdPerSource` | **Veri:** kaynak A'da işler id 1 (`FAILED`), 3 (`DONE`); kaynak B'de id 2 (`EMBEDDING`); kaynak C'de iş yok.<br>**Beklenen:** `[A, B, C]` → `{A: 3, B: 2}` (2 kayıt); `[C]` → boş |
| B2 | Liste ve detayda `latestJob` (K13, K18) | integration `SourceIntegrationTest` | `givenSourceWithTwoJobs_whenListAndGetSource_thenLatestJobIsNewest` | 302'ye iki yükleme (ilk `FAILED`, sonra `DONE` sürüm `3.2`) → `listSources(42)` 302 satırında `latestJob.jobId` ikinci işin id'si, `stage DONE`, `version "3.2"`; 301 (seed) `latestJob` null; `getSource(42, 302)` aynı `latestJob` |
| B3 | Aktif iş ilerlemesi | integration | `givenJobEmbeddingNinetySixOfTwoHundredFourteen_whenGetSource_thenLatestJobShowsProgress` | İş elle `EMBEDDING`, `totalChunks 214`, `embeddedChunks 96` → yanıtta `status INGESTING`, `latestJob.stage EMBEDDING`, `progress` ≈ `0.458879` (`within(1e-6)`), `statusUrl "/api/workspaces/42/ingestion-jobs/<id>"` |
| B4 | Başarısız sürüm yüklemesi (K12, D12) | integration | `givenVersionUploadFailsWithEmbeddingUnavailable_whenGetSource_thenStatusReadyAndLatestJobFailed` | `FakeEmbeddingClient` hata verir → iş bitince `status READY`, `version "3.1"` (eski), `latestJob.stage FAILED`, `latestJob.errorCode EMBEDDING_UNAVAILABLE` |
| B5 | Tek sorgu (N+1 yok) | unit `SourceServiceTest` | `givenPageOfThreeSources_whenListSources_thenLoadsLatestJobsOnce` | `findLatestBySourceIds` tek kez `[301, 302, 303]` ile çağrılır; dönen iş 302'ye eşlenir, diğerlerinde `latestJob` null. Boş sayfada repository çağrılmaz |
| B6 | JSON şekli + sözleşme (AC-5) | controller `SourceControllerTest` + integration `OpenApiDocumentationIntegrationTest` | • `givenSourceWithoutJob_whenGetSource_thenLatestJobIsNull`<br>• `givenSourceWithJob_whenGetSource_thenLatestJobHasContractFields`<br>• `givenApplication_whenGetApiDocs_thenVersionAndLatestJob` | • JSON'da `"latestJob": null` anahtarı var<br>• İç içe alanlar `jobId, workspaceId, sourceId, version, fileName, stage, progress, embeddedChunks, totalChunks, errorCode, createdAt, startedAt, finishedAt, statusUrl`<br>• `info.version = 0.4.0`; `SourceResponse.properties.latestJob` var ve `required`'da değil; `errorCode` enum'u 21 değer |
| F1 | Dosya kuralı (K2, G2, G4) | unit `uploadRules.test.ts` | `givenFiles_whenValidateUploadFile_thenReturnsRuleMessage` (tablo) | **Uyarı dönenler:**<br>• `null` → `FILE_REQUIRED_MESSAGE`<br>• `kilavuz.docx` 10 B → `FILE_TYPE_RULE_MESSAGE`<br>• `arsiv.pdf.zip` → tür mesajı<br>• `README` → tür mesajı<br>• `bos.txt` 0 B → `FILE_EMPTY_RULE_MESSAGE`<br>• `k.pdf` 20.971.521 B → `FILE_SIZE_RULE_MESSAGE`<br>**`null` dönenler:**<br>• `k.pdf` 20.971.520 B<br>• `KILAVUZ.PDF`<br>• `Şifre notları.markdown`<br>• `tablo.csv`<br>• `notlar.txt` |
| F2 | Kaynak türü (D6, G1) | unit | `givenSourceKind_whenValidateUploadFile_thenRejectsOtherKinds` | PDF + `notlar.md` → `'Bu kaynağa sadece PDF dosyası yüklenebilir.'`; MARKDOWN + `a.txt` → null; MARKDOWN + `a.csv` → `'Bu kaynağa sadece TXT, MD ya da MARKDOWN dosyası yüklenebilir.'`; `acceptAttribute('PDF')` → `'.pdf'`, `acceptAttribute()` → `'.pdf,.txt,.md,.markdown,.csv'` |
| F3 | Ad ve sürüm (K2, D15) | unit | `givenNamesAndVersions_whenValidate_thenReturnsRuleMessage` (tablo) | Ad: `''`, `'   '`, `'a'`, 81 karakter → `SOURCE_NAME_RULE_MESSAGE`; `' Fatura SSS '`, 80 karakter → null. Sürüm: `''`, `'3.2'`, 40 karakter → null; 41 → `VERSION_RULE_MESSAGE` |
| F4 | Aşama metni (K9) | unit `ingestionRules.test.ts` | `givenJobStages_whenIngestionStageText_thenReturnsTurkishStageText` (tablo) | **Aşama metinleri:**<br>• QUEUED → "Sıraya alındı"<br>• PARSING → "Dosya okunuyor"<br>• CHUNKING → "Parçalara bölünüyor"<br>• WRITING → "Yazılıyor"<br>**Embed aşaması:**<br>• EMBEDDING 96/214 → "Embed ediliyor · 96/214 parça"<br>• 1200/1410 → "Embed ediliyor · 1.200/1.410 parça"<br>• `totalChunks` null → "Embed ediliyor · 0/… parça"<br>**Yüzde:** `progressPercent` 0.458879 → 46 |
| F5 | Toast metni (D13) | unit | `givenDoneJob_whenIngestionDoneMessage_thenNameVersionAndChunkCount` | ("Dalga T5 modem kullanım kılavuzu", version "3.2", 214) → "Dalga T5 modem kullanım kılavuzu v3.2 hazır · 214 parça"; ("Yardım notları", null, 12) → "Yardım notları hazır · 12 parça" |
| F6 | İş hata nedenleri (K12, AC-6, D7) | unit `errorMessages.test.ts` | `givenIngestionErrorCode_whenIngestionFailureReasonFor_thenReturnsTurkishReason` (tablo) | 7 kod için yukarıdaki tablodaki metin (literal); `null` ve `'SOMETHING_NEW'` → "beklenmeyen bir hata oluştu"; `ingestionFailureLine` (EMBEDDING_UNAVAILABLE) → "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" |
| F7 | Dosya boyutu biçimi | unit `numberFormat.test.ts` | `givenBytes_whenFormatFileSize_thenTurkishUnits` | 830 → "830 B"; 1536 → "1,5 KB"; 2.516.582 → "2,4 MB"; 20.971.520 → "20 MB" |
| F8 | Multipart (D10) | unit `httpClient.test.ts` (MSW) | `givenFormDataBody_whenRequest_thenSendsMultipartWithoutJsonContentType` | Handler'ın gördüğü `content-type` `multipart/form-data; boundary=` ile başlar; `name` parçası "Yardım notları". JSON gövdeli istekte hâlâ `application/json` |
| F10 | Yeni kaynak (K1, K4, S-3) | component `CreateSourceDialog.test.tsx` (`renderApp('/w/42/kaynaklar')`) | `givenValidNameAndMarkdown_whenAddSource_thenPostsMultipartClosesDialogAndListsIngestingSource` | **Diyalog:**<br>• "Yeni kaynak ekle" → diyalog açılır, odak "Kaynak adı"da<br>• `'  Yardım notları '` + `yardim-notlari.md` (120 B) → "Kaynağı ekle"<br>**İstek:** gövdede `name = 'Yardım notları'`, dosya adı `yardim-notlari.md`<br>**Sonuç:**<br>• Diyalog kapanır<br>• Liste yeniden istenir; 10001 "Yardım notları" satırında "İşleniyor" rozeti ve "Sıraya alındı" |
| F11 | Geçersiz dosya (K2, S-4) | component | `givenInvalidFile_whenSelected_thenShowsWarningUnderFieldAndSendsNothing` (tablo) | `kilavuz.docx` → tür mesajı; 25 MB `kilavuz.pdf` (`size` özelliği tanımlanmış `File`) → boyut mesajı; 0 B `bos.txt` → boş mesajı. Her biri dosya alanının altında `role="alert"`, input `aria-invalid="true"`; "Kaynağı ekle" sonrası POST sayısı 0 |
| F12 | Boş ad (K2) | component | `givenWhitespaceName_whenAddSource_thenShowsNameRuleAndFocusesName` | Ad `'   '` + geçerli dosya → `SOURCE_NAME_RULE_MESSAGE` ad alanının altında, odak ad alanında, POST 0 |
| F13 | Backend hataları (K3) | component | `givenBackendRejects_whenAddSource_thenShowsTurkishErrorInDialog` (tablo) | Diyalog her durumda açık kalır.<br>• 409 `SOURCE_NAME_TAKEN` → ad alanı altında "Bu ad çalışma alanında başka bir kaynakta kullanılıyor."<br>• 415 `UNSUPPORTED_FILE_ENCODING` → dosya alanı altında "Metin dosyası UTF-8 olmalı…"<br>• 409 `SOURCE_LIMIT_REACHED` → diyalogda `role="alert"` "Bu çalışma alanı 10 kaynak sınırına ulaştı."<br>• Ağ hatası → "Sunucuya ulaşılamıyor…"<br>`detail` metni ekranda yok |
| F14 | Buton görünürlüğü (K5, S-8) | component `SourcesPage.test.tsx`, `SourceDetailPage.test.tsx` | `givenSourceKinds_whenRendered_thenUploadVersionOnlyOnFileSources` | Listede 302 (PDF) ve 303 (CSV) satırlarında "Yeni sürüm yükle" var; 301 (Web sitesi) ve 304 (Talep geçmişi) satırlarında yok. Detay 302 başlığında var, 301'de yok. Sayfada "Yeni kaynak ekle" var (TASK-003 F29/F37'nin "buton yok" kontrolleri kaldırılır) |
| F15 | Yeni sürüm diyaloğu (K6) | component `UploadVersionDialog.test.tsx` | `givenPdfSource_whenUploadVersionWithLabel_thenDialogShowsInfoAndPostsFileAndVersion` | **Diyalog içeriği:**<br>• Başlık "Yeni sürüm yükle"<br>• "Dalga T5 modem kullanım kılavuzu · şu an v3.1 · 5 parça"<br>• "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür."<br>**Dosya seçimi:** `Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf` (2.516.582 B) → kutuda ad ve "2,4 MB"<br>**İstek:**<br>• Sürüm `' 3.2 '` → `POST .../302/versions` parçaları `file` + `version = '3.2'`<br>• İkinci koşuda sürüm boş → `version` parçası yok |
| F16 | Türü uymayan dosya (G1) | component | `givenPdfSource_whenSelectMarkdown_thenShowsKindRuleAndSendsNothing` | "Bu kaynağa sadece PDF dosyası yüklenebilir."; input `accept=".pdf"`; POST 0 |
| F17 | İş sürerken pasif (K8, S-7) | component | 2 test:<br>• `givenActiveLatestJob_whenRendered_thenUploadVersionDisabledInRowAndDetail`<br>• `givenInProgressConflict_whenUploadVersion_thenShowsInProgressMessage` | • 302 `latestJob.stage EMBEDDING` → satırdaki ve detaydaki buton `disabled`, rozet "İşleniyor"<br>• 302 `READY` iken 409 `INGESTION_IN_PROGRESS` → diyalogda "Bu kaynağın yüklemesi sürüyor. Bitince tekrar dene."; kaynak listesi yeniden istenir |
| F18 | İlerleme ve bitiş (K9-K11) | component `IngestionProgress.test.tsx` (`renderApp('/w/42/kaynaklar')`, sahte zamanlayıcı) | `givenJobAdvancingThroughStages_whenPolled_thenShowsEachStageThenToastHighlightAndStops` | **Kurulum:** 302 `latestJob` QUEUED (job 7); handler sırayla PARSING, CHUNKING, EMBEDDING 96/214 (`progress 0.458879`), WRITING, DONE (`version 3.2`, `totalChunks 214`) döner.<br>**Sorgu:** ilk istek açılıştan 1 sn sonra, sonra her 1 sn'de bir.<br>**Aşamalar:** metinler sırayla görünür; EMBEDDING'de `progressbar` `aria-valuenow="46"`.<br>**DONE sonrası:**<br>• `role="status"` içinde "Dalga T5 modem kullanım kılavuzu v3.2 hazır · 214 parça"<br>• Satır `isJustCompleted` sınıfında, 3 sn sonra sınıf kalkar<br>• Kaynak listesi yeniden istenir (yeni fixture: v3.2, 214 parça)<br>• Toplam job isteği 5 (QUEUED ilk listeden gelir); 3 sn daha ilerletince sayı artmaz |
| F19 | Başarısız iş (K12, S-5, S-6) | component | `givenJobFails_whenPolled_thenShowsFailureReasonInRowAndStopsWithoutToast` (tablo) | • `NO_TEXT_EXTRACTED` → satırda "Son yükleme başarısız: dosyada metin yok (…)"<br>• `EMBEDDING_UNAVAILABLE` → "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor"<br>• Sorgu durur, toast yok, kaynak listesi yeniden istenir |
| F20 | Yenileme / yeni sekme (K13, G5) | component | 2 test:<br>• `givenListWithActiveLatestJob_whenPageOpens_thenShowsProgressAndPollsJob`<br>• `givenListWithFinishedLatestJobs_whenPageOpens_thenNoPollingNoToastButFailureShown` | • İlk listede 302 `latestJob` EMBEDDING 96/214 → açılışta "Embed ediliyor · 96/214 parça", 1 sn sonra `GET .../ingestion-jobs/7` istenir<br>• 302 `latestJob` DONE, 303 `latestJob` FAILED `FILE_UNREADABLE` → job isteği 0, toast yok; 303 satırında "Son yükleme başarısız: PDF okunamıyor (…)" |
| F21 | Başka sayfadayken bitiş (K11, K14) | component | `givenActiveJob_whenUserGoesToSearchAndJobFinishes_thenToastOnSearchPageAndSearchWorks` | Kaynaklar'da aktif iş → "Arama" sekmesi → iş DONE → Arama sayfasında toast metni; aynı anda "TS-999 hata kodu" araması sonuç listesini gösterir |
| F22 | Diyalog klavyesi ve etiketler (K15-K17, S-9) | component | `givenDialogOpenedByButton_whenEscapeOrCancel_thenClosesAndFocusReturnsToButton` | • "Yeni kaynak ekle" ile açılınca odak "Kaynak adı"da; Esc → diyalog kapalı, odak "Yeni kaynak ekle"de<br>• "Vazgeç" için aynısı<br>• Satırdaki "Yeni sürüm yükle" için de aynısı; odak satırdaki butona döner, sayfa detay'a gitmez<br>• Diyalog alanları `getByLabelText`: "Kaynak adı", "Dosya", "Sürüm etiketi (isteğe bağlı)" |
| F23 | Erişilebilirlik (K16) | component (`vitest-axe`) | `givenUploadStates_whenRendered_thenHasNoAxeViolations` | Kaynaklar sayfası: yeni kaynak diyaloğu açık + dosya hatası görünür; satırda ilerleme çubuğu; satırda başarısızlık metni. Detay sayfası ilerlemeyle. Toast görünürken |
| F24 | Detay sayfası yeni sürüme geçer (K11) | component `SourceDetailPage.test.tsx` | `givenDetailOpenDuringJob_whenJobDone_thenInfoBoxesAndChunksShowNewVersion` | 302 detayında iş aktif (ilerleme başlık altında) → DONE → kutularda Sürüm "3.2", Parça "214", parça tablosunun ilk satırı yeni fixture'ın ilk başlığı; ilerleme kalkar |
| F25 | Bağlantı kopması (G3, D16) | component | `givenPollingNetworkError_whenBackendReturns_thenKeepsLastStageThenShowsInterrupted` | EMBEDDING 96/214 → bir sorgu `HttpResponse.error()` → satırda hâlâ "Embed ediliyor · 96/214 parça", hata mesajı yok → sonraki sorgu `FAILED` `INGESTION_INTERRUPTED` → "Son yükleme başarısız: iş yarıda kaldı (…)" |
| E1 | Yeni sürüm + yenileme (AC-2, S-1) | e2e `upload.spec.ts` | `givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady` | **Yükleme:**<br>• `/w/42/kaynaklar` → 302 satırı "Yeni sürüm yükle"<br>• `backend/samples/Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf` + sürüm `3.2` → "Yeni sürümü yükle"<br>• Diyalog kapanır; 302 satırında "İşleniyor" ve `progressbar` görünür, satırdaki buton `disabled`<br>**Yenileme:** hemen `page.reload()` → 302 satırında `progressbar` ya da aşama metni yine görünür<br>**Bitiş (zaman aşımı 120 sn):**<br>• Toast `/Dalga T5 modem kullanım kılavuzu v3\.2 hazır · \d+ parça/`<br>• Satırda "Hazır" ve "v3.2" |
| E2 | Yeni kaynak (AC-2, S-3) | e2e | `givenWorkspaceWithFreeSlot_whenAddMarkdownSource_thenReadyAndKeywordSearchFindsIt` | **Kurulum:** API'den `sourceCount < 10` olan en yüksek id'li alan (D17); ad `E2E notları <Date.now()>`, dosya `backend/samples/yardim-notlari.md`.<br>**Ekleme ve bekleme:** satır "İşleniyor" → (120 sn içinde) "Hazır".<br>**Arama:** Arama sayfasında Gelişmiş ayarlardan Kelime seçilir; dosyada geçen bir kelimeyle (tester dosyadan seçer) aranır. Bir sonucun alt satırında yeni kaynak adı görünür |
| E3 | Geçersiz dosya (AC-2, S-4) | e2e | `givenUnsupportedAndEmptyFiles_whenSelectedInNewSourceDialog_thenWarnsAndSendsNoUpload` | Bellekten `kilavuz.docx` (buffer) → tür uyarısı; boş `bos.txt` → boş uyarısı; "Kaynağı ekle" tıklanır; sayfa boyunca `POST /api/workspaces/*/sources` isteği 0 |
| E4 | Klavye (S-9, K15, K17) | e2e | `givenKeyboardOnly_whenOpenDialogTabAroundAndEscape_thenFocusStaysInsideAndReturns` | Tab ile "Yeni kaynak ekle"ye gelinir, Enter → odak "Kaynak adı"da; 10 kez Tab → her adımda `document.activeElement` diyalog içinde; Escape → diyalog görünmez, odak "Yeni kaynak ekle"de |
| E5 | CSP (K19, AC-7) | e2e | `givenSite_whenOpenSearchSourcesAndDetail_thenNoCspViolationInConsole` | `/w/42/arama`, `/w/42/kaynaklar`, `/w/42/kaynaklar/302` açılır; `page.on('console')` + `pageerror` içinde "Content Security Policy" ya da "violates" geçen mesaj 0. Anlamlı sonuç 3000'e (nginx CSP) karşı; 5173'te de geçer |

TASK-003 E2E'leri (E1, E3, E4, E5) değişmeden geçmeli. Yükleme, arama sonuçlarını değiştirmez: E1 Yardım merkezini, E3 Kıyı Net'i kullanır.

### qa canlı kontrolleri (gerçek Ollama + Docker, tarayıcıdan)
qa kullanıcının Ollama servisini durdurmaz. Ollama kapalı durumu TASK-003'teki gibi: backend container'ı `OLLAMA_BASE_URL=http://127.0.0.1:9` ile ayrıca başlatılır, sonra eski hâline döndürülür. Ekran görüntüleri raporda.
- **Q0 (AC-3):**
  - `docker compose -f deployment/docker-compose.yml down -v` sonra `up --build -d` → 3 servis `healthy`.
  - Temiz veritabanı gerekli: 302 3.1'den başlamalı.
- **Q-CSP (K19):** `http://localhost:3000` açılışında ve kaynak sayfalarında konsolda CSP hatası yok; `curl -sI` ile CSP başlığının TASK-003'teki gibi aynı kaldığı.
- **Q1 (S-1):** 302'ye 3.2 yükleme; aşamalar sırayla, yenileme sonrası devam, Hazır + toast + vurgu. Detay sayfasında sürüm 3.2 ve yeni parça sayısı.
- **Q2 (S-2):** İş sürerken aynı kılavuzun başlığıyla Kelime araması → sonuçların parça id'leri eski; iş bitince aynı arama → yeni id'ler.
- **Q3 (S-3):** `backend/samples/yardim-notlari.md` ile yeni kaynak → İşleniyor → Hazır → içindeki bir kelime aranınca bulunur.
- **Q4 (S-4):** `.docx`, 25 MB PDF, boş `.txt` → uyarı, Network'te istek yok.
- **Q5 (S-5):** Ollama kapalıyken dosya yükleme → "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor". Eski sürümün parçası Kelime aramasında bulunur.
- **Q6 (S-6):** Sadece görsel içeren PDF → "dosyada metin yok". qa dosyayı scratchpad'de üretir (ör. tek bir görselden PDF).
- **Q7 (S-7):** İş sürerken butonun pasif olduğu.
- **Q8 (S-8):** Web sitesi ve Talep geçmişi satırlarında ve detayında buton yok.
- **Q9 (S-9):** Sadece klavyeyle iki diyalog: açma, Tab, Esc, odak dönüşü, gönderme.
- **Sözleşme uyumu:**
  - `/v3/api-docs.yaml` ↔ `docs/api/openapi.yaml` (0.4.0, `latestJob`);
  - `model/types.ts` ↔ şemalar;
  - `errorMessages.ts` ↔ 21 HTTP + 7 iş kodu.
- **Komutlar:** `npm run build`, `npm run lint`, `npm test`, `npm run test:coverage` (%80), `E2E_BASE_URL=http://localhost:3000 npm run test:e2e`, `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify`.

## Deployment planı
Yok. Docker, nginx ve compose dosyaları değişmez. qa sistemi `--build` ile yeniden kurar; yeni Vite build'i (D9) imaja böyle girer.

## Açık sorular ve riskler
- **Açık soru yok.** Belirsiz noktalar "Kararlar"da ve onay kartında "Benim verdiğim kararlar" olarak yazıldı.
- **E1'de yenileme anı:** Yenilemenin iş bitmeden yapılması gerekir. 214 parçalık PDF'in gerçek Ollama ile embed edilmesi birkaç saniyeden uzun sürüyor (TASK-002/003 gözlemi), test yeniden yüklemeyi diyalog kapanır kapanmaz yapar. Makine çok hızlıysa test kırılgan olabilir; tester süreyi raporlar.
- **E2E veri değiştirir (D17):** Her koşu bir kaynak ekler ve 302'ye yeni sürüm yükler. qa'nın senaryoları `down -v` sonrası temiz veritabanında, E2E'den önce yapılır.
- **jsdom ve `<dialog>`:** jsdom `showModal` desteklemiyorsa testte basit taklit kullanılır. Odak tuzağı ve Esc gerçek tarayıcıda E4 ile doğrulanır.
- **nginx 413:** İstemci kontrolü atlatılırsa 21 MB'tan büyük istek nginx'te HTML 413 ile reddedilir; ekranda genel hata mesajı çıkar. İstemci 20 MB'tan büyüğünü zaten göndermez; kabul edildi.
- **Kapsam dışı not:** `frontend-conventions` §4'te `ingestion` feature'ı önerilir. Bu task'ta D2 nedeniyle açılmadı; skill metnini kullanıcı ya da lead günceller.

STATUS: DONE
