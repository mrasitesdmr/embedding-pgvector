TASK: TASK-006
# Frontend uygulama raporu

## Kullanılan skill'ler
`frontend-conventions`, `react-patterns`, `frontend-a11y`, `react-state-management`, `frontend-design` (planın "Frontend planı"ndaki liste).

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| `shared/workspace/workspaceRules.ts` (K5, K6, K8) | `src/shared/workspace/workspaceRules.ts` | + `UPLOAD_SECTION = 'belge-yukle'`, `UPLOAD_SOURCE_PARAM = 'kaynak'`, `workspaceHomePath`, `uploadPath(workspaceId, sourceId?)`, `homeSection(workspace)`, `shouldShowWorkspaceSelect(totalElements)` (`>= 2`). `DEFAULT_WORKSPACE_ID = 42` aynı kaldı |
| `EmptyState` children | `src/shared/components/EmptyState.tsx`, `.module.css` | Opsiyonel `children`, açıklamanın altında `.actions` kutusunda |
| D8, K8: açılış yönlendirmesi | `src/app/App.tsx`, `src/app/WorkspaceHomeRedirect.tsx` (yeni) | `/` → `/w/42`; index → `WorkspaceHomeRedirect` → `sourceCount === 0` ise `belge-yukle`, değilse `arama` (`replace`). `belge-yukle` route'u eklendi. "Sayfa bulunamadı" bağlantısı `workspaceHomePath` |
| D8: logo ve "bulunamadı" bağlantıları | `src/app/AppHeader.tsx`, `src/app/WorkspaceLayout.tsx`, `src/app/NotFoundPage.tsx` | Hepsi `workspaceHomePath(...)` → `/w/42` |
| K6: seçici | `src/app/WorkspaceSelect.tsx` | `data` yoksa (yüklenirken/hatada) ya da `totalElements < 2` ise `null`; artık hiç "Yükleniyor…" seçeneği/`disabled` durumu yok (ölü dal kaldırıldı). `targetSection` `belge-yukle`'yi tanır |
| K9: menü | `src/app/SectionTabs.tsx` | Sıra: Arama · Arama kayıtları · Bilgi kaynakları · **Belge yükle** (`NavLink end`, `UploadIcon` `aria-hidden`) |
| D9, K10, G3: ad türetme | `src/features/source/model/uploadRules.ts` | + `sourceNameFromFileName`, `resolveSourceName`, `SOURCE_NAME_HINT`, `UPLOAD_LIMITS_TEXT` |
| `CreateSourceForm` ayrı dosya (K10, K13, G2) | `src/features/source/components/CreateSourceForm.tsx` (yeni), `uploadDialog.module.css` (+ `.hint`) | Plandaki props. Ad alanı `required` değil; altında ipucu (`aria-describedby` = ipucu id'si, hata varsa ipucu + hata id'si). Placeholder "ör. Süreç el kitabı". Gönderim: önce dosya kontrolü; dosya geçerliyse `resolveSourceName` → ad boşsa türetilen ad kutuya yazılır → `validateSourceName`. Hata gösterimi `toUploadFieldErrors` (değişmedi). `onCancel` yoksa "Vazgeç" yok |
| `CreateSourceDialog` | `src/features/source/components/CreateSourceDialog.tsx` | `CreateSourceForm`'u kullanır (`submitLabel="Kaynağı ekle"`, `pendingLabel="Yükleniyor…"`, `onCancel=onClose`, `onCreated={() => onClose()}`). Açıklama paragrafı diyalogda kaldı. Başlık "Yeni kaynak ekle" |
| K10, K11, D10: yükleme sayfası | `src/features/source/pages/UploadPage.tsx` (yeni), `UploadPage.module.css` (yeni) | `<title>Belge yükle · yanıt.</title>`, h1, açıklama. `?kaynak=` `parsePositiveId` ile okunur: geçerliyse `UploadStatusPanel` (key = sourceId), değilse `UPLOAD_LIMITS_TEXT` + form (`Belgeyi yükle` / `Yükleniyor…`); 202 sonrası `navigate(uploadPath(42, job.sourceId), { replace: true })` |
| K11, K12, G4, G5: durum paneli | `src/features/source/components/UploadStatusPanel.tsx` (yeni), `.module.css` (yeni) | `useSourceQuery` + `useSourceIngestion`. Kapsayıcı `aria-live="polite"`. Durumlar plandaki gibi: yükleniyor (`aria-busy`), hata (`ErrorAlert` + "Yeni belge yükle"), sürüyor (h2 kaynak adı, `IngestionProgress`, not), hazır (h2 "Belge hazır", `ingestionDoneMessage` ya da `${ad} hazır · ${parça} parça`, "Soru sor", "Başka bir belge yükle"), başarısız (h2 "Belge yüklenemedi", kaynak adı, `IngestionFailure`, "Yeniden dene" → `UploadVersionDialog` aynı kaynak, "Başka bir belge yükle"). `UploadVersionDialog` live region'ın dışında render edilir (diyalog içeriği duyurulmasın diye) |
| K15: types / api / query | `src/features/search/model/types.ts`, `api/searchApi.ts`, `api/searchQueries.ts` | + `PopularQueryResponse`, `PopularQueriesResponse`; `listPopularQueries` → `GET /api/workspaces/{id}/search-events/popular-queries`; `searchKeys.popularQueries(id)` = `['search', id, 'popular-queries']` (`searchKeys.all` altında, K19 mevcut invalidate ile karşılanır); `usePopularQueriesQuery` |
| K15, K20: `PopularQueries` | `src/features/search/components/PopularQueries.tsx`, `PopularQueries.module.css` (eski `SearchSuggestions.*` silindi/yeniden adlandırıldı) | Props `queries`, `isDisabled`, `onSelect`. "Sık aranan:" etiketi, her öğe buton, key `query`, backend sırası |
| `SUGGESTED_QUERIES` silindi | `src/features/search/model/searchRules.ts` | |
| K14, K18, D12, D13: arama sayfası | `src/features/search/pages/SearchPage.tsx`, `.module.css` (+ `.link`) | Başlık + açıklama her zaman. `sourceCount === 0` → `EmptyState` "Henüz belge yüklenmedi" + `Link` "Belge yükle"; form, ayarlar, "Sık aranan" render edilmez ve popular-queries isteği atılmaz (form ve sorgu dosya-içi `SearchWorkspace` component'inde). Aksi hâlde `usePopularQueriesQuery`; liste boş değilse `PopularQueries`; yüklenirken/hatada gösterilmez, hata `useEffect` içinde `console.error`. Boş sonuç durumu "Belgene bir soru sorarak başla" + plandaki iki açıklama. Açıklama D13 metni |
| K2: demo metni | `src/features/search/components/SearchForm.tsx` | Placeholder "Örn. modemde kırmızı ışık yanıyor" → "Örn. Yıllık izin kaç gün?" (grep'te çıkan demo metni) |
| D11 | `playwright.config.ts` | + `globalSetup: './e2e/global-setup.ts'` (dosyayı tester yazar) |
| `frontend/CLAUDE.md` | `frontend/CLAUDE.md` | Adresler (`belge-yukle?kaynak=`), açılış davranışı, seçici kuralı, "Sık aranan"ın kaynağı, Playwright `globalSetup`, E2E'nin veriyi sildiği notu ve local komutu (`E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npm run test:e2e`) |

## Değişen dosyalar
**Yeni:**
- `src/app/WorkspaceHomeRedirect.tsx`
- `src/features/source/components/CreateSourceForm.tsx`
- `src/features/source/components/UploadStatusPanel.tsx`, `UploadStatusPanel.module.css`
- `src/features/source/pages/UploadPage.tsx`, `UploadPage.module.css`
- `src/features/search/components/PopularQueries.tsx`, `PopularQueries.module.css` (eski `SearchSuggestions.module.css`'ten taşındı, kök class `.popularQueries`)

**Değişen:**
- `src/app/App.tsx`, `AppHeader.tsx`, `NotFoundPage.tsx`, `SectionTabs.tsx`, `WorkspaceLayout.tsx`, `WorkspaceSelect.tsx`
- `src/shared/workspace/workspaceRules.ts`, `src/shared/components/EmptyState.tsx`, `EmptyState.module.css`
- `src/features/source/components/CreateSourceDialog.tsx`, `uploadDialog.module.css`, `src/features/source/model/uploadRules.ts`
- `src/features/search/model/searchRules.ts`, `model/types.ts`, `api/searchApi.ts`, `api/searchQueries.ts`, `components/SearchForm.tsx`, `pages/SearchPage.tsx`, `pages/SearchPage.module.css`
- `playwright.config.ts`, `CLAUDE.md`

**Silinen:** `src/features/search/components/SearchSuggestions.tsx`, `SearchSuggestions.module.css`.

**Paket:** eklenen ya da kaldırılan yok.

## Ekran metinleri
**Menü ("Bölümler" navigation):** "Arama", "Arama kayıtları", "Bilgi kaynakları", "Belge yükle" (`href /w/42/belge-yukle`).

**Yükleme sayfası (`/w/42/belge-yukle`):**
- `<title>`: "Belge yükle · yanıt."; h1 "Belge yükle"; açıklama "Belgeni yükle; parçalara bölünür ve embedding'e çevrilir. Bitince belgene soru sorabilirsin."
- Form: "PDF, TXT, MD, MARKDOWN ya da CSV · en fazla 20 MB"; label "Kaynak adı" (placeholder "ör. Süreç el kitabı", ipucu "Boş bırakırsan dosya adı kullanılır."); label "Dosya"; buton "Belgeyi yükle" / gönderilirken "Yükleniyor…" (Vazgeç yok). Bölge `aria-label="Belge yükleme"`.
- Alan hataları (değişmedi): "Kaynak adı 2-80 karakter olmalı.", "Bu ad çalışma alanında başka bir kaynakta kullanılıyor.", "Yüklenecek dosyayı seç.", "Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.", "Dosya boş. İçinde metin olan bir dosya seç.", "Dosya 20 MB'tan büyük olamaz."; form üstü `alert`'te `errorMessages.ts` metinleri.
- Durum paneli (`?kaynak=<id>`):
  - yükleniyor: "Belgenin durumu yükleniyor…"
  - hata: `alert` `messageForError` (404 → "Kaynak bulunamadı.") + bağlantı "Yeni belge yükle" (`/w/42/belge-yukle`)
  - sürüyor: h2 = kaynak adı, `progressbar` (`aria-label` "<ad> yükleme ilerlemesi", aşama metni ör. "Embed ediliyor · 24/48 parça"), "Bu sayfadan ayrılabilirsin; yükleme arka planda sürer."
  - hazır: h2 "Belge hazır", "<ad> hazır · <n> parça" (sürüm varsa "<ad> v<sürüm> hazır · …"), bağlantılar "Soru sor" (`/w/42/arama`) ve "Başka bir belge yükle" (`/w/42/belge-yukle`)
  - başarısız: h2 "Belge yüklenemedi", kaynak adı, "Son yükleme başarısız: <neden>", buton "Yeniden dene" (diyalog "Yeni sürüm yükle", buton "Yeni sürümü yükle"), bağlantı "Başka bir belge yükle"

**"Yeni kaynak ekle" diyaloğu:** başlık "Yeni kaynak ekle", buton "Kaynağı ekle" / "Yükleniyor…", "Vazgeç"; ad alanı artık zorunlu değil, aynı ipucu ve placeholder.

**Arama sayfası (`/w/42/arama`):**
- h1 "Bilgi bankası"; açıklama "Sorunu kendi kelimelerinle yaz; Belgelerim çalışma alanındaki belgelerde aranır."
- Kaynak yok: "Henüz belge yüklenmedi", "Soru sorabilmek için önce bir belge yükle.", bağlantı "Belge yükle" (`/w/42/belge-yukle`).
- Kaynak var: arama kutusu label "Arama metni" (placeholder "Örn. Yıllık izin kaç gün?"), "Sık aranan:" + liste (sadece liste doluysa).
- Sonuç yokken: "Belgene bir soru sorarak başla" + liste doluysa "Sık arananlardan birini seç ya da kendi sorunu yaz.", boşsa "Sorunu kendi kelimelerinle yaz; en ilgili parçalar listelenir."

**Seçici:** `combobox` "Çalışma alanı" sadece `totalElements >= 2` iken.

## Plandan sapmalar
Yok. İki küçük uygulama ayrıntısı (plan bunları açıkça belirtmemişti, kimseyle konuşmayı gerektirmedi):
- `UPLOAD_LIMITS_TEXT` sadece form durumunda gösterilir; durum panelinde gösterilmez (sınır bilgisi sadece dosya seçerken anlamlı).
- K2 grep'i `SearchForm` placeholder'ındaki "modem" metnini yakaladı; plan "frontend/src → 0" dediği için "Örn. Yıllık izin kaç gün?" yapıldı.

## Doğrulama
- `npm run lint` → **geçti** (oxlint, exit 0, uyarı yok).
- `npm run build` → **kaldı; sebep sadece tester'ın test dosyası**: `src/features/search/pages/SearchPage.test.tsx(9,10): error TS2305: Module '"../model/searchRules.ts"' has no exported member 'SUGGESTED_QUERIES'.` Plan `SUGGESTED_QUERIES`'i siliyor ve bu testin yeniden yazılması tester'ın kapsamında (F10-F14). Test dosyasına dokunmadım.
  - Uygulama kodunun tek başına temiz olduğunu göstermek için: testler hariç tip kontrolü (`tsconfig.app.json`'u extend eden, `*.test.ts(x)` ve `src/test` hariç tutan geçici config, scratchpad'de) `npx tsc -p <geçici> --typeRoots …` → **exit 0**; `npx vite build` → **geçti** ("built in 117ms").
- `npm test` (`vitest run`) → 26 dosya, 361 test: **348 geçti, 13 kaldı** (4 dosya). Kırılanların hepsi plandaki davranış değişikliğinden:
  - `SearchPage.test.tsx` (5), `App.test.tsx` (3), `a11y.test.tsx` (1): MSW'de `GET …/search-events/popular-queries` handler'ı yok (unhandled request), "Henüz arama yapılmadı" / "Dalga Fiber bilgi kaynaklarında aranır" / "Şifremi unuttum…" önerisi / logo `href /w/42/arama` beklentileri eski davranış.
  - `CreateSourceDialog.test.tsx` (4): ad artık zorunlu değil (boş ad → yalnız "Yüklenecek dosyayı seç."; boşluklu ad + dosya → dosya adından türetilir), ad alanının `aria-describedby`'ı artık `"<ipucu-id> <hata-id>"`.
- Demo metni taraması (testler hariç): `grep -rniE 'poyraz|dalga|yakamoz|kıyı ?net|şifremi unuttum|ts-999|modem|fatura sss|yardım merkezi|taşınıyorum' frontend/src frontend/CLAUDE.md` → **0**; 37 seed çalışma alanı adıyla (`grep -rniF -f <ad listesi>`) `frontend/src`, `frontend/CLAUDE.md`, `frontend/index.html` → **0**.
- Tarayıcı kontrolü (Playwright, Vite dev server 5174 → backend-coder'ın açık dev backend'i, temiz kurulum; sadece GET, veri yazılmadı): `/` → `/w/42/belge-yukle`; `combobox` "Çalışma alanı" sayısı 0; `/w/42/arama` "Henüz belge yüklenmedi" + "Belge yükle"; `?kaynak=999999` → `alert` "Kaynak bulunamadı." + "Yeni belge yükle"; 360 px'te boş gönderim → "Yüklenecek dosyayı seç.", sayfa taşmıyor. Konsoldaki tek hata 999999 için beklenen 404. Dev server kapatıldı.

## Tester için notlar
- **MSW handler'ları:** her arama sayfası testine `GET /api/workspaces/:workspaceId/search-events/popular-queries` → `{ queries: [...] }` gerekli (kaynak varken sayfa açılınca istek atılır; `sourceCount === 0` iken atılmaz, F10). Workspace fixture'ında `sourceCount` K8/K14 için belirleyici. `GET /api/workspaces` yanıtındaki `totalElements` seçiciyi belirler (F8).
- **Yükleme akışı (F2-F5):** `POST /api/workspaces/42/sources` (202 `IngestionJobResponse`) → sayfa `?kaynak=<sourceId>`'ye `replace` ile gider → `GET …/sources/{id}` (panel) + `GET …/sources` (izleyici `IngestionWatcherProvider`, aktif `latestJob`'ları 1 sn'de bir `GET …/ingestion-jobs/{jobId}` ile sorgular). Panel ilerlemeyi detay yanıtındaki `latestJob` ve iş cache'inden okur; iş `DONE` olunca izleyici kaynak verisini invalidate eder ve detay yeniden çekilir. Yani "Belge hazır" için iş handler'ının `DONE` dönmesi ve ardından detay handler'ının `status: READY` dönmesi gerekir (hazır satırı `latestJob.stage === 'DONE'` ise `ingestionDoneMessage`, değilse `chunkCount`'tan).
- Mutation `onSuccess` `workspaceKeys.all`'ı da invalidate eder; yönlendirme testlerinde `sourceCount` değişimi buradan gelir.
- Ad türetme: dosya geçersizse ad türetilmez ve ad kutusuna bir şey yazılmaz; dosya geçerli ve ad boşsa türetilen ad kutuya yazılır, sonra 2-80 kuralı uygulanır (`a.pdf` → kutuda "a", ad altında kural mesajı, POST 0).
- "Yeniden dene" diyaloğu (`UploadVersionDialog`) sadece başarısız durumda mount edilir; `POST …/sources/{id}/versions`.
- "Sık aranan" hatasında `console.error` çağrılır (D12); testte `console.error` spy'ı gerekebilir.
- `UploadStatusPanel` kapsayıcısı `aria-live="polite"`; kaynak yüklenene kadar ayrı, yüklenince yeni bir live kapsayıcı render edilir.
- Mevcut kırılan 13 test yukarıda "Doğrulama"da listelendi; `npm run build` `SearchPage.test.tsx`'teki `SUGGESTED_QUERIES` import'u kalkınca geçer.

## Düzeltme
—

STATUS: DONE
