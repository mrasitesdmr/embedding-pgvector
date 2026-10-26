TASK: TASK-009
# Frontend uygulama raporu

## Kullanılan skill'ler
`frontend-conventions`, `react-patterns`, `frontend-a11y`, `frontend-security`, `frontend-design` (planın "Frontend planı"ndaki liste).

## Yapılanlar
| Plan maddesi / Kural # | Dosya(lar) | Not |
|---|---|---|
| D4: `shared/chunk/` | `shared/chunk/types.ts`, `chunkApi.ts`, `chunkQueries.ts`, `chunkRules.ts` | `ChunkResponse` ve `getChunk` gövdeleri aynen taşındı. `SOURCES_QUERY_SCOPE = 'sources'`, `chunkKey(...)` → `['sources', workspaceId, sourceId, 'chunk', chunkId]` (eski `sourceKeys.chunk` ile aynı değer), `useChunkQuery` + `staleTime: 0`. `CHUNK_GONE_ERROR_CODES` (`as const`) ve `isChunkGoneError` (ApiError + 404 + kod listede) |
| D4: `features/source` import'ları | `features/source/api/sourceApi.ts`, `sourceQueries.ts`, `model/types.ts`, `pages/SourceDetailPage.tsx`, `components/ChunkTable.tsx` | `sourceKeys.all` → `[SOURCES_QUERY_SCOPE, workspaceId]`; `sourceKeys.chunk`, eski `getChunk`, eski `useChunkQuery` ve eski `ChunkResponse` silindi. Re-export yok |
| D5, D6, D9, D10: Dialog | `shared/components/Dialog.tsx`, `Dialog.module.css` | `title: ReactNode`; `size` (`'default'`/`'wide'`), `hasCloseButton` (× düğmesi `aria-label="Paneli kapat"`, `disabled={!canClose}`), `closeOnBackdropClick` (`target === currentTarget && canClose`), `returnFocusRef` (cleanup'ta bağlıysa ona, değilse `opener`'a odak). `<dialog>` padding 0, içerik `.content` sarmalayıcısında (flex column, gap, `overflow-y: auto`). Varsayılanlarla `CreateSourceDialog`/`UploadVersionDialog` aynı davranır ve aynı görünür (tarayıcıda kontrol edildi) |
| D7 / K8: kaydırma kilidi | `index.css` | `:root { scrollbar-gutter: stable }`, `:root:has(dialog[open]) { overflow: hidden }` |
| K2, K4: metin yardımcıları | `features/search/model/searchHitText.ts` | Yeni: `hitTitleSegments`, `hitSnippetSegments`, `hitContentSegments`, `hitSourceLine`. Mevcutlar (`buildSnippet`, `toHighlightSegments`, `hitTitle`) kaldı |
| K5: etiketler | `features/search/components/SearchHitTags.tsx` + `.module.css` | "anlam"/"kelime"; ikisi de yoksa `null`. Stiller `SearchHitItem.module.css`'ten taşındı (değerler aynı) |
| K1, D8: sonuç düğmedir | `features/search/components/SearchHitItem.tsx` + `.module.css` | Kök `<button type="button">`, prop'lar `{ hit, onOpen }` (`workspaceId` kaldırıldı). CSS: `width: 100%`, `text-align: left`, `font: inherit`, `cursor: pointer`, `:focus-visible` outline |
| K1, K7, D12, G2 | `features/search/components/SearchHitList.tsx` | `openHit` state'i + `openerRef`; `handleOpen` önce ref'i sonra state'i yazar; panel sadece `openHit !== null` iken mount edilir. `LastSearchProvider`'a dokunulmadı |
| K2-K6, K9, K11, K12, D3, D10 | `features/search/components/SearchHitDialog.tsx` + `.module.css` | `Dialog` `size="wide"`, `hasCloseButton`, `closeOnBackdropClick`, `returnFocusRef`. Sıra: başlık (vurgulu) + ×; `.meta` (etiketler + `<small>` kaynak satırı); metin bölgesi (`role="region"`, `aria-label="Parça metni"`, `tabIndex={0}`, `pre-wrap`, kendi içinde kayar) ya da gone ise `<div role="alert"><Callout tone="warning">{messageForError(error)}</Callout></div>`; `.actions`: "Kapat" + (gone değilse) "Kaynakta göster" `Link`. Varlık kontrolü `useChunkQuery`; başka hata (ağ, 5xx) → metin kalır, `messageForError` çağrılmaz. `dangerouslySetInnerHTML` yok |
| K10, D11 | `features/source/components/ChunkTable.tsx` + `.module.css` | Vurgulu satırda `<span className={styles.fullText}>{chunk.content}</span>` (`pre-wrap`, `overflow-wrap: anywhere`); diğer satırlar `previewChunkContent` |
| `SearchResults.tsx` | değişmedi | `SearchHitList`'e `workspaceId` geçmeye devam ediyor |
| errorCode → mesaj | değişmedi | `errorMessages.ts`'e yeni kod/metin eklenmedi |
| Diğer: `frontend/CLAUDE.md` | `frontend/CLAUDE.md` | Klasör yapısında `shared/chunk/`; "Tuzaklar"a kaydırma kilidi, `.content` padding'i ve StrictMode `close` olayı notu |

## Değişen dosyalar
Yeni (`frontend/src/`):
- `shared/chunk/types.ts`, `shared/chunk/chunkApi.ts`, `shared/chunk/chunkQueries.ts`, `shared/chunk/chunkRules.ts`
- `features/search/components/SearchHitDialog.tsx`, `SearchHitDialog.module.css`
- `features/search/components/SearchHitTags.tsx`, `SearchHitTags.module.css`

Değişen:
- `index.css`
- `shared/components/Dialog.tsx`, `shared/components/Dialog.module.css`
- `features/search/model/searchHitText.ts`
- `features/search/components/SearchHitItem.tsx`, `SearchHitItem.module.css`, `SearchHitList.tsx`
- `features/source/api/sourceApi.ts`, `features/source/api/sourceQueries.ts`, `features/source/model/types.ts`
- `features/source/pages/SourceDetailPage.tsx`
- `features/source/components/ChunkTable.tsx`, `ChunkTable.module.css`
- `frontend/CLAUDE.md`

Silinen dosya yok. Eklenen paket yok.

## Ekran metinleri
- Arama sonucu: `button`. Erişilebilir adı kartın bütün metni (sıra no, başlık, etiketler, önizleme, kaynak satırı).
- Panel: `dialog`, adı başlık metni (`heading`, yoksa "Başlıksız parça"), `aria-labelledby` ile h2'ye bağlı.
- × düğmesi: `aria-label` "Paneli kapat" (panelde ilk odak burada).
- Etiketler: "anlam", "kelime".
- Kaynak satırı: `<kaynak adı> · parça <chunkNo> · #<chunkId>` (ör. "Atlaskap-Lojistik-Surec-El-Kitabi · parça 31 · #1380").
- Metin bölgesi: `role="region"`, adı "Parça metni", `tabIndex=0`.
- Alt düğmeler: "Kapat" (`button`), "Kaynakta göster" (`link`, `href` `/w/<ws>/kaynaklar/<sourceId>?parca=<chunkId>`).
- Parça yok (`role="alert"`): `CHUNK_NOT_FOUND` → "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.", `SOURCE_NOT_FOUND` → "Kaynak bulunamadı.", `WORKSPACE_NOT_FOUND` → "Çalışma alanı bulunamadı." Bu durumda "Parça metni" ve "Kaynakta göster" yok, "Kapat" ve "Paneli kapat" var.
- Kaynak detayında vurgulu satırın tam metni `span.fullText` (CSS Modules testte non-scoped: class adı `fullText`); diğer satırlarda `span.preview`.
- Panel Tab sırası (metin durumu): "Paneli kapat" → "Parça metni" → "Kapat" → "Kaynakta göster" → "Paneli kapat" …

## Plandan sapmalar
1. **`Dialog.handleNativeClose` düzeltmesi (planda yoktu, davranışı plana uydurmak için gerekti).** Plan D12'deki gibi her zaman `isOpen` ile mount edilen bir `Dialog`'da StrictMode (`main.tsx`) açma effect'ini iki kez çalıştırıyor. İlk cleanup'ın `dialog.close()`'u bir `close` olayı kuyruğa koyuyor, olay ikinci `showModal()`'dan sonra geliyor ve `onClose`'u çağırıyordu. Sonuç: tarayıcıda panel açılır açılmaz kapanıyordu. Mevcut iki diyalog `isOpen=false` ile mount edildiği için bu durum onlarda hiç çıkmıyordu. Düzeltme: `handleNativeClose` artık sadece `isOpen && !event.currentTarget.open` iken `onClose` çağırıyor (gövdede tek satırlık "neden" yorumu var). Mevcut davranış değişmedi: gerçek kapanmada diyalog zaten kapalı. jsdom'da StrictMode kullanılmadığı için unit testler bu durumu yakalamaz; E2E (EP1) yakalar.
2. **Dialog'da bir lint yorumu.** `<dialog>`'a `onClick` eklemek `jsx-a11y/no-noninteractive-element-interactions` uyarısı veriyor. Tek satırlık bir `oxlint-disable-next-line` ve bir satırlık neden yazıldı: backdrop tıklaması dialog öğesinin kendisine düşüyor, klavye karşılığı Esc. `.oxlintrc.json` değişmedi.
3. **`returnFocusRef` değeri effect başında okunuyor.** Plan "cleanup'ta `returnFocusRef?.current?.isConnected`" diyor. Cleanup'ta `ref.current` okumak `react-hooks/exhaustive-deps` uyarısı veriyor. Bu yüzden değer effect başında bir değişkene alınıyor, `isConnected` kontrolü yine cleanup'ta yapılıyor. `SearchHitList` ref'i state'ten önce yazdığı için, panel açıkken değer değişmiyor (modal açıkken başka sonuca tıklanamıyor). Sonuç aynı.
4. **Build ve test fixture'ı (lead kararı: B).** `tsconfig.app.json` `include: ["src"]` olduğu için `tsc -b` test dosyalarını da derliyor. `src/test/fixtures.ts:8` `ChunkResponse`'u silinen yerden (`features/source/model/types.ts`) import ediyor. Lead'e soruldu, karar B oldu: `fixtures.ts` tester'ın dosyası olduğu için ona dokunulmadı ve plan gereği re-export yazılmadı. Satırı tester ilk iş olarak düzeltecek; lead tester'a iletiyor.

## Doğrulama
`frontend/` içinde:
- `npm run lint` → **geçti**, 0 uyarı, 0 hata (çıkış kodu 0, oxlint çıktısında bulgu yok).
- `npm run build` → **KALDI, sadece test dosyası kaynaklı.** `tsc -b` çıktısındaki tek hata satırı:
  ```
  src/test/fixtures.ts(8,15): error TS2305: Module '"../features/source/model/types.ts"' has no exported member 'ChunkResponse'.
  ```
  Uygulama kodunda (test dışı dosyalarda) TS hatası yok. Link'ten button'a dönüş yüzünden kırılan iki test dosyası tip hatası vermiyor, sadece çalışırken kalıyor (aşağıda). Bkz. sapma 4.
- `npx vite build` → **geçti** (`✓ built in 121ms`), bundle `dist/`'e üretildi.
- **Son durum (rapordan sonra):** `src/test/fixtures.ts` başka bir rol tarafından (frontend-coder değil, muhtemelen tester) düzeltilmiş. Şu an satır 8'de `IngestionJobResponse, SourceResponse`, satır 9'da `import type { ChunkResponse } from '../shared/chunk/types.ts'` var. Bu hâliyle yeniden çalıştırıldı: `npx tsc -b` hatasız (çıkış 0), `npm run build` **geçti** (çıkış 0, `✓ built in 116ms`).
- `npm test` → 27 dosya / 413 test: **411 geçti, 2 kaldı**. İkisi de beklenen kırılmalar (sonuç artık link değil); bkz. "Tester için notlar".
- `grep -rn "dangerouslySetInnerHTML\|innerHTML\|eval(\|new Function" src` → boş.
- Tarayıcıda elle deneme: dev server (5199) + çalışan dev backend (8080), Playwright betiği scratchpad'de; repoya test eklenmedi. Viewport 360×640, Hybrid aramada "masraf beyanı" (dev DB'ye 2 arama kaydı yazıldı):
  - Sonuca tıklama → panel açıldı, adres `/w/42/arama` olarak kaldı, ilk odak "Paneli kapat"ta.
  - Panel kutusu ekranın içinde (`x 16.5, y 10, 327×620`). Metin bölgesi `scrollHeight 1150 > clientHeight 438`, `scrollWidth 293 = clientWidth 293`. 11 `mark` var, "…" yok.
  - `mouse.wheel(0, 600)` sonrası `window.scrollY` değişmedi (78 → 78).
  - Tab 5 kez: sıra "Parça metni" → "Kapat" → "Kaynakta göster" → "Paneli kapat" → "Parça metni"; odak hep diyalog içinde kaldı.
  - Esc → panel kapandı, odak tıklanan sonuca döndü. Backdrop tıklaması → panel kapandı.
  - "Kaynakta göster" → `/w/42/kaynaklar/10001?parca=8`. Vurgulu satırın `textContent`'i REST `content`'iyle (1474 karakter) birebir aynı. Geri tuşu → sonuçlar listede, panel yok. Konsolda hata yok.
  - "Yeni sürüm yükle" diyaloğu eski görünümde; backdrop tıklamasıyla kapanmıyor, Esc ile kapanıyor.

## Tester için notlar
- **İlk iş: düzeltilecek satırlar (test dosyalarına dokunulmadı):**
  1. `src/test/fixtures.ts:8` (build'i kıran tek satır). Şu anki satır `import type { ChunkResponse, IngestionJobResponse, SourceResponse } from '../features/source/model/types.ts'`. Bu satır `import type { IngestionJobResponse, SourceResponse } from '../features/source/model/types.ts'` olmalı ve yanına `import type { ChunkResponse } from '../shared/chunk/types.ts'` eklenmeli. Bu yapılmadan `npm run build` kırık kalır.
  2. `features/search/components/SearchResults.test.tsx` › `givenHit_whenRendered_thenLinksToSourceDetailWithChunk`: sonucu `link` olarak arıyor. Plana göre SR1 ile değişecek.
  3. `features/search/pages/SearchPage.test.tsx` › `givenHitsAndSearchEventStats_whenSearchCompletes_thenResultIsKeptAfterVisitingSourceAndGoingBack`: `findByRole('link', { name: /Yıllık izin kaç gün?/ })` artık bulunamıyor. Sonuç `button` oldu; kaynak sayfasına panelin "Kaynakta göster" bağlantısıyla gidilmeli. Bu test planın "Mevcut testlerde değişecekler" listesinde yok, ek olarak düzeltilmeli.
- **MSW:** Panel açan her test `GET /api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId` isteği yapar (`useChunkQuery`, `staleTime: 0`). `handlers.ts`'e 200 + `chunk(...)` dönen varsayılan handler gerekiyor; 404 durumları `problem(404, 'CHUNK_NOT_FOUND' | 'SOURCE_NOT_FOUND' | 'WORKSPACE_NOT_FOUND')`. HD8 (5xx / ağ hatası) için: uygulamanın `queryClient`'ı 5xx'te 2 kez yeniden deniyor (`shouldRetryQuery`). Testte `retry: false` olan bir `QueryClient` kullanılmazsa istek sayacı 3'e çıkar.
- **Backdrop tıklaması (DT1, HD10, SP3):** `user.click(dialogElement)` yeterli; tıklama hedefi `<dialog>`'un kendisi olmalı. İçerik `.content` sarmalayıcısında olduğu için içerideki herhangi bir öğeye tıklama paneli kapatmaz.
- **Odak dönüşü (DT4):** `returnFocusRef.current` effect çalışırken okunur (bkz. sapma 3). Ref'i `Dialog` mount edilmeden önce doldur; `isConnected` kontrolü unmount sırasında yapılır.
- **StrictMode:** `renderWithProviders` StrictMode kullanmıyorsa sapma 1'deki hata unit testte görünmez. İstenirse `Dialog`'u `<StrictMode>` içinde `isOpen` ile mount edip açık kaldığını doğrulayan bir test eklenebilir. EP1 bu durumu gerçek uygulamada zaten kapsıyor.
- **Mutation check noktaları:** `isChunkGoneError` (status 404 dalı, kod listesi), `closeOnBackdropClick && canClose && target === currentTarget`, `returnTarget?.isConnected` dalı, `handleNativeClose`'taki `!event.currentTarget.open`, `ChunkTable`'daki `isHighlighted` dalı.
- `hitTitle` hâlâ export ediliyor ve mevcut testi geçiyor. `SearchHitItem` artık onu değil `hitTitleSegments`'i kullanıyor.
- E2E'de panelin kaynak satırından `chunkId` okunacak: metin `… · #<chunkId>` ile bitiyor ve `<small>` içinde.

## Düzeltme
Yok.

STATUS: DONE
