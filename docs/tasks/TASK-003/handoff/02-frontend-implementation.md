TASK: TASK-003
# Frontend uygulama raporu

## Kullanılan skill'ler
`frontend-conventions`, `react-patterns`, `react-state-management`, `frontend-a11y`, `frontend-design`, `vite-patterns`, `frontend-security`, `react-best-practices`, `react-composition-patterns`.

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| İskelet, paketler (S2 = A) | `package.json`, `package-lock.json`, `vite.config.ts`, `playwright.config.ts`, `tsconfig*.json`, `.oxlintrc.json`, `.gitignore`, `index.html`, `public/favicon.svg` | `npm create vite@latest` (react-ts) geçici klasörde üretildi, `CLAUDE.md` korunarak taşındı. `App.css`, `assets/*`, `icons.svg`, sayaç örneği alınmadı. Şablon zaten oxlint ile geliyor; ESLint yok. Script'ler: `dev`, `build`, `lint`, `preview`, `test`, `test:watch`, `test:coverage`, `test:e2e`. `engines.node` `>=26 <27` (D26: makinede `node v26.12.0`). |
| K1, K4, D11, D12 | `app/AppHeader.tsx`, `app/WorkspaceSelect.tsx`, `app/SectionTabs.tsx`, `index.css` | Logo "yanıt." (Link → `/w/:id/arama`), native `<select>` (görsel olarak gizli etiket "Çalışma alanı"), `<nav aria-label="Bölümler">` içinde `NavLink`'ler. Kullanıcı adı yok. |
| K2 | — | Tarayıcı çerçevesi, Sistem konsolu, senaryolar taşınmadı. |
| K3, D10 | `app/App.tsx`, `app/AppProviders.tsx` | `BrowserRouter` + `Routes`. `/` → `/w/42/arama`; `/w/:id` → `arama`; bilinmeyen adres → "Sayfa bulunamadı". |
| K5 | Bütün UI metinleri | "RAG" ve "lab" geçmiyor; ürün adı "yanıt.", başlık "Bilgi bankası". |
| K6, D14, AC-6 | `shared/api/httpClient.ts`, `ApiError.ts`, `errorMessages.ts`, `shared/components/ErrorAlert.tsx` | Sözleşmenin 21 kodu + `NETWORK_ERROR` + fallback, plandaki tabloyla birebir. `detail` hiçbir yerde gösterilmiyor. |
| K7, D18, G5 | `features/search/model/searchHitText.ts`, `components/HighlightedText.tsx` | Vurgu segment dizisiyle `<mark>`; `dangerouslySetInnerHTML`/`innerHTML` yok (grep boş). |
| K8, K9, K10, G3, D6, D9 | `app/WorkspaceLayout.tsx`, `shared/workspace/*`, `features/search/components/LastSearchProvider.tsx` | `parseWorkspaceId` null ise istek atılmadan "Çalışma alanı bulunamadı"; 404 `WORKSPACE_NOT_FOUND` aynı ekran; `LastSearchProvider key={workspaceId}`. |
| K11-K17, D8, D15, D16 | `features/search/pages/SearchPage.tsx`, `components/SearchForm.tsx`, `SearchSuggestions.tsx`, `SearchInfoLine.tsx`, `SearchSettingsPanel.tsx`, `hooks/useSearchSettings.ts`, `model/searchRules.ts`, `model/searchSettings.ts` | Ayarlar `emb:search-settings:v1`; sadece geçerli alanlar yazılıyor; panel her açılışta kapalı. Geçersiz alanla aramada istek atılmıyor ve panel açılıyor. |
| K13 | `LastSearchProvider.tsx`, `SearchPendingResults.tsx` | Mutation `isPending` + ref koruması: aynı anda ikinci istek gitmiyor. |
| K18-K21, D22 | `SearchResults.tsx`, `SearchResultHeader.tsx`, `SearchResultEmpty.tsx`, `SearchHitList.tsx`, `SearchHitItem.tsx`, `SearchTechnicalDetails.tsx`, `model/searchNotices.ts` | |
| K22, K38, D4, G1 | `features/source/pages/SourceDetailPage.tsx`, `components/HighlightedChunkNotice.tsx`, `ChunkTable.tsx`, `api/sourceQueries.ts` (`useChunkQuery`) | Vurgulu parça tablonun ilk satırı (listede tekrar etmez). `CHUNK_NOT_FOUND` → `messageFor`, liste yine görünür. |
| K23 | `SEARCH_CHANNEL = 'Temsilci paneli'` | |
| K24 | `errorMessages.ts` | |
| K25-K27, D21, G4 | `pages/SearchEventsPage.tsx`, `components/SearchEventStats.tsx`, `SearchEventTable.tsx`, `ResultCountBadge.tsx`, `model/resultCountTone.ts`, `shared/components/Pagination.tsx`, `app/SectionTabs.tsx` | Mutation `onSuccess` → `searchKeys.all(workspaceId)` invalidate (sekme sayısı + tablo + göstergeler). |
| K28, K29, D19, D20, G2 | `features/source/pages/SourcesPage.tsx`, `SourceTable.tsx`, `SourceRow.tsx`, `SourceStatusBadge.tsx`, `model/sourceRules.ts` | Yükleme butonu, ilerleme ve polling yok. "İşleniyor" rozetindeki dönen ikon `prefers-reduced-motion`'da duruyor. |
| K35-K37, D5 | `SourceDetailPage.tsx`, `SourceInfoBoxes.tsx`, `ChunkTable.tsx`, `useChunksInfiniteQuery` | Embedding modeli = ilk parçanın `embeddingModel`'i. "Daha fazla göster" son sayfada yok. |
| K41 (frontend kısmı) | `vite.config.ts` | `server.proxy['/api'] → http://localhost:8080`. |
| K43 | Native `<details>`, `<select>`, `<a>`, radio/checkbox `<label>`'ları | Bütün form alanlarının görünür ya da görsel olarak gizli etiketi var. |
| D17 | `main.tsx` | Source Sans 3 (400, 500, 600, 700), JetBrains Mono (400, 500). |
| D21 | `shared/format/numberFormat.ts`, `dateFormat.ts` | Saat dilimi `Europe/Istanbul`. |
| D24 | `app/queryClient.ts` | 4xx `ApiError`'da retry yok, diğerlerinde 2; `staleTime` 30 sn; `refetchOnWindowFocus: false`. |
| Görsel dil | `index.css` + her component'in `.module.css`'i | `lab.html`'deki `--s-*` değerleri `--color-*` token'larına taşındı; tablolar, rozetler, callout, kpi kutuları, sonuç kartları, iskelet ve etiketler lab'daki gibi. Tam pencere; ≥1024 px'te yatay taşma yok. |
| `frontend/CLAUDE.md` | `frontend/CLAUDE.md` | "Hedef" ifadeleri kaldırıldı; gerçek sürümler, "henüz olmayanlar" listesi, klasörler, adresler, komutlar (test, tek test, coverage, e2e, `npx playwright install chromium`) ve lint tuzakları yazıldı. |

## Değişen dosyalar
**Yeni (hepsi `frontend/` altında):**
- Config: `package.json`, `package-lock.json`, `vite.config.ts`, `playwright.config.ts`, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json` (`playwright.config.ts` eklendi), `.oxlintrc.json`, `.gitignore`, `index.html`, `public/favicon.svg`.
- `src/main.tsx`, `src/index.css`.
- `src/app/`: `App.tsx`, `AppProviders.tsx` (+css), `queryClient.ts`, `WorkspaceLayout.tsx` (+css), `AppHeader.tsx` (+css), `WorkspaceSelect.tsx` (+css), `SectionTabs.tsx` (+css), `NotFoundPage.tsx`.
- `src/shared/api/`: `ApiError.ts`, `httpClient.ts`, `errorMessages.ts`, `pagination.ts`.
- `src/shared/workspace/`: `types.ts`, `workspaceApi.ts`, `workspaceQueries.ts`, `workspaceRules.ts`, `useCurrentWorkspace.ts`.
- `src/shared/format/`: `numberFormat.ts`, `dateFormat.ts`.
- `src/shared/components/`: `ErrorAlert`, `StatusBadge`, `EmptyState`, `NotFoundState`, `Pagination`, `Breadcrumbs`, `Callout` (+ `.module.css`), `table.module.css`, `visuallyHidden.module.css`.
- `src/features/search/`: `api/searchApi.ts`, `api/searchQueries.ts`; `hooks/useLastSearch.ts`, `hooks/useSearchSettings.ts`; `model/types.ts`, `searchRules.ts`, `searchSettings.ts`, `searchHitText.ts`, `searchNotices.ts`, `resultCountTone.ts`; `components/` `LastSearchProvider`, `SearchForm`, `SearchSuggestions`, `SearchInfoLine`, `SearchSettingsPanel`, `SearchPendingResults`, `SearchResults`, `SearchResultHeader`, `SearchResultEmpty`, `SearchHitList`, `SearchHitItem`, `HighlightedText`, `SearchTechnicalDetails`, `SearchEventStats`, `SearchEventTable`, `ResultCountBadge` (+css); `pages/SearchPage.tsx`, `pages/SearchEventsPage.tsx` (+css).
- `src/features/source/`: `api/sourceApi.ts`, `api/sourceQueries.ts`; `model/types.ts`, `model/sourceRules.ts`; `components/` `SourceTable`, `SourceRow`, `SourceStatusBadge`, `SourceInfoBoxes`, `ChunkTable`, `HighlightedChunkNotice` (+css); `pages/SourcesPage.tsx`, `pages/SourceDetailPage.tsx` (+css).

**Değişen:** `frontend/CLAUDE.md`.
**Silinen:** yok (şablonun `App.css`, `App.tsx` örneği, `assets/`, `public/icons.svg` hiç taşınmadı).

**Eklenen paketler (kurulumdaki son kararlı sürümler; hepsi planda var):**
- Runtime: `react-router` ^7.18.4, `@tanstack/react-query` ^5.104.5, `react-error-boundary` ^6.1.6, `@fontsource/source-sans-3` ^5.3.1, `@fontsource/jetbrains-mono` ^5.3.0. Şablondan: `react` / `react-dom` ^19.3.1.
- Dev: `vitest` ^5.0.5, `@vitest/coverage-v8` ^5.0.5, `jsdom` ^30.1.3, `@testing-library/react` ^16.3.3, `@testing-library/dom` ^10.4.2, `@testing-library/user-event` ^14.6.8, `@testing-library/jest-dom` ^7.0.1, `msw` ^3.0.3, `vitest-axe` ^0.1.0 (son kararlı; 1.0 sadece pre-release), `@playwright/test` ^1.64.1. Şablondan: `typescript` ~6.0.3, `vite` ^8.3.4 (kurulu 8.3.5), `@vitejs/plugin-react` ^6.1.2, `oxlint` ^1.88.0 (kurulu 1.89.0), `@types/*`.
- `npm audit`: 0 açık. Tek install script'i `fsevents` (opsiyonel, macOS); onaylanmadı, gerekmiyor.

## Ekran metinleri
**Başlık ve sekmeler**
- Logo bağlantısı: erişilebilir adı "yanıt." → `/w/:id/arama`.
- Seçici: etiket "Çalışma alanı" (görsel olarak gizli). Seçenek: `"Dalga Fiber · 4 kaynak · 42 parça"`. Liste yüklenirken "Yükleniyor…", geçerli alan listede yoksa "Çalışma alanı seç" (disabled).
- `<nav aria-label="Bölümler">` bağlantıları: "Arama", "Arama kayıtları" + sayı rozeti, "Bilgi kaynakları". Rozetten önce bir boşluk var, yani bağlantının erişilebilir adı `"Arama kayıtları 0"`; testte `{ name: /^Arama kayıtları/ }` kullanın. Rozet `stats` yüklenmeden görünmez. Aktif sekmede `aria-current="page"` var (React Router `NavLink`). "Bilgi kaynakları" detay sayfasında da aktif.

**Arama sayfası** (`<title>Bilgi bankası · yanıt.</title>`)
- h1 "Bilgi bankası"; açıklama `Müşterinin sorusunu kendi kelimeleriyle yaz; Dalga Fiber bilgi kaynaklarında aranır.`
- Form `role="search"`. Input etiketi "Arama metni" (gizli), placeholder "Örn. modemde kırmızı ışık yanıyor", `maxLength=500`. Buton "Ara"; arama sürerken "Aranıyor…" ve disabled.
- Alan hatası (`VALIDATION_FAILED` + `violations[].field='query'`): input altında `role="alert"` ile "Arama metni 1-500 karakter olmalı."
- "Sık aranan:" + 3 buton (TASK metinleri); arama sürerken disabled.
- Bilgi satırı (`<ul aria-label="Arama bilgisi">`): `Arama: <b>Hybrid (kelime + anlam)</b>`, `Sonuç: <b>en fazla 10</b>`, `Embedding: <b>bge-m3@1024</b>`.
- `<details>` özeti "Gelişmiş ayarlar". İçinde:
  - fieldset "Arama yöntemi": radyolar "Kelime", "Anlam", "Hybrid";
  - fieldset "Filtre stratejisi": "post-filter", "iterative scan" (Kelime'de fieldset disabled);
  - sayı alanı "ef_search" (Kelime'de disabled), sayı alanı "Sonuç sayısı";
  - onay kutusu "Sorgu planını getir" (Kelime'de disabled);
  - buton "Varsayılana dön".
  - Hata metinleri alanın altında `aria-invalid` + `aria-describedby` ile (role alert değil): "ef_search 10 ile 1000 arasında bir tam sayı olmalı." ve "Sonuç sayısı 1 ile 50 arasında bir tam sayı olmalı." ef_search hatası Kelime yönteminde gösterilmez ve aramayı engellemez.
- Boş durum: "Henüz arama yapılmadı" / "Sık arananlardan birini seç ya da kendi aramanı yaz."
- Arama sürerken: `<section aria-label="Sonuçlar" aria-busy="true">` içinde h2 `“q”` ve "aranıyor…" ile 3 iskelet satır (`aria-hidden`).
- Sonuç başlığı: h2 `“TS-999 hata kodu”`, `"2 sonuç"`, `"toplam 57 ms"` (ayrı öğeler, `aria-live="polite"`).
- Kısa kaldı uyarısı (callout):
  - POST_FILTER: `Anlam araması istenen 10 sonuçtan sadece 4 tanesini buldu (kısa kaldı). Gelişmiş ayarlarda filtre stratejisini iterative scan yapıp tekrar dene.`
  - Diğer: `Anlam araması istenen 10 sonuçtan sadece 4 tanesini buldu. Bu çalışma alanında yeterli parça olmayabilir.`
- Sonuç listesi `<ol aria-label="Arama sonuçları">`. Her sonuç tek bir bağlantı (`href="/w/42/kaynaklar/303?parca=1380"`). İçinde:
  - sıra numarası ve başlık (yoksa "Başlıksız parça");
  - etiketler "anlam" ve "kelime" (bu sırayla);
  - snippet (eşleşmeler `<mark>`);
  - alt satır `Modem hata kodları tablosu · parça 31 · #1380`.
- Sonuç yok: `“…” için sonuç bulunamadı` + "Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene." ya da "Farklı kelimelerle aramayı dene."
- `<details>` özeti "Teknik detay". İçinde:
  - Tanım listesi: "Yöntem", "Filtre stratejisi", "ef_search", "Kelime kolu satır", "Vektör kolu satır", "Embedding süresi", "SQL süresi", "Toplam süre". Süreler `"40 ms"` biçiminde, değer yoksa "—".
  - Tablo (caption "Sonuç başına sıralar ve puanlar"), sütunlar: "Sıra", "Parça" (`#1380`), "Kelime sırası", "Kelime puanı", "Vektör sırası", "Cosine mesafesi", "RRF puanı". Puanlar 4 ondalık (`0,1000`), değer yoksa "—".
  - `vectorPlan` varsa h3 "Sorgu planı" ve `role="region" aria-label="Sorgu planı metni"` içinde `<pre>`.
- Arama hataları: `role="alert"` içinde `messageFor(errorCode)`.

**Arama kayıtları** (`<title>Arama kayıtları · yanıt.</title>`)
- h1 "Arama kayıtları"; açıklama plandaki metin.
- Göstergeler bir `<dl>` içinde: `dt` "Bugün" → `dd` "4 arama"; "Sonuçsuz arama" → "%25 son 7 gün"; "Az sonuç dönen" → "%25 son 7 gün". Sayı ayrı bir `<span>` içinde; oran null ise "—".
- Tablo sütunları: "Saat", "Nereden", "Arama", "Yöntem", "Sonuç", "İlk sonuç".
- Rozetler: bad → "Sonuçsuz" (class `bad`), run → sayı (class `run`), ok → sayı (class `ok`). Rozetin kendi class'ı `badge`.
- Kayıt yoksa "Henüz arama kaydı yok". Yüklenirken "Kayıtlar yükleniyor…".
- Sayfalama (sadece `totalPages > 1` iken): `<nav aria-label="Sayfalar">`, "Önceki", `Sayfa 1 / 2`, "Sonraki".

**Bilgi kaynakları** (`<title>Bilgi kaynakları · yanıt.</title>`)
- Yol gösterici `<nav aria-label="Konum">`: "Dalga Fiber" (bağlantı → `/w/42/arama`) › "Bilgi kaynakları" (`aria-current="page"`). Ayraç "›" `aria-hidden`; öğeler ayrı `<li>`.
- h1 "Bilgi kaynakları"; açıklama "Arama sadece buradaki içerikte yapılır. Her kaynak parçalara bölünür ve embedding'e çevrilir."
- Tablo sütunları: "Kaynak", "Tür", "Parça", "Durum", "Son senkron".
  - Ad hücresi: bağlantı (kaynak adı) + `Dalga-T5-Kullanim-Kilavuzu-v3.1.pdf · v3.1` (dosya yoksa `— · sürüm yok`).
  - Türler: "Web sitesi", "PDF", "Metin / Markdown", "CSV", "Talep geçmişi".
  - Rozetler: "Hazır" / "İşleniyor" / "Başarısız".
  - Son senkron: `12 Eki 2026 14:31` ya da "—".
- Alt bilgi (tek `<p>`): `Toplam 42 parça · embedding modeli bge-m3@1024`.
- Kaynak yoksa "Bu çalışma alanında henüz kaynak yok".

**Kaynak detayı** (`<title>{ad} · yanıt.</title>`)
- Yol gösterici: "Bilgi kaynakları" (bağlantı → `/w/42/kaynaklar`) › ad. h1 ad.
- Bilgi kutuları (`dl`): "Tür", "Sürüm" (`3.1`), "Dosya adı", "Parça", "Embedding modeli", "Son senkron", "Durum" (rozet).
- Vurgu bilgisi (callout): `Arama sonucundan açılan parça vurgulandı: #1380`. `CHUNK_NOT_FOUND` olunca: `Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.`
- Parça tablosu sütunları: "#", "Parça", "Token". Parça hücresinde:
  - başlık (yoksa "Başlıksız parça");
  - önizleme (ilk 160 karakter + "…");
  - `chunk 1380`.
  - Vurgulu satırın class'ı `highlighted`.
- Buton "Daha fazla göster".

**Bulunamadı ekranları** (h1 + bağlantı)
- "Çalışma alanı bulunamadı" + "Adresteki çalışma alanı yok ya da silinmiş." + "Varsayılan çalışma alanına dön" → `/w/42/arama`.
- "Kaynak bulunamadı" + "Bilgi kaynaklarına dön" → `/w/:id/kaynaklar`.
- "Sayfa bulunamadı" + "Varsayılan çalışma alanına dön".

**Diğer**
- Yükleme metinleri: "Çalışma alanı yükleniyor…", "Kaynaklar yükleniyor…", "Kaynak yükleniyor…", "Parçalar yükleniyor…".
- Genel hata kutusu: `role="alert"` + "Tekrar dene" butonu.
- Error boundary: "Beklenmeyen bir hata oluştu. Tekrar dene." + "Tekrar dene".

## Plandan sapmalar
- **Planla ilgili bildirim (kodda sapma değil): E1'in adım sırası D9 ile çelişiyor.** E1, detay sayfasında `page.reload()` yaptıktan sonra `page.goBack()` ile sonuç listesini bekliyor. D9 ve onay kartı ise "sayfa yenilenince son arama sonucu silinir" diyor. Kod D9'a uyuyor: reload'suz geri dönüşte sonuçlar kalıyor, reload'dan sonra kalmıyor. Planner'a ve lead'e mesajla bildirdim. **Çözüldü:** planner E1'i `01-plan.md` "Düzeltme turu 1"de D9'a göre yeniden sıraladı: tıkla → `goBack` (sonuçlar) → `goForward` → `reload` (vurgu kalır). Kodda değişiklik gerekmedi.
- **Vitest `css.include`:** Planda sadece `css.modules.classNameStrategy: 'non-scoped'` yazıyordu. Vitest varsayılanında CSS işlenmediği için bu ayar tek başına etkisiz. Bu yüzden `css.include: [/\.module\.css$/]` de eklendi. Amaç aynı: testte class adı CSS'teki adla aynı (`bad`, `highlighted`).
- **`.oxlintrc.json`:**
  - Şablonun kurallarına `jsx-a11y` eklentisi ve `react/exhaustive-deps` eklendi.
  - `jsx-a11y/prefer-tag-over-role` kapatıldı. Planın istediği `role="search"`'ü `<search>` öğesine çevirmemi istiyordu; testlerin `getByRole('search')` sorgusu için `role` korundu.
  - `no-noninteractive-tabindex` kuralı `region` rolüne izin veriyor (kaydırılabilir sorgu planının klavyeyle erişimi için).
  - Yeni paket yok.
- **Planda adı geçmeyen küçük ek dosyalar (yeni dependency yok):**
  - `app/queryClient.ts`: `createQueryClient` component dosyasından ayrıldı, yoksa `only-export-components` uyarısı çıkıyordu.
  - `shared/components/Callout.tsx`, `table.module.css`, `visuallyHidden.module.css`.
  - `features/search/components/SearchPendingResults.tsx`.
  - Adres yardımcıları `workspacePath`, `sourceDetailPath`, `HIGHLIGHTED_CHUNK_PARAM` ve `parsePositiveId` `shared/workspace/workspaceRules.ts`'te. Arama feature'ı kaynak feature'ını import edemediği için `?parca=` adresi orada tek yerde tutuluyor.
- **`shortVectorWarning(result)`** `vectorShort` false iken `null` dönüyor (planda dönüş tipi yazmıyordu).
- **Kaynak listesi yol gösterici:** ilk öğe ("Dalga Fiber") bağlantı (→ Arama). TASK K28 "yol gösterici bağlantısı" diyor; lab'da düz metindi.

## Doğrulama
Hepsi `frontend/` içinde, 2026-10-25:
- `npm run build` (`tsc -b && vite build`): **geçti**, tip hatası yok, `✓ built`. Bundle: JS 339 kB (gzip 106 kB), CSS 54 kB (gzip ~20 kB; çoğu font-face tanımları).
- `npm run lint` (`oxlint`): **geçti**, 0 uyarı, 0 hata (exit 0).
- `npm test`: test dosyası yok, Vitest "No test files found" ile exit 1 veriyor. Testleri ve `src/test/setup.ts`'i tester yazacak (`vite.config.ts` `setupFiles` bu dosyayı gösteriyor). Bu adımda geçen ya da kalan test sayısı yok.
- `grep -rn "dangerouslySetInnerHTML\|innerHTML\|eval(\|console.log\|debugger" src`: boş.
- **Tarayıcı kontrolü:** Backend açık değildi. Scratchpad'de sözleşme şekline uyan geçici bir Node sahte API'si (8080) ve `npm run dev` ile Playwright Chromium'da denendi. Sahte API ve script'ler proje dışında; sunucular kapatıldı.
  - `/` → `/w/42/arama`, başlık "Bilgi bankası · yanıt.".
  - Sık aranan butonu ile arama sürerken "Aranıyor…" ve iskelet göründü. Sonuçta etiketler, `<mark>` ve kısa kaldı uyarısı çıktı.
  - `<img onerror>` ve `<b>` içeren içerik düz metin göründü (DOM'da `img`/`b` yok).
  - Teknik detay ve sorgu planı göründü. ef_search `5` için uyarı çıktı. Kelime yönteminde sonuç yok mesajı ve önerisi çıktı.
  - Sonuca tıklayınca `/w/42/kaynaklar/303?parca=1380`; vurgu bilgisi ve ilk satır vurgulu. Reload sonrası vurgu korunuyor. "Daha fazla göster" çalışıyor.
  - Arama kayıtları: göstergeler, rozetler ve sekme sayısının artması doğru.
  - Kaynaklar: türler, rozetler ve alt bilgi doğru. Seçicide Kıyı Net → `/w/19/kaynaklar`.
  - `/w/999/arama` → "Çalışma alanı bulunamadı". `/w/abc/arama` için `/api/workspaces/abc` isteği atılmadı (sadece liste isteği).
  - Olmayan kaynak → "Kaynak bulunamadı". Olmayan parça → `CHUNK_NOT_FOUND` metni. `/yok` → "Sayfa bulunamadı".
  - 503 `EMBEDDING_UNAVAILABLE` → Türkçe alert.
  - Klavye Tab sırası: logo → seçici → Arama → Arama kayıtları → Bilgi kaynakları → arama kutusu → 3 öneri. Enter ile arama çalışıyor.
  - 1024 px'te `scrollWidth` 1024 (yatay taşma yok).

## Tester için notlar
- **MSW handler'ı gereken endpoint'ler** (hepsi göreli `/api/...`):
  - `GET /api/workspaces?size=100` (seçici, her ekranda);
  - `GET /api/workspaces/:workspaceId`;
  - `GET /api/workspaces/:workspaceId/search-events/stats` (sekme rozeti, her ekranda);
  - `POST /api/workspaces/:workspaceId/search`;
  - `GET /api/workspaces/:workspaceId/search-events?page=N&size=20`;
  - `GET /api/workspaces/:workspaceId/sources?page=0&size=20`;
  - `GET /api/workspaces/:workspaceId/sources/:sourceId`;
  - `GET /api/workspaces/:workspaceId/sources/:sourceId/chunks?page=N&size=20`;
  - `GET /api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId` (sadece `?parca=` varken).
- **Render:** `App` sadece `<Routes>` döner; router ve QueryClient içermez. `renderApp(path)` için `QueryClientProvider` (retry false) + `MemoryRouter initialEntries={[path]}` + `<App />` yeterli. `AppProviders` `BrowserRouter` içerdiği için testte kullanılmamalı. Prod ayarlarıyla client gerekirse `app/queryClient.ts` → `createQueryClient()`.
- **Sayfalar:** `WorkspaceLayout` dışında render edilemez, çünkü `useCurrentWorkspace` yüklü alan bekler ve yoksa hata fırlatır. Sayfa testleri `renderApp('/w/42/...')` ile yapılmalı.
- **Son arama** `LastSearchProvider` context'inde (layout içinde, `key={workspaceId}`). Sayfalar arası gezinmede ve geri butonunda kalıyor, alan değişince siliniyor. Arama sayfasındaki input açılışta son aramanın metniyle doluyor.
- **Ayarlar:** localStorage anahtarı `emb:search-settings:v1`, değer `{ mode, filterStrategy, efSearch, limit, explain }` (sayılar number). Panel `<details>` kontrollü (`open` + `onToggle`); jsdom'da özet tıklaması `toggle` olayını tetiklemezse paneli `open` niteliğiyle kontrol edin. Panel kapalıyken içindeki alanlar DOM'da durur (`getByLabelText` çalışır).
- **CSS sınıfları:** `non-scoped` olduğu için sınıflar CSS'teki adlarıyla gelir: rozet `badge` + `ok` / `run` / `bad` / `neutral`; vurgulu parça satırı `highlighted`; aktif sekme `tab active`.
- **Saat biçimi:** `formatClock(iso, now = new Date())` ikinci parametreyle deterministik test edilebilir; `formatDateTime(iso)`.
- **Diğer imzalar:**
  - `toHighlightSegments(text, terms, field)`, `buildSnippet(content)`, `hitTitle(heading)` → `features/search/model/searchHitText.ts`.
  - `messageFor(errorCode)` ve `messageForError(error)` → `shared/api/errorMessages.ts`. `messageForError`, `ApiError` olmayan hatada `console.error` çağırır.
- **Kaynak satırı:** `tr` `onClick` ile detaya gidiyor. Ad hücresindeki bağlantıya tıklama ayrıca yakalanmıyor (çift navigate yok). jsdom'da satır hücresine `user.click` yeterli.
- **STOMP yok.** Polling yok.
- **E2E:** E1'in güncel sırası `01-plan.md` "Düzeltme turu 1"de. Dev server'a karşı `E2E_BASE_URL=http://localhost:5173`. Backend 8080'de açık olmalı (Vite proxy).

## Düzeltme
Yok.

STATUS: DONE
