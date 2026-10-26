TASK: TASK-009
# Plan

## Özet
**Mevcut davranış:** Arama sonucu (`features/search/components/SearchHitItem.tsx`) bir `Link`'tir ve kaynak detayına gider (`sourceDetailPath(workspaceId, sourceId, chunkId)` → `?parca=<chunkId>`). Listede parça metni `buildSnippet` ile 190 karaktere kısaltılır. Kaynak detayında `ChunkTable` her parçayı `previewChunkContent` ile 160 karaktere kısaltır; vurgulanan parça da kısaltılmış görünür. Tam metin hiçbir yerde okunamaz.

**Backend kontrolü (değişiklik yok):** `SearchHitResponse.content` parçanın tam metnidir: arama sorguları `c.content`'i olduğu gibi seçer (`ChunkSearchRepository.java:46,62,77`), `SearchService.java:184-186` onu kısaltmadan DTO'ya koyar. `matchedTerms` offset'leri tam `content` üzerinde hesaplanır (`SearchService.java:181-183` → `KeywordHighlighter.highlight(..., hit.heading(), hit.content())`, `Matcher.start()/end()` UTF-16 indeksidir, JavaScript string indeksiyle aynı). Parça metni satır sonlarını korur (`TextChunker.java`: paragraf içinde `\n`, paragraflar arası `\n\n`). Bu yüzden backend-coder çalışmaz; sözleşmeye sadece açıklama eklendi.

**Yeni davranış (sadece frontend):**
1. Arama sonucu bir `button`'dır. Tıklanınca (ya da Enter) arama sayfasının üstünde `SearchHitDialog` açılır: başlık (vurgulu), "anlam"/"kelime" etiketleri, `<kaynak adı> · parça <chunkNo> · #<chunkId>`, parçanın tam metni (vurgulu, satır sonları korunur, panel içinde kayar), "Kapat" ve "Kaynakta göster". Adres değişmez.
2. Panel açılınca parçanın hâlâ var olduğu `GET …/sources/{sourceId}/chunks/{chunkId}` ile kontrol edilir. 404 (`CHUNK_NOT_FOUND`, `SOURCE_NOT_FOUND`, `WORKSPACE_NOT_FOUND`) gelirse metin yerine `messageForError` mesajı görünür.
3. Panel × düğmesi, "Kapat", Esc ve dışına tıklama ile kapanır; odak tıklanan sonuca döner; arkadaki sayfa kaymaz.
4. Kaynak detayında vurgulanan parçanın tam metni görünür; diğerleri önizleme kalır.

**Değişen / taşınan mevcut parçalar (eski ad → yeni ad):**
- `SearchHitItem`: kök öğe `Link` → `button`; yeni prop `onOpen`. Etiket span'ları `SearchHitTags` component'ine taşınır.
- `ChunkResponse` type'ı: `features/source/model/types.ts` → `shared/chunk/types.ts`.
- `getChunk`: `features/source/api/sourceApi.ts` → `shared/chunk/chunkApi.ts`.
- `useChunkQuery`: `features/source/api/sourceQueries.ts` → `shared/chunk/chunkQueries.ts`; `sourceKeys.chunk(...)` → `chunkKey(...)` (aynı key değeri).
- `shared/components/Dialog.tsx`: `title: string` → `title: ReactNode`; yeni isteğe bağlı prop'lar (varsayılanlarıyla mevcut iki diyalog aynı davranır).

**Bölme:** Önerilmiyor. Panel ve kaynak detayındaki tam metin aynı akışın iki ucu ("Kaynakta göster"); tek frontend teslimi.

## Çalışacak roller
| Sıra | Rol | Ne yapar |
|------|-----|----------|
| 1 | planner | Bu plan, onay kartı, `openapi.yaml` açıklamaları |
| 2 | frontend-coder | Panel, Dialog genişletmesi, `shared/chunk/`, kaynak detayında tam metin |
| 3 | tester | Unit, component, axe ve E2E testleri; mevcut testlerin yeni davranışa uyarlanması |
| 4 | qa | Kural ve kabul kriterlerinin son kontrolü, çalışan sistemde deneme |

- **Atlanan: backend-coder.** Arama cevabı zaten tam metni ve tam metin üzerindeki offset'leri taşıyor (bkz. Özet); yeni endpoint ya da alan yok. `./mvnw verify` bu task'ta zorunlu değil (TASK: "backend değişirse").
- **Atlanan: devops.** Dockerfile, compose, nginx, README ve `ollama.md` değişmiyor (README ve `ollama.md`'de arama sonucuna tıklama anlatılmıyor). Site imajının yeniden derlenmesi dosya değişikliği değildir; tester ve qa komutu çalıştırır (bkz. "Deployment planı").
- Bağımlılık: qa, tester'a bağlıdır.

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | Sonuca tıklama ya da Enter → panel açılır, sayfa değişmez | `SearchHitItem` (`button`), `SearchHitList` (açık sonuç state'i), `SearchHitDialog` | SR1, SP1, SP2, EP1, EP3 |
| K2 | Panelde başlık ("Başlıksız parça"), kaynak adı, parça no, parça id, tam metin; "…" yok | `SearchHitDialog`, `hitTitleSegments`, `hitSourceLine`, `hitContentSegments` | HT1, HD1, HD2, EP1 |
| K3 | Satır/paragraf yapısı korunur; uzun metin panel içinde kayar, panel ekrandan taşmaz | `SearchHitDialog.module.css` (`.text`: `white-space: pre-wrap`, `overflow: auto`), `Dialog` `size="wide"` + `.content` flex düzeni | HD1 (metin aynen), EP1 (`innerText` md5, 360×640'ta kutu ekran içinde, bölge kayar) |
| K4 | Kelime eşleşmeleri panelde de vurgulu, metnin tamamında doğru yerde | `hitContentSegments` (tam `content` + `matchedTerms` `CONTENT`), `hitTitleSegments` | HT1, HT2, HD3, EP2 |
| K5 | "anlam"/"kelime" etiketleri panelde | `SearchHitTags` (liste ve panel ortak) | HD4 |
| K6 | Kapatma düğmesi, Esc, dışına tıklama | `Dialog` `hasCloseButton`, `closeOnBackdropClick`, mevcut `onCancel`; panel alt satırında "Kapat" | DT1, DT3, HD10, SP3, EP1 (Esc) |
| K7 | Kapanınca arama metni, ayarlar, sonuçlar kalır; yeni arama ve arama kaydı yok; odak tıklanan sonuca döner | Panel state'i `SearchHitList`'te yerel; `LastSearchProvider`'a dokunulmaz; `Dialog` `returnFocusRef` | DT4, SP1, SP2, EP1 (DB `search_events` sayısı), EP3 |
| K8 | Panel açıkken arka sayfa kaymaz, odak panel dışına çıkmaz | `index.css` `:root:has(dialog[open])`; native `showModal()` + `Dialog.handleKeyDown` Tab döngüsü | DT5, SP2, EP1 (`scrollY` değişmez), EP3 |
| K9 | "Kaynakta göster" → kaynak detayı, parça vurgulu en üstte | `SearchHitDialog` `Link` → `sourceDetailPath(workspaceId, sourceId, chunkId)`; mevcut `SourceDetailPage` sıralaması | HD9, SP4, EP1 |
| K10 | Kaynak detayında vurgulanan parça tam metin; diğerleri önizleme | `ChunkTable` (`highlightedChunkId` satırı `.fullText`) | CT1, SP4, EP1 |
| K11 | Parça silinmiş/değişmişse panelde Türkçe "artık yok" mesajı (`errorCode` eşlemesi), sayfa bozulmaz | `SearchHitDialog` + `useChunkQuery` + `isChunkGoneError`; mesaj `messageForError` | HD6, HD7, HD8, EP4 |
| K12 | HTML/script içeren metin metin olarak gösterilir | React metin düğümü; `dangerouslySetInnerHTML` yok | HD5 |
| S1 | PDF, anlam araması "Yıllık izin kaç gün?", panel, Esc, odak ilk sonuçta | — | EP1 |
| S2 | "masraf beyanı" kelime araması, metnin sonlarında da vurgu | — | EP2, HD3 |
| S3 | "Kaynakta göster" → parça en üstte, vurgulu, tam metin | — | EP1, SP4 |
| S4 | Başka sekmede yeni sürüm, sonra eski sonuca tıklama → mesaj, panel kapatılabilir | — | EP4, HD6 |
| S5 | Sadece klavye: Tab ile 2. sonuç, Enter, Tab panel içinde, Esc, odak 2. sonuçta | — | SP2, EP3 |
| AC1 | `npm run build`, `npm run lint`, unit ve E2E geçer (backend değişmediği için `verify` zorunlu değil) | tester, qa | T-ALL |
| AC2 | Açılma, tam metin, vurgu, kapanınca sonuç ve odak, "artık yok" otomatik testli | — | HD1, HD3, HD6, SP1, SP2, EP1, EP2, EP4 |
| AC3 | En az bir E2E: yükle, ara, tıkla, tam metin, kapat, "Kaynakta göster" | — | EP1 (örnek PDF önceki E2E testlerinde yüklenir; aynı serial akış) |
| AC4 | Panel axe kontrolünden geçer | — | HD11 (metin ve "parça yok" durumları) |
| AC5 | Panel aç/kapa yeni arama kaydı oluşturmaz | — | SP1 (POST sayısı 1), EP1 (DB sayısı aynı), qa Q3 |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | Dar ekran (360 px) ve çok uzun parça (≈1600-2400 karakter), boşluksuz uzun kelime | Panel ekranın içinde kalır; metin bölgesi kendi içinde dikey kayar; uzun kelime satıra sarılır, yatay taşma yok | EP1 (360×640: panel kutusu ekran içinde, bölge `scrollHeight > clientHeight`, `scrollWidth <= clientWidth`) |
| G2 | Sonuca hızlı çift tıklama | Tek panel açılır | SP5 |
| G3 | Parçanın varlığı kontrol edilirken sunucuya ulaşılamıyor ya da 5xx | Metin gösterilmeye devam eder (arama sonucuyla zaten geldi); uyarı çıkmaz; panel normal kapanır | HD8 |
| G4 | Eşleşmeden önce Türkçe harf (İ, ı) ya da iki birimlik karakter (emoji) | Vurgu kaymaz; işaretlenen metin tam olarak eşleşen kelimedir | HT2 |
| G5 | "Kaynakta göster"den sonra tarayıcının geri tuşu | Arama sayfası aynı metin ve sonuçlarla açılır, panel kapalı, yeni arama kaydı yok | EP1 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | Backend değişmez; panel metni `SearchHitResponse.content`'ten, vurgu `matchedTerms`'ten gelir | İkisi de tam metin üzerinde (Özet'teki satırlar); ek istek beklemeden panel anında dolar | Tam metin için yeni endpoint ya da alan: veri zaten var |
| D2 | Sözleşmede sadece açıklama değişti; `info.version` 0.5.0 kalır. Backend `@Schema` açıklamaları güncellenmez; qa bu farkı sözleşme farkı saymaz (path, method, status, alan, `errorCode` aynı) | TASK-008 D13 ile aynı gerekçe; backend rolü başlatmamak için | 0.5.1: yapısal değişiklik yokken backend değişikliği ister |
| D3 | **Varlık kontrolü:** Panel açılınca `useChunkQuery(workspaceId, hit.sourceId, hit.chunkId)`. Metin istek beklenmeden `hit.content`'ten gösterilir. Hata `isChunkGoneError` ise (404 + `CHUNK_NOT_FOUND`/`SOURCE_NOT_FOUND`/`WORKSPACE_NOT_FOUND`) metin bölgesi ve "Kaynakta göster" kaldırılır, yerine `role="alert"` içinde `Callout tone="warning"` + `messageForError(error)`. Başka hata (ağ, 5xx): metin kalır, uyarı yok, `console.error` yok (`messageForError` çağrılmaz). Başarılı cevabın içeriği kullanılmaz (aynı id'li parçanın metni değişmez; yeni sürüm yeni id üretir) | Kural 11 tespit ister; çoğu açılışta yükleniyor satırı titremez | İstek bitene kadar "yükleniyor": her açılışta titreme. Kontrol yok: kural 11 karşılanmaz. 404'te eski metni uyarıyla göstermek: senaryo "parça artık yok" diyor, eski metin yanıltıcı |
| D4 | **`shared/chunk/` modülü:** `types.ts` (`ChunkResponse`, taşındı), `chunkApi.ts` (`getChunk`, taşındı), `chunkQueries.ts` (`SOURCES_QUERY_SCOPE = 'sources'`, `chunkKey`, `useChunkQuery` taşındı, `staleTime: 0`), `chunkRules.ts` (`CHUNK_GONE_ERROR_CODES`, `isChunkGoneError`). `sourceKeys.all` `SOURCES_QUERY_SCOPE`'u kullanır; `chunkKey` değeri bugünkü `sourceKeys.chunk` ile aynıdır (`['sources', workspaceId, sourceId, 'chunk', chunkId]`) | İki feature aynı endpoint'i kullanıyor; feature'lar birbirini import edemez. Aynı key: "Kaynakta göster" sonrası vurgulu parça önbellekten anında gelir; yeni sürüm bitince `invalidateSourceData` panelin key'ini de geçersiz kılar. `staleTime: 0`: başka sekmede silinen parça, önbellek taze olsa bile her açılışta yeniden sorulur | Search feature'da ikinci `getChunk` + ikinci `ChunkResponse` type'ı: iki tanım ayrışır, önbellek paylaşılmaz, kaynak verisiyle birlikte geçersiz kılınmaz. `features/source`'tan import: yasak |
| D5 | **Dialog yeniden kullanılır ve genişletilir** (isteğe bağlı prop'lar; varsayılanlar bugünkü davranış): `size?: 'default' \| 'wide'`, `hasCloseButton?: boolean`, `closeOnBackdropClick?: boolean`, `returnFocusRef?: RefObject<HTMLElement \| null>`; `title: ReactNode` | Focus tuzağı, Esc, `showModal` ve test taklidi tek yerde kalır; `CreateSourceDialog` ve `UploadVersionDialog` değişmez | Ayrı panel component'i: focus tuzağı ve Esc kodu kopyalanır |
| D6 | **Dışına tıklama tespiti:** `<dialog>`'un kendi padding'i 0 olur, bütün içerik `.content` sarmalayıcısında (padding, gap, flex column). `onClick`'te `event.target === event.currentTarget` ise backdrop'a tıklanmıştır | Sarmalayıcı diyalog kutusunu tamamen kapladığı için diyaloğun kendisine düşen tıklama sadece backdrop'tan gelir; jsdom'da da test edilebilir | Tıklama koordinatını `getBoundingClientRect` ile karşılaştırmak: jsdom'da kutu 0×0, test edilemez |
| D7 | **Kaydırma kilidi CSS ile:** `index.css`'e `:root:has(dialog[open]) { overflow: hidden; }` ve `:root { scrollbar-gutter: stable; }`. Bütün modal diyaloglarda geçerli | Native `showModal()` arka sayfanın kaymasını engellemez; CSS'te açma/kapama durumu tutulmaz, StrictMode'dan etkilenmez; gutter kaydırma çubuğu kaybolurken sayfanın yana kaymasını önler | `document.body.style.overflow`'u effect'te değiştirmek: iç içe/çift effect'te geri alma hatası riski |
| D8 | **Sonuç bir `button`'dır, panel adreste yer almaz.** Orta tıkla yeni sekmede açma kalkar; yeni sekme için paneldeki "Kaynakta göster" bağlantısı kullanılır | Kural 1: "Sayfa değişmez". Diyalog açan öğe semantik olarak düğmedir. Arama sonuçları adreste olmadığı için sayfa yenilenince panel zaten geri getirilemez | `Link` + `preventDefault`: ekran okuyucu "bağlantı" der ama sayfa değişmez. `?parca=` arama sayfasında: sonuçlar yenilemede kaybolduğu için anlamsız |
| D9 | Odak dönüşü `returnFocusRef` ile: `SearchHitList` tıklanan düğmeyi bir ref'te tutar; `Dialog` kapanırken (unmount cleanup) `returnFocusRef.current` bağlıysa ona, değilse bugünkü `opener`'a odaklanır | Safari ve Firefox'ta (macOS) tıklanan düğme odak almaz; `document.activeElement` `body` olur | Sadece `activeElement`: Chromium dışında odak kaybolur |
| D10 | **Panel düzeni** (lab `openChunk` referansı): genişlik `min(620px, calc(100% - 2 * var(--space-5)))`; başlık satırında başlık + × düğmesi (`aria-label="Paneli kapat"`); altında etiketler + kaynak satırı; metin bölgesi `role="region"`, `aria-label="Parça metni"`, `tabIndex={0}`; alt satırda "Kapat" (`button.secondary`) ve "Kaynakta göster" (`Link`, `button.primary`). İlk odak ×'tedir (Dialog'un ilk odaklanabilir öğesi) | Lab ile aynı; iki kapatma düğmesinin farklı adı testte ve ekran okuyucuda karışıklığı önler; klavyeyle kaydırma için bölge odaklanabilir (`.oxlintrc.json` `region`'a izin verir) | İki düğmenin de adı "Kapat": aynı ada sahip iki düğme, sorgu belirsiz |
| D11 | Kaynak detayında vurgulanan parçanın tam metninde kelime vurgusu yok (lab'daki gibi) | Kural 10 sadece tam metin istiyor; eşleşen terimler kaynak sayfasına taşınmıyor | Terimleri adrese koymak: kapsam dışı yeni davranış |
| D12 | `SearchHitList` `const [openHit, setOpenHit] = useState<SearchHitResponse \| null>(null)` tutar; `SearchHitDialog` sadece `openHit !== null` iken mount edilir (`Dialog isOpen` her zaman `true`); kapatma `setOpenHit(null)` | Mount/unmount Dialog'un mevcut effect/cleanup'ıyla açma, kapama ve odak dönüşünü doğal yapar; arama state'i (`LastSearchProvider`) hiç değişmez → yeni arama yok | State'i `LastSearchProvider`'a koymak: arama bağlamını kirletir, sayfadan çıkınca da kalır |
| D13 | E2E'de beklenen metin REST'ten (`GET …/chunks/{id}`) alınır ve `node:crypto` md5'iyle panelin `innerText`'i karşılaştırılır; DB'den `select md5(content) from chunks where id = <id>` aynı md5'i verir | `psql -At -F '\|'` çıktısı satır sonu ve `\|` içeren metni güvenle taşıyamaz; md5 üç katmanı bağımsız bağlar. `innerText` `pre-wrap` olmadan satır sonlarını kaybeder, yani kural 3'ü de doğrular | `toContainText`: boşlukları normalize eder, satır sonu kaybını yakalamaz |

## Sözleşme değişiklikleri
`docs/api/openapi.yaml` (bu planla birlikte yapıldı; `info.version` 0.5.0 kalır, D2). Sadece açıklama; path, method, status, alan ve `errorCode` değişmedi:
1. `SearchHitResponse.content`: "Full text of the chunk, never shortened; the same value as `ChunkResponse.content` of this chunk. Line breaks are kept (`\n` inside a paragraph, `\n\n` between paragraphs). Plain text, no HTML."
2. `MatchedTermResponse.start`: offset'in tam alan metni (`heading` ya da önizleme değil bütün `content`) üzerinde olduğu yazıldı.
3. `getChunk` açıklaması: arama sayfasındaki parça panelinin parçanın hâlâ var olduğunu bu endpoint'le kontrol ettiği eklendi.

`docs/api/websocket.md`: yok, oluşturulmaz.

## Backend planı
Yok (bkz. "Çalışacak roller"). Yeni dependency yok.

## Frontend planı
Yeni paket yok. Yüklenecek skill'ler: `frontend-conventions`, `frontend-a11y`, `react-patterns`, `frontend-security` (parça içeriği), `frontend-design` (panel görünümü; lab `.dlg.chunk` referans).

### `shared/chunk/` (yeni, D4)
- `types.ts`: `ChunkResponse` (alanlar aynen `features/source/model/types.ts`'teki gibi; oradan silinir).
- `chunkApi.ts`: `getChunk(workspaceId: number, sourceId: number, chunkId: number): Promise<ChunkResponse>` (`sourceApi.ts`'ten taşınır, gövde aynı).
- `chunkQueries.ts`: `export const SOURCES_QUERY_SCOPE = 'sources'`; `chunkKey(workspaceId, sourceId, chunkId)` → `[SOURCES_QUERY_SCOPE, workspaceId, sourceId, 'chunk', chunkId] as const`; `useChunkQuery(workspaceId: number, sourceId: number | null, chunkId: number | null)` (bugünkü gövde + `staleTime: 0`).
- `chunkRules.ts`: `CHUNK_GONE_ERROR_CODES = ['CHUNK_NOT_FOUND', 'SOURCE_NOT_FOUND', 'WORKSPACE_NOT_FOUND'] as const`; `isChunkGoneError(error: unknown): boolean` → `error instanceof ApiError && error.status === 404 && CHUNK_GONE_ERROR_CODES` içeriyor.
- `features/source`: `sourceKeys.all` → `[SOURCES_QUERY_SCOPE, workspaceId] as const`; `sourceKeys.chunk` silinir; `SourceDetailPage`, `ChunkTable`, `sourceApi.ts` import'ları `shared/chunk/`'a döner. Re-export yazılmaz.

### `shared/components/Dialog.tsx` (D5, D6, D9, D10)
- Prop'lar: `title: ReactNode`; `size?: 'default' | 'wide'` (varsayılan `'default'`, `wide` → `styles.wide`); `hasCloseButton?: boolean` (varsayılan `false`; `true` ise başlık ile × düğmesi `.header` satırında, düğme `type="button"`, `aria-label="Paneli kapat"`, `disabled={!canClose}`, tıklayınca `onClose`); `closeOnBackdropClick?: boolean` (varsayılan `false`; `onClick`'te `target === currentTarget && canClose` → `onClose`); `returnFocusRef?: RefObject<HTMLElement | null>` (cleanup'ta `returnFocusRef?.current?.isConnected` ise ona, değilse `opener`'a `focus()`).
- Yapı: `<dialog>` → `<div className={styles.content}>` → (başlık ya da `.header`) + `children`. `aria-labelledby` h2'yi göstermeye devam eder.
- `Dialog.module.css`: `.dialog` padding 0, `overflow: hidden`, `[open]` → `display: flex; flex-direction: column`; `.content` → `flex: 1 1 auto; min-height: 0; overflow-y: auto; padding: var(--space-7); display: flex; flex-direction: column; gap: var(--space-6)`; `.wide` genişliği D10; `.header` (flex, `align-items: flex-start`, `justify-content: space-between`, gap); `.closeButton` (çerçevesiz, görünür `:focus-visible`). Mevcut iki diyaloğun görünümü değişmez (padding sarmalayıcıya taşındı).
- `index.css`: D7'deki iki kural.

### `features/search`
- `model/searchHitText.ts` (yeni function'lar; mevcutlar kalır): `hitTitleSegments(hit: SearchHitResponse): HighlightSegment[]` (heading yoksa `[{ text: UNTITLED_CHUNK_TITLE, isMatch: false }]`, varsa `toHighlightSegments(heading, matchedTerms, 'HEADING')`; `SearchHitItem`'daki inline hesap buraya taşınır), `hitSnippetSegments(hit)` (bugünkü liste: `toHighlightSegments(buildSnippet(content), …, 'CONTENT')`), `hitContentSegments(hit)` (tam metin: `toHighlightSegments(hit.content, hit.matchedTerms, 'CONTENT')`), `hitSourceLine(hit): string` → `` `${sourceName} · parça ${chunkNo} · #${chunkId}` ``.
- `components/SearchHitTags.tsx` + `.module.css`: `SearchHitTags({ matchedBy }: { matchedBy: SearchBranch[] })`; `SEMANTIC` → "anlam", `KEYWORD` → "kelime" span'ları (stil `SearchHitItem.module.css`'teki `.tag/.semantic/.keyword`'den taşınır). İkisi de yoksa `null`.
- `components/SearchHitItem.tsx`: prop'lar `{ hit: SearchHitResponse; onOpen: (hit: SearchHitResponse, opener: HTMLButtonElement) => void }` (`workspaceId` artık gerekmez). Kök `<button type="button" className={styles.hit} onClick={(event) => onOpen(hit, event.currentTarget)}>`; içerik bugünkü span'lar, `SearchHitTags`, `hitSourceLine`. CSS: `width: 100%`, `text-align: left`, `font: inherit`, `cursor: pointer`, görünür `:focus-visible`.
- `components/SearchHitList.tsx`: D12 state'i + `openerRef = useRef<HTMLButtonElement | null>(null)`; `handleOpen(hit, opener)` önce ref'i sonra state'i yazar; `openHit` varken `<SearchHitDialog workspaceId hit={openHit} returnFocusRef={openerRef} onClose={() => setOpenHit(null)} />`. Çift tıklama aynı state'i yazar, tek panel.
- `components/SearchHitDialog.tsx` + `.module.css`: prop'lar `{ workspaceId: number; hit: SearchHitResponse; returnFocusRef: RefObject<HTMLElement | null>; onClose: () => void }`. `<Dialog title={<HighlightedText segments={hitTitleSegments(hit)} />} isOpen onClose size="wide" hasCloseButton closeOnBackdropClick returnFocusRef>`. İçerik sırası: `.meta` satırı (`SearchHitTags` + `<small>` `hitSourceLine`), sonra ya gone mesajı (`<div role="alert"><Callout tone="warning">{messageForError(error)}</Callout></div>`) ya da metin bölgesi (`<div className={styles.text} role="region" aria-label="Parça metni" tabIndex={0}><HighlightedText segments={hitContentSegments(hit)} /></div>`), en altta `.actions`: "Kapat" düğmesi ve (gone değilse) `<Link className={button.primary} to={sourceDetailPath(workspaceId, hit.sourceId, hit.chunkId)}>Kaynakta göster</Link>`. `.text`: `flex: 1 1 auto; min-height: 0; overflow: auto; overscroll-behavior: contain; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55`; çerçeve ve zemin token'larla (lab `.ctext`). `dangerouslySetInnerHTML` yok.
- `SearchResults.tsx`: `SearchHitList`'e `workspaceId` geçmeye devam eder (panel için).

### `features/source`
- `components/ChunkTable.tsx`: `chunk.chunkId === highlightedChunkId` satırında `previewChunkContent` yerine `<span className={styles.fullText}>{chunk.content}</span>`; diğer satırlar aynı. `.fullText`: `display: block; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.5; color: var(--color-ink-soft)`.

### errorCode → mesaj
Yeni kod ve yeni metin yok (`errorMessages.ts` değişmez). Panelde gösterilenler: `CHUNK_NOT_FOUND` → "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.", `SOURCE_NOT_FOUND` → "Kaynak bulunamadı.", `WORKSPACE_NOT_FOUND` → "Çalışma alanı bulunamadı."

### Diğer
- `frontend/CLAUDE.md` "Klasör yapısı" `shared/` satırına `chunk/` (ChunkResponse, getChunk, useChunkQuery; iki feature'ın ortak parça kodu) ve "Tuzaklar"a: "Modal açıkken sayfa kaydırması `index.css`'teki `:root:has(dialog[open])` kuralıyla kilitlenir; diyaloğun padding'i `.content` sarmalayıcısındadır (dışına tıklama tespiti buna bağlı)."
- Bitirmeden: `npm run build`, `npm run lint`, `npm test`. Mevcut testlerden kırılanlar (link beklentisi, `ChunkResponse` import'u) tester'ındır; frontend-coder raporunda listeler.

## Test senaryoları
**Başlangıç sayıları (TASK-008 kapanışı):** frontend unit 27 dosya / 411-413 test (raporlar arasında fark var; tester ilk koşuda ölçer), E2E 18 geçti + 2 atlandı (`ollama-down.spec.ts`). Backend 432 (koşulmaz).

**Ortak kurallar:** `frontend-conventions` bölüm 11 (given_when_then, AAA, MSW + `problem(status, errorCode)`, `getByRole` önceliği, sabit bekleme yok). Fixture'lar `src/test/fixtures.ts`'teki `hit(...)`, `searchResult(...)`, `chunk(...)` (import `shared/chunk/types.ts`'e döner). Beklenen offset ve metinler elle yazılır; `toHighlightSegments`/`buildSnippet` beklenen değeri üretmek için kullanılmaz. Panel açan her test `GET /api/workspaces/:workspaceId/sources/:sourceId/chunks/:chunkId` isteği yapar (`onUnhandledFrame: 'error'`): `src/test/handlers.ts`'e 200 + `chunk(...)` dönen varsayılan handler eklenir; hata durumları `server.use` ile ezilir.

**Mevcut testlerde değişecekler:** `SearchResults.test.tsx` `givenHit_whenRendered_thenLinksToSourceDetailWithChunk` → SR1 ile değişir (artık link değil). `document-flow.spec.ts` `givenSearchHitOpened_whenSourceGetsNewVersion_thenChunkNotFoundNoticeAndHeadingKept` → EP4 olur. `hits(page).first().click()` kullanan diğer E2E adımları paneli açar; sonuç sırası/metni okuyan testler değişmez.

### Unit ve component
| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| HT1 | Tam metin segmentleri (K2, K4) | unit `searchHitText.test.ts` | `givenContentLongerThanSnippet_whenHitContentSegments_thenWholeContentWithMatchesAfterSnippetLimit` | `content = 'a '.repeat(150) + 'masraf beyanı sonu'` (300 + 18 karakter), terimler `CONTENT` 300-306, 307-313 → segment metinleri birleşince `content`'e eşit, `…` yok; `isMatch` olanlar `['masraf', 'beyanı']` |
| HT2 | Türkçe harf ve emoji sonrası offset (G4) | unit `searchHitText.test.ts` | `givenTurkishLettersAndEmojiBeforeTerm_whenHitContentSegments_thenMarkIsExactWord` | `content = 'İzin 😀 süresi: yıllık izin'` (27 UTF-16 birimi), terim `CONTENT` 23-27 → tek eşleşen segment `'izin'`; `hitTitleSegments` heading `null` → `[{ text: 'Başlıksız parça', isMatch: false }]` |
| DT1 | Dışına tıklama (K6) | component `shared/components/Dialog.test.tsx` (yeni) | `givenCloseOnBackdropClick_whenBackdropOrContentClicked_thenClosesOnlyOnBackdrop` | `user.click(dialog)` → `onClose` 1 kez; içerikteki paragrafa tıklama → `onClose` çağrılmaz |
| DT2 | Varsayılan davranış korunur | component `Dialog.test.tsx` | `givenDefaultProps_whenBackdropClicked_thenStaysOpenAndHasNoCloseButton` | `onClose` çağrılmaz; "Paneli kapat" düğmesi yok |
| DT3 | × düğmesi (K6) | component `Dialog.test.tsx` | `givenHasCloseButton_whenCloseButtonClicked_thenOnCloseCalled` | `onClose` 1 kez; `canClose={false}` iken düğme `disabled` |
| DT4 | Odak dönüşü (K7, D9) | component `Dialog.test.tsx` | `givenReturnFocusRef_whenDialogUnmounted_thenFocusMovesToRefElement` | Odak başka yerdeyken diyalog unmount → `document.activeElement` ref'teki düğme |
| DT5 | Tab döngüsü (K8) | component `Dialog.test.tsx` | `givenOpenDialog_whenTabOnLastAndShiftTabOnFirst_thenFocusWraps` | Son öğede Tab → ilk öğe; ilk öğede Shift+Tab → son öğe |
| HD1 | Tam metin, başlık, kaynak satırı (K2, K3) | component `SearchHitDialog.test.tsx` (yeni) | `givenLongMultiParagraphHit_whenOpened_thenShowsTitleSourceLineAndWholeContent` | `heading` "Yıllık izin ve diğer izinler", `sourceName` "Atlaskap-Lojistik-Surec-El-Kitabi", `chunkNo` 31, `chunkId` 1380, `content` 3 paragraf (`\n\n` ile), ~600 karakter, sonu "SON-CÜMLE." → `dialog` adı "Yıllık izin ve diğer izinler"; "Atlaskap-Lojistik-Surec-El-Kitabi · parça 31 · #1380" görünür; `region` "Parça metni" `textContent === content`; `…` içermez; odak "Paneli kapat"ta |
| HD2 | Başlıksız (K2) | component | `givenHitWithoutHeading_whenOpened_thenTitleIsUntitledChunk` | `dialog` adı "Başlıksız parça" |
| HD3 | Vurgu tam metinde (K4, S2) | component | `givenKeywordHitWithTermsBeyondSnippet_whenOpened_thenMarksAtAllOffsets` | `content = 'Masraf ' + 'x '.repeat(120) + 'masraf beyanı.'` (7 + 240 + 14), terimler 0-6, 247-253, 254-260 → bölgedeki `mark`'lar sırayla `['Masraf', 'masraf', 'beyanı']`; heading terimi `HEADING` 0-6 → başlıkta `mark` "Masraf" |
| HD4 | Etiketler (K5) | component | `givenMatchedBy_whenOpened_thenShowsMatchingTags` (`it.each`) | `['SEMANTIC','KEYWORD']` → "anlam" ve "kelime"; `['SEMANTIC']` → sadece "anlam"; `['KEYWORD']` → sadece "kelime" |
| HD5 | HTML metin olarak (K12) | component | `givenHtmlInContent_whenOpened_thenRenderedAsText` | `content = '<img src=x onerror="window.__xss=1"><script>window.__xss=2</script>'` → bölge `textContent` aynen bu dize; `dialog.querySelector('img, script')` `null`; `window.__xss` `undefined` |
| HD6 | Parça yok (K11, S4) | component | `givenChunkNotFound_whenOpened_thenShowsChunkGoneMessageAndHidesText` | MSW `GET …/chunks/1380` → `problem(404, 'CHUNK_NOT_FOUND')` → `alert` "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir."; "Parça metni" bölgesi yok; "Kaynakta göster" yok; "Kapat"a tıklama → `onClose` 1 kez |
| HD7 | Kaynak yok (K11) | component | `givenSourceNotFound_whenOpened_thenShowsSourceNotFoundMessage` | `problem(404, 'SOURCE_NOT_FOUND')` → `alert` "Kaynak bulunamadı." |
| HD8 | Kontrol başarısız (G3) | component | `givenChunkCheckFails_whenOpened_thenKeepsTextWithoutAlert` (`it.each`: `problem(500, 'INTERNAL_ERROR')`, `HttpResponse.error()`) | Handler çağrıldıktan sonra (`await waitFor` ile istek sayacı 1; test `QueryClient`'ında `retry: false`) bölge `textContent === content`; `alert` yok; "Kaynakta göster" var |
| HD9 | Kaynak bağlantısı (K9) | component | `givenOpenedHit_whenRendered_thenSourceLinkPointsToHighlightedChunk` | "Kaynakta göster" `href` `/w/42/kaynaklar/10001?parca=1380`; MSW `GET /api/workspaces/42/sources/10001/chunks/1380` tam 1 kez çağrıldı |
| HD10 | Kapatma düğmeleri ve Esc (K6) | component | `givenOpenedHit_whenClosedByEachControl_thenOnCloseCalled` (`it.each` "Paneli kapat", "Kapat", `{Escape}`, backdrop) | Her biri `onClose` 1 kez |
| HD11 | axe (AC4) | component | `givenOpenedHit_whenAxeRuns_thenNoViolations` (`it.each`: metin durumu, `CHUNK_NOT_FOUND` durumu) | `expect(await axe(container)).toHaveNoViolations()` |
| SR1 | Sonuç düğmedir (K1) | component `SearchResults.test.tsx` | `givenHit_whenClicked_thenOpensChunkDialogWithoutChangingLocation` | Sonuç `button`; tıklama → `dialog` açık; `currentUrl()` değişmez (`renderWithProviders(…, '/w/42/arama')` → `/w/42/arama`) |
| SP1 | Kapanınca sonuçlar, odak, arama yok (K7, AC5, S1) | integration `SearchPage.test.tsx` | `givenSearchResults_whenHitOpenedAndClosedWithEscape_thenResultsKeptFocusOnHitAndNoNewSearch` | 3 sonuçlu arama; MSW `POST …/search` sayacı 1; 1. sonuç açılır, Esc → `dialog` yok; arama kutusu değeri aynı; 3 sonuç aynı sırada; `document.activeElement` 1. sonuç düğmesi; `POST` sayacı hâlâ 1 |
| SP2 | Sadece klavye (K1, K8, S5) | integration `SearchPage.test.tsx` | `givenSearchResults_whenSecondHitOpenedWithKeyboard_thenFocusStaysInDialogAndReturns` | `user.tab()` ile 2. sonuca gelinir, `{Enter}` → `dialog` açık; 6 kez Tab → her adımda `activeElement` diyalog içinde ve sıra × → bölge → "Kapat" → "Kaynakta göster" → × …; Esc → odak 2. sonuçta |
| SP3 | Kapatma yolları sayfada (K6) | integration `SearchPage.test.tsx` | `givenOpenedHit_whenClosedWithCloseButtonsOrBackdrop_thenResultsStay` (`it.each` "Paneli kapat", "Kapat", backdrop) | `dialog` yok; sonuç listesi görünür; odak açan sonuçta |
| SP4 | "Kaynakta göster" (K9, K10, S3) | integration (tam uygulama, `renderApp('/w/42/arama')`) | `givenOpenedHit_whenSourceLinkClicked_thenSourceDetailWithFullHighlightedChunk` | MSW: arama 1380'i döner, `GET …/chunks/1380` 400 karakterlik, `\n` içeren içerik → tıklama sonrası yol `/w/42/kaynaklar/10001?parca=1380`; ilk satır vurgulu, tam metin öğesinin `textContent` 400 karakterlik içeriğe eşit; `…` yok |
| SP5 | Çift tıklama (G2) | integration `SearchPage.test.tsx` | `givenHit_whenDoubleClicked_thenSingleDialog` | `getAllByRole('dialog')` uzunluğu 1 |
| CT1 | Kaynak detayında tam metin (K10) | component `features/source/components/ChunkTable.test.tsx` (yeni) | `givenHighlightedLongChunk_whenRendered_thenHighlightedFullAndOthersPreview` | İki parça, ikisi de 400 karakter (vurgulu olanda `\n` var) → vurgulu satırda tam metin `textContent === content`; diğer satırda ilk 160 karakter + "…" (161 karakter) |
| CQ1 | Önbellek kapsamı (D4) | unit `shared/chunk/chunkQueries.test.ts` (yeni) | `givenCachedChunk_whenInvalidateSourceData_thenChunkQueryInvalidated` | `setQueryData(chunkKey(42, 10001, 1380), chunk(1380))`, `invalidateSourceData(queryClient, 42)` → `getQueryState(chunkKey(42, 10001, 1380))?.isInvalidated === true` |

### E2E (`document-flow.spec.ts`; EP1-EP3, örnek PDF 10001 hazır olduktan sonra ve EP4'ten önce, mevcut serial describe içinde)
| # | Senaryo | Test adı | Beklenen sonuç |
|---|---------|----------|----------------|
| EP1 | Ana akış (S1, S3, K1-K3, K7-K10, AC3, AC5, G1, G5) | `givenSampleSearch_whenHitOpenedAndClosed_thenFullTextShownResultsKeptAndNoNewSearchEvent` | "Anlam" modunda "Yıllık izin kaç gün?"; DB `select count(*) from search_events` = n. 1. sonuca tıklanır → `dialog` görünür, adres değişmez; panelin kaynak satırından `chunkId`; REST `GET …/sources/10001/chunks/<id>` (`ChunkResponse` şeması); `md5(region.innerText)` = `md5(REST content)` = DB `select md5(content) from chunks where id = <id>`; panel metninde "…" yok. Viewport 360×640: panel `boundingBox` ekran içinde; bölge `scrollHeight > clientHeight`, `scrollWidth <= clientWidth`; `page.mouse.wheel(0, 600)` sonrası `window.scrollY` değişmez. Esc → `dialog` gizli; 1. sonuç `toBeFocused()`; sonuç sayısı ve arama kutusu aynı; DB sayısı n. Yeniden açılır → "Kaynakta göster" → adres `/w/42/kaynaklar/10001?parca=<id>`; ilk satır vurgulu, tam metin öğesinin `textContent` md5'i aynı. `page.goBack()` → sonuç listesi görünür, `dialog` yok, DB sayısı n |
| EP2 | Kelime vurgusu sonda (S2, K4) | `givenKeywordSearch_whenHitOpened_thenTermsHighlightedBeyondSnippet` | "Kelime" modunda "masraf beyanı"; cevap `page.waitForResponse` ile alınır (`SearchResultResponse` şeması). Ön koşul assertion'ı: `CONTENT` terimlerinden biri `start >= 190` olan bir sonuç var (index i). O sonuca tıklanır → bölgedeki `mark` sayısı = o sonucun `CONTENT` terim sayısı (bitişik terim yoksa); son `mark`'ın metni = `content.slice(sonTerim.start, sonTerim.end)` |
| EP3 | Klavye gerçek tarayıcıda (S5, K8) | `givenKeyboardOnly_whenSecondHitOpened_thenFocusTrappedAndReturned` | Arama sonrası Tab'la 2. sonuç odaklanır, Enter → `dialog`; 8 kez Tab: her adımda `document.activeElement.closest('dialog')` dolu; Esc → 2. sonuç `toBeFocused()` |
| EP4 | Eski parça (S4, K11; mevcut test dönüştürülür) | `givenSearchHitShown_whenSourceGetsNewVersionBeforeOpening_thenPanelShowsChunkGoneMessage` | Önce mevcut F11 kontrolü korunur (`/w/42/kaynaklar/10001`: "Daha fazla göster" ile satır sayısı = DB `chunk_count`). Arama "Yıllık izin kaç gün?" → 1. sonucun `chunkId`'si okunur. Yeni sürüm `page.request` ile yüklenir (başka sekmeyi temsil eder; örnek PDF, `version` "2026.2"), DB'de iş `DONE` beklenir. Aynı sayfada 1. sonuca tıklanır → panelde `alert` "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir."; "Parça metni" ve "Kaynakta göster" yok; "Kapat" → `dialog` gizli, sonuç listesi yerinde. Sonra mevcut assertion'lar: `?parca=<id>` adresinde aynı mesaj; REST 404 `CHUNK_NOT_FOUND` (`ProblemDetail`); DB `chunks` o id için 0; `sources.version` "2026.2"; `search_events.top_chunk_heading` dolu |

### Komut listesi (T-ALL)
`cd frontend && npm run build`, `npm run lint`, `npm test`, `npm run test:coverage` (satır eşiği %80), sistem güncel imajla açıkken `npm run test:e2e`. Mutation: Stryker yok; tester `writing-good-tests` mutation check'ini elle yapar ve raporlar (özellikle `isChunkGoneError` dalları, `closeOnBackdropClick` koşulu, `returnFocusRef` dalı, `ChunkTable` vurgulu dalı).

## Deployment planı
Dosya değişikliği yok. E2E ve qa yeni site koduyla çalışmalıdır: tester E2E'den önce kök dizinde `docker compose -f deployment/docker-compose.yml up --build -d` çalıştırır (sadece site imajı değişir; veri silinmez, E2E `global-setup` zaten sıfırlar). Local alternatif: `E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npm run test:e2e` (dev server açık). İş sonunda sistem güncel imajla açık bırakılır.

## Açık sorular ve riskler
- **Kullanıcıya soru yok.** Belirsiz noktalar kartta "Benim verdiğim kararlar" altında.
- **EP2 veri bağımlılığı:** Örnek PDF'te "masraf beyanı" için 190. karakterden sonra eşleşme olduğu TASK senaryosundan varsayıldı. Ön koşul assertion'ı kırmızı olursa tester sorguyu örnek belgede bu koşulu sağlayan başka bir kelime öbeğiyle değiştirir ve raporlar; kural testi (HD3) bundan bağımsızdır.
- **Sözleşme açıklaması farkı:** Backend `@Schema` açıklamaları (`SearchHitResponse.content`, `MatchedTermResponse.start`) güncel `openapi.yaml` metninden kısa kalır; davranış ve şema aynı (D2). Bir sonraki backend task'ında hizalanabilir.
- **Kapsam dışı not:** `SearchHitItem.module.css`'teki etiket stilleri `SearchHitTags`'e taşınırken listenin görünümü değişmemeli; qa lab ile karşılaştırır.
- **Ortak Dialog değişikliği:** Padding'in `.content`'e taşınması `CreateSourceDialog` ve `UploadVersionDialog`'u etkiler; mevcut testleri yeşil kalmalı, qa iki diyaloğu gözle kontrol eder.

STATUS: DONE
