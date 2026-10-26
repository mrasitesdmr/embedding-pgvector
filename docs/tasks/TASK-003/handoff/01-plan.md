TASK: TASK-003
# Plan

## Özet
Mevcut durum (TASK-002 sonrası): backend REST API'si çalışıyor (çalışma alanları, kaynaklar, yükleme + iş durumu, parçalar, arama, arama kayıtları; sözleşme 0.2.0). `frontend/` altında sadece `CLAUDE.md` var: `package.json`, Vite iskeleti, test altyapısı yok. Docker Compose'da `postgres` ve `backend` var, `frontend` yok.

**Kullanıcı kararları (onay kartı 1. tur):** S1 = B: task bölündü. Dosya yükleme ve ilerleme (TASK Kural 30-34) ile "Yeni kaynak ekle" ve "Yeni sürüm yükle" butonları sonraki task'a kaldı. Kaynak listesi ve kaynak sayfası bu task'ta sadece görüntüleme için yapılır. S2 = A: bütün paketler eklenir, yazı tipleri Fontsource ile.

Yeni durum:
1. **Backend (küçük ekleme, davranış değişmez):** sözleşme 0.3.0.
   - Arama sonucuna `chunkNo`.
   - Arama kaydına istenen sonuç sayısı `limit` (migration `V7`, eski kayıtlarda null).
   - Çalışma alanına `embeddingModels`.
   - İki yeni endpoint: `GET .../search-events/stats` (göstergeler, Kural 39) ve `GET .../sources/{sourceId}/chunks/{chunkId}` (vurgulu parça; yeni `errorCode` `CHUNK_NOT_FOUND`).
2. **Frontend (sıfırdan):**
   - Vite + React 19 + TypeScript iskeleti, React Router, TanStack Query.
   - `lab.html` "yanıt." ürün ekranının tam pencere hali: başlık (logo + çalışma alanı seçici), yatay sekmeler.
   - Sayfalar: Arama, Arama kayıtları, Bilgi kaynakları (görüntüleme), Kaynak detayı (görüntüleme).
   - Test altyapısı: Vitest + RTL + MSW + vitest-axe, Playwright.
3. **Docker:** `frontend` servisi (Node build → nginx, `/api` proxy), host portu 3000. `ollama.md`'ye siteyi açma ve ilk arama bölümü.

Değişen mevcut parçalar (eski ad → yeni ad yok; sadece genişleme):
- `RetrievedHitView` + `getChunkNo()`, `RetrievedHit` + `chunkNo`, `SearchHitResponse` + `chunkNo` (`chunkId`'den hemen sonra).
- `SearchEvent` + `resultLimit` alanı (kolon `result_limit`), `SearchEvent.record(...)` + `int resultLimit` parametresi; `SearchEventResponse` + `limit`.
- `WorkspaceResponse` + `embeddingModels`, `WorkspaceResponse.from(WorkspaceSummary, List<String>)`.
- `SearchProperties` + `ZoneId statsTimeZone`.
- `OpenApiConfig`: `API_VERSION = "0.3.0"`, `ERROR_CODES`'a `CHUNK_NOT_FOUND` (`SOURCE_NOT_FOUND`'dan sonra; sözleşmedeki sırayla 21 kod).
- Var olan testlerde bu record'ların constructor'larını kullananlar (ör. `WorkspaceControllerTest`) tester tarafından yeni alanlarla güncellenir.

Uygulama sırası: backend ve frontend paralel. Frontend içinde: iskelet + paylaşılan katman (httpClient, hata mesajları, workspace, layout, router) → Arama → Arama kayıtları → Bilgi kaynakları → Kaynak detayı. Ardından tester (component + backend testleri + E2E), devops (Docker + rehber), qa.

### Sonraki task'a kalanlar (S1 = B)
- TASK Kural 30, 31, 32, 33, 34 (yeni kaynak ekleme, yeni sürüm yükleme, ilerleme ve polling, yenileme sonrası devam, iş sürerken pasif buton).
- Kural 35'teki "Yeni sürüm yükle" butonu; Kural 43'ün diyalog kısmı (odak, Esc, odak dönüşü); Kural 1'deki diyalog ve bildirim (toast) bileşenleri (bu task'ta kullanılan bir diyalog ya da toast yok).
- Örnek senaryolar 7, 8, 9 ve 10'un yükleme kısmı; "yeni sürüm yükleme ve ilerleme" E2E kabul kriteri.
- Sözleşmede süren işi bulmak için `SourceResponse.latestJob` (D1). Bu task'ta sözleşmeye eklenmez; sonraki task'ın planı ekler.

## Çalışacak roller
- **planner**: bu plan, sözleşme (`docs/api/openapi.yaml` 0.3.0).
- **backend-coder**: `V7` migration, yeni alanlar ve iki endpoint, `OpenApiConfig`, `backend/CLAUDE.md`.
- **frontend-coder**: `frontend/` iskeleti, `package.json` (bütün paketler, test araçlarının config'leri dahil), bütün `src/**` (testler hariç), `index.html`, `frontend/CLAUDE.md`.
- **tester**: backend testleri (`backend/src/test`), frontend component testleri ve `src/test/**`, Playwright testleri (`frontend/e2e/**`).
- **devops**: `frontend/Dockerfile`, `frontend/.dockerignore`, `frontend/nginx.conf`, `deployment/docker-compose.yml`, `deployment/CLAUDE.md`, `ollama.md` (TASK-002 D24 ile aynı: rehberi çalıştırarak yazan rol).
- **qa**: kural kontrolü, sözleşme uyumu (backend `/v3/api-docs.yaml` ve frontend type'ları), çalışan sistemde tarayıcıdan bu task'taki örnek senaryolar.
- Atlanan rol yok. WebSocket yok (TASK kapsam dışı), `docs/api/websocket.md` açılmaz.

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | Başlık (logo → Arama, çalışma alanı seçici), yatay sekmeler, içerik; renk/yazı/tablo/rozet `lab.html`'den. Diyalog ve toast sonraki task'ta | `app/WorkspaceLayout`, `app/AppHeader`, `app/SectionTabs`, `index.css` token'ları, `shared/components/*` | F10, F41, F42; qa Q-UI |
| K2 | Sahte tarayıcı çerçevesi, Sistem konsolu, senaryolar yok | Taşınmaz | qa Q-UI |
| K3 | Her sayfanın adresi, adreste çalışma alanı; yenileme/yeni sekme aynı sayfa; geri/ileri | Router (D10), `BrowserRouter`; nginx SPA fallback | F10, F20, E1; qa Q0, Q5 |
| K4 | Kimlik doğrulama yok, kullanıcı adı yok | `AppHeader` | qa Q-UI |
| K5 | Türkçe metinler, "yanıt.", "Bilgi bankası", "RAG"/"lab" yok | Bütün UI metinleri | F10; qa Q-UI (metin taraması) |
| K6 | Hatalar `errorCode`'a göre Türkçe; ham metin yok; backend'e ulaşılamazsa ayrı mesaj | `shared/api/httpClient`, `ApiError`, `errorMessages.ts`, `ErrorAlert` | F7, F8, F24 |
| K7 | Parça içeriği, kaynak adı, arama metni düz metin | `dangerouslySetInnerHTML` yok; vurgulama segment dizisiyle (D18) | F25 |
| K8 | Seçici: ad, kaynak sayısı, parça sayısı; varsayılan 42 | `app/WorkspaceSelect`, `shared/workspace/workspaceRules.ts` | F10, F12 |
| K9 | Alan değişince bütün sayfalar yeni alan; eski arama sonucu kalmaz | Route param + query key'ler alan id'li; `LastSearchProvider key={workspaceId}` (D9) | F12, F13, E3 |
| K10 | Olmayan alan / kaynak → "bulunamadı" ekranı + dönüş bağlantısı | `WorkspaceLayout` (`WORKSPACE_NOT_FOUND`, geçersiz id), `SourceDetailPage` (`SOURCE_NOT_FOUND`), `NotFoundState` | F11, F39, E4 |
| K11 | "Bilgi bankası" başlığı + açıklamada alan adı; kutu + "Ara"; Enter | `SearchPage`, `SearchForm` | F14 |
| K12 | Boş/boşluk → arama yok, buton pasif; en fazla 500 | `searchRules.canSearch`, input `maxLength=500` | F1, F14 |
| K13 | Arama sürerken buton pasif, "aranıyor…" + iskelet; ikinci arama yok | `useSearchChunksMutation().isPending` | F15 |
| K14 | "Sık aranan" 3 sorgu, tıklanınca arama | `SearchSuggestions`, `SUGGESTED_QUERIES` | F16 |
| K15 | Bilgi satırı: yöntem, en fazla sonuç, embedding modeli | `SearchInfoLine` (`WorkspaceResponse.embeddingModels`) | F17 |
| K16 | Gelişmiş ayarlar paneli: 5 ayar, varsayılanlar, Kelime'de pasifler, geçersizde uyarı, sıfırlama, tarayıcıda hatırlama | `SearchSettingsPanel`, `useSearchSettings`, `searchSettings.ts` (D8) | F2, F9, F18 |
| K17 | "Henüz arama yapılmadı" | `SearchPage` | F10 |
| K18 | Sonuç: sıra, başlık, anlam/kelime etiketi, kısa içerik, kaynak adı + parça no + id; kelime vurgusu; başlıkta metin, sayı, süre | `SearchHitList`, `SearchHitItem`, `searchHitText.ts`, `SearchResultHeader`; backend `chunkNo` | F3, F4, F19, B1 |
| K19 | Sonuç yok boş durumu; Kelime'de Anlam/Hybrid önerisi | `SearchResultEmpty`, `searchNotices.ts` | F21 |
| K20 | Vektör kolu kısa kaldı uyarısı + iterative önerisi | `SearchResultHeader` (`vectorShort`) | F22 |
| K21 | Teknik detay (arama düzeyi, sonuç başına, sorgu planı), "—" | `SearchTechnicalDetails` | F23 |
| K22 | Sonuca tıkla → kaynak detayı; parça en üstte vurgulu + bilgi | Link `?parca=<chunkId>`, `SourceDetailPage`, `useChunkQuery`, yeni `getChunk` endpoint'i (D4) | F20, F38, B11, E1 |
| K23 | Kanal "Temsilci paneli" | `SEARCH_CHANNEL` | F14, E5 |
| K24 | Model uyuşmazlığı → neden + Kelime önerisi; Ollama yok / model yok ayrı mesaj | `errorMessages.ts` | F24; qa Q10 |
| K25 | Başlık, açıklama, 3 gösterge backend'de; gün Europe/Istanbul; son 7 gün oranları; veri yoksa "—" | Backend `getSearchEventStats` (D2); `SearchEventStats` | B3-B7, F26 |
| K26 | Tablo sütunları, rozet renkleri, "—", yeniden eskiye, 20'şer | `SearchEventTable`, `resultCountTone`, `Pagination`; backend `SearchEventResponse.limit` | F5, F26, F27, B2 |
| K27 | Sekmede toplam kayıt sayısı; yeni aramada güncellenir | `SectionTabs` (`stats.totalCount`), mutation `onSuccess` invalidate | F28, E5 |
| K28 | "Alan › Bilgi kaynakları" yol gösterici, başlık, açıklama | `SourcesPage` | F29 |
| K29 | Tablo sütunları, Türkçe tür, durum rozeti (Hazır/İşleniyor/Başarısız), alt satırda toplam parça + model, satır → detay | `SourceTable`, `SourceRow`, `sourceRules.ts` etiketleri (D20) | F29 |
| K30-K34 | Yeni kaynak, yeni sürüm, ilerleme, yenileme sonrası devam, iş sürerken pasif buton | **Sonraki task** (S1 = B) | — |
| K35 | Detay: "Bilgi kaynakları › Ad", ad. "Yeni sürüm yükle" butonu sonraki task'ta | `SourceDetailPage` | F37 |
| K36 | Bilgi kutuları: tür, sürüm, dosya adı, parça sayısı, embedding modeli, son senkron | `SourceInfoBoxes` | F37 |
| K37 | Parça tablosu: no, başlık + önizleme (+ id), token; 20 + "Daha fazla göster" | `ChunkTable`, `useChunksInfiniteQuery` | F37 |
| K38 | Arama sonucundan gelindiyse Kural 22 | `?parca=` | F38 |
| K39 | Göstergeler için yeni endpoint, sözleşmede | `GET /api/workspaces/{workspaceId}/search-events/stats` | B3-B6, B13 |
| K40 | Eksik alanlar sözleşmeye + backend'e; var olan davranış değişmez | `chunkNo`, `limit`, `embeddingModels` (Kural 33'ün alanı sonraki task'ta) | B1, B2, B10, B14; mevcut backend testleri; qa sözleşme karşılaştırması |
| K41 | Compose'a frontend; `/api` backend'e; `npm run dev` + Vite proxy | `frontend/Dockerfile`, `nginx.conf`, compose, `vite.config.ts` proxy | devops raporu; qa Q0 |
| K42 | `ollama.md`'ye siteyi açma + ilk arama | devops | qa rehberi sırayla dener |
| K43 | Klavyeyle sekmeler, arama, ayarlar, sonuçlar; form alanlarının etiketi. Diyalog kısmı sonraki task'ta | Native `<details>`, `<select>`, `<a>` (D12) | F18, F40, F41; qa Q-KB |
| S-1 | Docker ile açılış, Dalga seçili, "Henüz arama yapılmadı", kayıt sayısı 0 | Router + stats | F10; qa Q1 |
| S-2 | Parola: Hybrid'de "Online İşlemler parolanı sıfırlama" ilk 10'da "anlam"; Kelime'de sonuç yok + öneri | Arama | F21, E1; qa Q2 |
| S-3 | TS-999: hem kelime hem anlam, en üstte, "TS-999" vurgulu; teknik detayda sıra, mesafe, RRF | Arama | F19, F23; qa Q3 |
| S-4 | Nakil post-filter + ef 40 → <10 + uyarı; iterative → 10; plan HNSW | Arama | F22, F23; qa Q4 |
| S-5 | Sonuca tıkla → detay, parça en üstte vurgulu; geri → sonuçlar | D4, D9 | F38, E1; qa Q5 |
| S-6 | Arama kayıtları: 4 arama, kanal, rozetler, Bugün 4, sonuçsuz %25 | Stats + tablo | B3, F26; qa Q6 |
| S-7, S-8, S-9 | Yükleme senaryoları | **Sonraki task** | — |
| S-10 | Ollama kapalı: Hybrid "ulaşılamıyor", Kelime çalışır. Yükleme kısmı sonraki task'ta | `errorMessages` | F24; qa Q10 |
| S-11 | Kıyı Net'e geçiş: adres, kaynaklar, arama sadece Kıyı Net | K9 | F12, E3; qa Q11 |
| S-12 | Olmayan alan adresi → "Çalışma alanı bulunamadı" | K10 | F11, E4; qa Q12 |
| AC-1 | `npm run build`, `npm run lint`, `npm test`, `./mvnw verify` | Tüm kod | tester + qa |
| AC-2 | Vitest + RTL + MSW; Playwright gerçek sisteme; E2E: hybrid + kaynağa gitme, alan değiştirme (yeni sürüm + ilerleme sonraki task'ta) | Test altyapısı | E1, E3 |
| AC-3 | Compose: `postgres`, `backend`, `frontend` healthy; site açılıyor | devops | qa Q0 |
| AC-4 | Bu task'taki örnek senaryoların hepsi tarayıcıdan, qa raporunda | qa | Q1-Q6, Q10-Q12 |
| AC-5 | Yeni/değişen endpoint'ler sözleşmede; backend ve frontend birebir | `openapi.yaml` 0.3.0 | B13; qa: `/v3/api-docs.yaml` karşılaştırması + `model/types.ts` alanları |
| AC-6 | Sözleşmedeki bütün `errorCode`'ların Türkçe karşılığı | `errorMessages.ts` | F7 |
| AC-7 | `lab.html` görsel dili, tam pencere, çerçeve/konsol yok | `index.css`, layout | qa Q-UI (ekran görüntüsü) |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | Arama sonucundaki parça, kaynağın yeni sürümüyle silinmiş (eski sonuçtan ya da eski adresten açılıyor) | Detay sayfası açılır; vurgulu parça yerine "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir." bilgisi; parça listesi normal | F38, B11 |
| G2 | Kaynak API ya da Swagger ile yüklenirken site açık (bu task'ta yükleme ekranı yok) | Satırda "İşleniyor" rozeti; ilerleme çubuğu yok, liste kendiliğinden yenilenmez; sayfa yenilenince yeni durum görünür | F29 |
| G3 | URL'deki alan id'si sayı değil ya da ≤ 0 (`/w/abc/arama`, `/w/0/arama`) | İstek atılmadan "Çalışma alanı bulunamadı" ekranı | F11 |
| G4 | 0.3.0'dan önce yazılmış arama kaydı (`limit` null) | Tabloda sonuç > 0 ise yeşil rozet; "Az sonuç dönen" oranına girmez; sonuçsuzsa "Sonuçsuz" | F5, B3, B14 |
| G5 | Kaynak adı, parça başlığı ve sorguda Türkçe karakter ve HTML benzeri metin | Olduğu gibi düz metin gösterilir; vurgu konumları Türkçe harflerde kaymaz | F4, F25 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | (S1 = B sonrası) `SourceResponse.latestJob` bu task'ta sözleşmeye eklenmez; sonraki (yükleme) task'ının planı ekler | Alanın tek tüketicisi yükleme ilerlemesi ve son yükleme hatası; bu task'ta onları gösteren ekran yok. Kullanılmayan alan backend'e kod ve test yükü getirir | Şimdiden eklemek: kullanılmayan alan; sonraki task'ta şekli değişirse sözleşme iki kez değişir |
| D2 | Göstergeler `GET .../search-events/stats` → `SearchEventStatsResponse` (sayılar + oranlar + `timeZone`, `todayStart`, `last7DaysStart`). Günler `search.stats-time-zone=Europe/Istanbul` takvim günleri; "son 7 gün" = bugün + önceki 6 gün (00:00'dan). Oranlar yuvarlanmaz (0-1), ekranda `%` biçimi frontend'de. Sınırlar `Clock` + zone ile Java'da hesaplanır, sorgu tek native SQL (`count(*) FILTER (...)`) | "Bugün" tanımıyla tutarlı; sınırlar yanıtta olduğu için test ve qa elle doğrular | Kayan 168 saat: "Bugün" takvimle, "son 7 gün" saatle farklı mantıkta olurdu; oranı backend'de yüzdeye yuvarlamak: biçim kararı UI'ın |
| D3 | `search_events.result_limit integer NULL` (V7), eski satırlar null kalır (doldurulmaz). Yeni kayıtlarda her zaman dolu | Eski aramaların gerçek `limit`'i bilinmiyor; uydurmak göstergeyi yanıltır | `DEFAULT 10` ile doldurmak: qa'nın TASK-002'de yaptığı farklı `limit`'li aramalar yanlış rozet alır |
| D4 | Vurgulu parça için yeni `GET .../sources/{sourceId}/chunks/{chunkId}` (`ChunkResponse`, `CHUNK_NOT_FOUND`). Adres `?parca=<chunkId>` (lab'daki gibi) | Kural 3: sayfa yenilenince/yeni sekmede de aynı vurgu; parça listenin 20'lik ilk sayfasında olmayabilir | Router `location.state` ile hit verisini taşımak: yenilemede kaybolur; parçayı bulana kadar sayfaları gezmek: 200+ parçalı kaynakta çok istek |
| D5 | `WorkspaceResponse.embeddingModels` (alan parçalarının farklı etiketleri, artan sıralı, parça yoksa boş). Bilgi satırı (K15) ve kaynak tablosu altı (K29) `join(', ')`, boşsa "—". Kaynak detayındaki model (K36) ilk parçanın `embeddingModel`'i, parça yoksa "—" | Gösterilen model gerçekten parçaları yazan model; uyuşmazlıkta iki etiket görünür | Yapılandırılan modeli dönen yeni endpoint: parçalarla uyuşmazlığı gizler; `SourceResponse.embeddingModel`: parça listesinde zaten var |
| D6 | Çalışma alanı kodu `shared/workspace/` altında (`types.ts`, `workspaceApi.ts`, `workspaceQueries.ts`, `workspaceRules.ts`, `useCurrentWorkspace.ts`); `features/workspace` açılmaz | Her feature (search, source) ve layout geçerli alanı okur; feature'lar birbirini import edemez | `features/workspace` + outlet context: tip yine paylaşılmak zorunda |
| D7 | Feature'lar: `search` ve `source`. `ingestion` feature'ı bu task'ta açılmaz (yükleme sonraki task'ta) | İhtiyacı olmayan klasör boş açılmaz | — |
| D8 | Arama ayarları `features/search/model/searchSettings.ts` (`readSearchSettings()`, `writeSearchSettings(settings)`), localStorage anahtarı `emb:search-settings:v1`; okunan değer şekil ve aralık kontrolünden geçer, bozuk alan varsayılana döner; `try/catch`. Sadece geçerli değerler yazılır; panelin açık/kapalı hali saklanmaz | `frontend-conventions` §9 kalıbı; tipler `search` feature'ında olduğu için `shared/preferences/labPreferences.ts` feature tipini import edemez; "lab" kelimesi ürün dışı | `shared/preferences/labPreferences.ts`: shared → feature import'u yasak |
| D9 | Son arama (istek + sonuç) `features/search/components/LastSearchProvider.tsx` context'inde; `WorkspaceLayout` içinde `key={workspaceId}` ile sarılır. Sayfalar arası gezinmede ve geri butonunda kalır; yenilemede ve alan değişince silinir. Adrese `?q=` yazılmaz, yenileme aramayı tekrar çalıştırmaz | S-5 (geri → sonuçlar), K9; yenilemede otomatik arama arama kaydı ve "Bugün" sayısını şişirir | `?q=` + yüklemede yeniden arama: her yenilemede yeni kayıt; mutation state'i sayfada: detaydan dönünce kaybolur |
| D10 | Adresler lab'daki gibi Türkçe, alan id'siyle: `/` → `/w/42/arama`; `/w/:workspaceId/arama`, `/w/:workspaceId/arama-kayitlari`, `/w/:workspaceId/kaynaklar`, `/w/:workspaceId/kaynaklar/:sourceId?parca=<chunkId>`; `/w/:workspaceId` → `arama`; bilinmeyen adres → "Sayfa bulunamadı" | TASK Kural 3 lab adresini örnek veriyor (convention'daki `/workspaces/...` örneğinden önce gelir); API'de slug yok | Slug (`/w/dalga`): API'de alan slug'ı yok, ek alan gerekir |
| D11 | Çalışma alanı seçici native `<select>` (etiket "Çalışma alanı", görsel olarak gizli); seçenek metni `"Dalga Fiber · 4 kaynak · 42 parça"`. Değişince: Arama → yeni alanın Arama'sı, Arama kayıtları → aynı sayfa, Bilgi kaynakları ve Kaynak detayı → yeni alanın Bilgi kaynakları'sı | Klavye ve ekran okuyucu desteği hazır; kaynak başka alana ait olduğu için detayda kalınamaz | Özel açılır liste: a11y işi büyük, TASK görünüm şartı koymuyor |
| D12 | Gelişmiş ayarlar ve Teknik detay native `<details>/<summary>`; sekmeler `<nav aria-label="Bölümler">` içinde `NavLink` (`aria-current="page"`), ARIA tab rolü değil. Diyalog bileşeni bu task'ta yazılmaz | Ayrı sayfalar oldukları için sekmeler bağlantıdır; native öğeler K43'ü kendiliğinden karşılar; bu task'ta diyalog kullanan ekran yok | ARIA `tablist`: sayfa geçişi için yanlış rol |
| D13 | Polling yok (yükleme sonraki task'ta). Kaynak listesi ve detayı açılışta bir kez yüklenir; TanStack Query varsayılan yenilemeleri dışında kendiliğinden güncellenmez | Bu task'ta süren işi gösteren ekran yok; "İşleniyor" rozeti sayfa yenilenince güncellenir (G2) | `INGESTING` kaynak varken listeyi periyodik yenilemek: sonraki task'ın polling tasarımıyla çakışır |
| D14 | `shared/api/httpClient.ts`: `request<T>(path, init)` JSON istekleri için `Content-Type: application/json` ekler. Hata: `application/problem+json`'dan `errorCode` + `violations`; yoksa `UNEXPECTED_ERROR`; `fetch` reddi → `NETWORK_ERROR`. Multipart desteği sonraki task'ta eklenir | Bu task'taki istekler JSON ya da gövdesiz GET | — |
| D15 | Arama isteği: her zaman `query`, `mode`, `limit`, `channel: 'Temsilci paneli'`; `mode !== 'KEYWORD'` ise ayrıca `filterStrategy`, `efSearch`, `explain`. Frontend varsayılanları `searchRules.ts`'te (HYBRID, ITERATIVE_SCAN, 40, 10, false) | Backend varsayılanlarına güvenmek yerine ekrandaki ayar tam olarak gönderilir; bilgi satırı ile istek aynı | Boş alanları backend'e bırakmak: backend varsayılanı değişirse ekran yanlış bilgi gösterir |
| D16 | Sorgu kuralı backend ile aynı: kırpıldıktan sonra 1-500 karakter (`QUERY_MAX_LENGTH = 500`); input `maxLength=500`. Kırpma `trim()`; iç boşluk korunur (backend `strip()` ile aynı) | K12; convention'daki `QUERY_MIN_LENGTH = 2` örneği backend'in 1'iyle çelişir, backend doğruluk kaynağı | 2 karakter alt sınır: "5" gibi geçerli backend araması engellenir |
| D17 | (S2 = A) Yazı tipleri `@fontsource/source-sans-3` (400, 500, 600, 700) ve `@fontsource/jetbrains-mono` (400, 500) paketlerinden, `main.tsx`'te import | Kendi sunucumuzdan: internetsiz çalışır, dışarıya istek yok, CSP sade | Google Fonts bağlantısı: kullanıcı A'yı seçti |
| D18 | Snippet ve vurgu (lab `snippet()`): `content.length > 190` ise ilk 188 karakter içindeki son boşluğa kadar + `" …"`; vurgu `matchedTerms` (`CONTENT`) konumlarıyla, snippet dışına taşan terim atılır; başlıkta `HEADING` terimleri. Metin `{ text, isMatch }[]` segmentlerine bölünür, eşleşenler `<mark>` | K7: HTML üretilmez; konumlar backend'den (UTF-16, JS `String` indeksiyle aynı) | Frontend'de kök bulma: Türkçe stemmer yok, backend zaten veriyor |
| D19 | Kaynak satırında ve detayında "Yeni sürüm yükle" butonu, sayfada "Yeni kaynak ekle" butonu yok (sonraki task) | S1 = B; işlevsiz buton kullanıcıyı yanıltır | Pasif buton göstermek: neden pasif olduğu anlaşılmaz |
| D20 | Durum rozeti kaynağın kendi `status`'u: `READY` → "Hazır" (yeşil), `INGESTING` → "İşleniyor" (sarı, dönen ikon; `prefers-reduced-motion`'da durur), `FAILED` → "Başarısız" (kırmızı). Satırda aşama metni, ilerleme çubuğu ve "Son yükleme başarısız: <neden>" yazısı yok (sonraki task; neden bilgisi `latestJob` ister) | K29 rozeti bu task'ta tam; neden ve ilerleme yükleme akışının parçası | — |
| D21 | Biçimler `shared/format/`: sayılar `tr-TR` (`1.410`), oran `Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 1 })` (`%25`, `%6,8`), null → `"—"`; skor/mesafe/RRF 4 ondalık (`0,1234`); süre `"57 ms"`; saat (Europe/Istanbul) bugünse `HH:mm`, değilse `"24 Eki 14:05"`; son senkron `"12 Eki 2026 14:31"` | Lab'daki biçimler; saat dilimi göstergelerle aynı | Tarayıcı saat dilimi: göstergeler İstanbul'a göre, tablo farklı gün gösterebilir |
| D22 | Sonuç başlığı `heading ?? 'Başlıksız parça'`; kaynak detayındaki parça satırında da aynı | Seed parçalarının hepsinde başlık var; yüklenen Markdown'da ilk başlıktan önceki metin başlıksız | İçeriğin başını başlık yapmak: içerik zaten altında |
| D23 | Toast bileşeni bu task'ta yazılmaz | Tek kullanımı yükleme bitişi (sonraki task) | — |
| D24 | TanStack Query varsayılanları (`AppProviders`): `retry` 4xx `ApiError`'da yok, diğerlerinde 2; `staleTime` 30 sn; `refetchOnWindowFocus: false` | 404'ü tekrar denemek "bulunamadı" ekranını geciktirir | Varsayılan 3 retry: 404'te ~7 sn bekleme |
| D25 | E2E (Playwright, sadece Chromium) gerçek sisteme: tester, compose'daki `postgres` + `backend` ve `npm run dev` (5173) ile çalıştırır; devops ve qa compose'daki `frontend`'e (3000) karşı tekrar çalıştırır (`E2E_BASE_URL`). E2E veri değiştirmez (sadece arama yapar; arama kaydı ekler) | Tekrarlanabilir koşu | — |
| D26 | Node sürümü: frontend-coder makinedeki `node --version` ana sürümünü `frontend/CLAUDE.md`'ye ve `package.json` `engines.node`'a yazar; devops aynı ana sürümün `node:<ana>-alpine` imajını kullanır. Runtime imajı `nginxinc/nginx-unprivileged:<1.29.x>-alpine` (root değil, 8080) | Geliştirme ve build aynı Node; deployment kuralı root olmayan container | `nginx:alpine`: root ile çalışır |
| D27 | Mutation testi (Stryker) bu task'ta kurulmaz | Kabul kriterlerinde yok; dependency sayısı zaten büyük | — |

## Sözleşme değişiklikleri
`docs/api/openapi.yaml` (`info.version` 0.2.0 → **0.3.0**). Var olan endpoint'lerin path, method, status ve hata davranışı aynı; sadece yanıtlara alan eklendi.
- **Yeni path'ler:**
  - `GET /api/workspaces/{workspaceId}/search-events/stats` (`getSearchEventStats`, tag Search) → 200 `SearchEventStatsResponse`; 404 `WORKSPACE_NOT_FOUND`; 500.
  - `GET /api/workspaces/{workspaceId}/sources/{sourceId}/chunks/{chunkId}` (`getChunk`, tag Sources) → 200 `ChunkResponse`; 404 `WORKSPACE_NOT_FOUND` / `SOURCE_NOT_FOUND` / `CHUNK_NOT_FOUND`; 500.
- **Yeni şema:** `SearchEventStatsResponse` (`totalCount`, `todayCount`, `last7DaysCount`, `zeroResultCount`, `lowResultCount` int64; `zeroResultRate`, `lowResultRate` `[number, 'null']`; `timeZone` string; `todayStart`, `last7DaysStart` date-time; oranlar dışında hepsi `required`).
- **Eklenen alanlar:**
  - `WorkspaceResponse.embeddingModels: string[]` (required).
  - `SearchHitResponse.chunkNo: int32` (required, `chunkId`'den sonra).
  - `SearchEventResponse.limit: [integer, 'null']` (`filterStrategy`'den sonra).
- **Yeni `errorCode`:** `CHUNK_NOT_FOUND` (`ProblemDetail.errorCode` enum'unda `SOURCE_NOT_FOUND`'dan sonra; toplam 21).
- **Eklenip geri alınan (S1 = B):** `SourceResponse.latestJob` ilk taslakta vardı, kullanıcının bölme kararıyla kaldırıldı (D1). Sürüm 0.3.0 kalır, çünkü yukarıdaki değişiklikler hâlâ var.
- qa karşılaştırmada path, method, status, `errorCode`, alan adları, `required` listelerine bakar (TASK-002 kuralı).
- `docs/api/websocket.md` yok.

## Backend planı
Yüklenecek skill'ler: `backend-conventions`, `jpa-patterns`, `flyway-migrations`, `postgres-patterns`, `spring-boot-openapi-documentation`, `problem-details-rfc9457`, `springboot-patterns`.
Yeni dependency yok.

### Migration
- `V7__add_result_limit_to_search_events.sql`:
  - `ALTER TABLE search_events ADD COLUMN result_limit integer NULL;`
  - `ADD CONSTRAINT ck_search_events_result_limit CHECK (result_limit IS NULL OR result_limit BETWEEN 1 AND 50);`
  - Veri güncellemesi yok (D3). Index eklenmez: göstergeler mevcut `idx_search_events_workspace_id_created_at` ile çalışır.

### `application.properties`
- `search.stats-time-zone=Europe/Istanbul`

### Sınıflar
| Sınıf | Değişiklik ve imza |
|-------|--------------------|
| `SearchProperties` | + `@NotNull ZoneId statsTimeZone` (record'un sonuna) |
| `RetrievedHitView` | + `int getChunkNo()` |
| `ChunkSearchRepository` | `searchKeyword`, `searchVector`, `searchHybrid` SELECT'lerine `c.chunk_no AS "chunkNo"` (`"chunkId"`'den sonra). Başka değişiklik yok |
| `RetrievedHit` | + `int chunkNo` (`chunkId`'den sonra); `ChunkRetrievalService` eşlemesi |
| `SearchHitResponse` | + `@Schema(requiredMode = REQUIRED, description = ...) int chunkNo` (`chunkId`'den sonra); `SearchService.toHits` doldurur |
| `SearchEvent` | + `@Column(name = "result_limit") @Nullable Integer resultLimit`. Fabrika: `static SearchEvent record(long workspaceId, Instant createdAt, @Nullable String channel, String query, SearchMode mode, @Nullable FilterStrategy filterStrategy, @Nullable Integer efSearch, int resultLimit, int resultCount, @Nullable Long topChunkId, @Nullable String topChunkHeading, long totalMs)`. `SearchService.saveSearchEvent` `response.limit()` geçer |
| `SearchEventResponse` | + `@Schema(types = {"integer", "null"}, format = "int32") @Nullable Integer limit` (`filterStrategy`'den sonra), `from` doldurur |
| `SearchEventStatsView` | `search/repository`, interface projection: `long getTotalCount()`, `long getTodayCount()`, `long getLast7DaysCount()`, `long getZeroResultCount()`, `long getLowResultCount()` |
| `SearchEventRepository` | + `SearchEventStatsView countStats(long workspaceId, Instant todayStart, Instant last7DaysStart)`, `@Query(nativeQuery = true)`. SQL (`FROM search_events WHERE workspace_id = :workspaceId`, alias'lar çift tırnaklı camelCase): `count(*) AS "totalCount"`; `count(*) FILTER (WHERE created_at >= :todayStart) AS "todayCount"`; `count(*) FILTER (WHERE created_at >= :last7DaysStart) AS "last7DaysCount"`; `count(*) FILTER (WHERE created_at >= :last7DaysStart AND result_count = 0) AS "zeroResultCount"`; `count(*) FILTER (WHERE created_at >= :last7DaysStart AND result_count >= 1 AND result_limit IS NOT NULL AND result_count < result_limit) AS "lowResultCount"` |
| `SearchEventStatsResponse` | `search/domain/dto`, `record(long totalCount, long todayCount, long last7DaysCount, long zeroResultCount, long lowResultCount, @Nullable Double zeroResultRate, @Nullable Double lowResultRate, String timeZone, Instant todayStart, Instant last7DaysStart)`. `static from(SearchEventStatsView view, ZoneId zone, Instant todayStart, Instant last7DaysStart)`: oran = `last7DaysCount == 0 ? null : (double) count / last7DaysCount` |
| `SearchService` | + `SearchEventStatsResponse getSearchEventStats(long workspaceId)`, sırasıyla: `ensureWorkspaceExists`; `LocalDate today = LocalDate.now(clock.withZone(zone))`; `todayStart = today.atStartOfDay(zone).toInstant()`; `last7DaysStart = today.minusDays(STATS_WINDOW_DAYS - 1).atStartOfDay(zone).toInstant()`; `countStats`; `from(...)`. Sabit: `private static final int STATS_WINDOW_DAYS = 7` |
| `SearchEventController` | + `@GetMapping("/stats") SearchEventStatsResponse getSearchEventStats(@PathVariable long workspaceId)` + OpenAPI annotation'ları (200, 404, 500) |
| `WorkspaceEmbeddingModelView` | `chunk/repository`, projection: `long getWorkspaceId()`, `String getEmbeddingModel()` |
| `ChunkRepository` | İki yeni sorgu, ikisi de açık JPQL `@Query`:<br>• `List<WorkspaceEmbeddingModelView> findEmbeddingModelsByWorkspaceIds(@Param("workspaceIds") Collection<Long> workspaceIds)`: `select distinct c.workspaceId as workspaceId, c.embeddingModel as embeddingModel from Chunk c where c.workspaceId in :workspaceIds`<br>• `Optional<ChunkView> findViewBySourceIdAndId(@Param("sourceId") long sourceId, @Param("chunkId") long chunkId)`: `select c.id as id, c.chunkNo as chunkNo, c.heading as heading, c.content as content, c.tokenCount as tokenCount, c.embeddingModel as embeddingModel from Chunk c where c.source.id = :sourceId and c.id = :chunkId` |
| `WorkspaceResponse` | + `@ArraySchema(arraySchema = @Schema(requiredMode = REQUIRED, ...)) List<String> embeddingModels` (sonda, `List.copyOf`); `static from(WorkspaceSummary summary, List<String> embeddingModels)` |
| `WorkspaceService` | `ChunkRepository` enjekte edilir. `listWorkspaces`: sayfanın id'leri için tek `findEmbeddingModelsByWorkspaceIds` → alan başına artan sıralı liste (yoksa `List.of()`). `getWorkspace`: aynı metot tek id'yle |
| `ChunkService` | + `ChunkResponse getChunk(long workspaceId, long sourceId, long chunkId)`, sırasıyla: mevcut alan + kaynak kontrolü (`WORKSPACE_NOT_FOUND`, `SOURCE_NOT_FOUND`); `findViewBySourceIdAndId`; yoksa `NotFoundException("CHUNK_NOT_FOUND", "Chunk not found: " + chunkId)` |
| `ChunkController` | + `@GetMapping("/{chunkId}") ChunkResponse getChunk(@PathVariable long workspaceId, @PathVariable long sourceId, @PathVariable long chunkId)` + annotation'lar |
| `OpenApiConfig` | `API_VERSION = "0.3.0"`; `ERROR_CODES` sözleşmedeki 21 kodla aynı sırada |
| `backend/CLAUDE.md` | Migration aralığı `V1`-`V7`; yeni endpoint'ler komutlar bölümüne bir satır (`curl -s localhost:8080/api/workspaces/42/search-events/stats`) |

`SourceResponse`, `SourceService`, `IngestionJobRepository` bu task'ta değişmez (D1). Log eklenmez (yeni endpoint'ler okuma).

## Frontend planı
Yüklenecek skill'ler: `frontend-conventions`, `react-patterns`, `react-state-management`, `frontend-a11y`, `frontend-design`, `vite-patterns`, `frontend-security`, `react-best-practices`, `react-composition-patterns`.

### İskelet ve paketler (S2 = A, kullanıcı onayladı)
- `frontend/` içinde Vite `react-ts` şablonu kurulur: `npm create vite@latest` geçici klasöre üretilir, `CLAUDE.md` korunarak taşınır.
  - Şablondan gelen `App.css`, `assets/*` ve örnek sayaç silinir.
  - Lint `oxlint` (`npm run lint`). Şablon ESLint getirirse ESLint paketleri ve config'i kaldırılır, `.oxlintrc.json` eklenir.
- **Runtime paketleri:** `react-router` (7.x, declarative `BrowserRouter`), `@tanstack/react-query` (5.x), `react-error-boundary`, `@fontsource/source-sans-3`, `@fontsource/jetbrains-mono`.
- **Dev paketleri:** `vitest`, `@vitest/coverage-v8`, `jsdom`, `@testing-library/react`, `@testing-library/dom`, `@testing-library/user-event`, `@testing-library/jest-dom`, `msw`, `vitest-axe`, `@playwright/test`, `oxlint`. Sürümler kurulumdaki son kararlı sürüm; raporda ve `frontend/CLAUDE.md`'de yazılır.
- `package.json` script'leri: `dev`, `build` (`tsc -b && vite build`), `lint` (`oxlint`), `preview`, `test` (`vitest run`), `test:watch` (`vitest`), `test:coverage` (`vitest run --coverage`), `test:e2e` (`playwright test`).
- `vite.config.ts`:
  - `server.proxy['/api'] = { target: 'http://localhost:8080' }`.
  - `test.environment: 'jsdom'`, `test.setupFiles: ['./src/test/setup.ts']`, `test.css.modules.classNameStrategy: 'non-scoped'`.
  - `test.coverage`: `provider: 'v8'`, `include: ['src/**/*.{ts,tsx}']`, `exclude: ['src/test/**', 'src/main.tsx', '**/*.test.*']`, `thresholds: { lines: 80 }`.
  - `e2e/` Vitest'e dahil edilmez (`test.exclude`'a `e2e/**`).
- `playwright.config.ts`:
  - `testDir: './e2e'`, `use.baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000'`.
  - Tek proje `chromium`; `timeout: 60_000` (gerçek Ollama), `retries: 0`, `reporter: 'list'`, `trace: 'retain-on-failure'`.
  - `webServer` yok (sistem dışarıdan açık).
- `frontend/CLAUDE.md`: "hedef" ifadeleri gerçek sürüm ve komutlarla değiştirilir. Eklenecek komutlar:
  - `npm test`, `npm run test:coverage`, `npm run test:e2e`, `npx playwright install chromium`.
  - Tek test: `npx vitest run src/features/search/pages/SearchPage.test.tsx -t '<ad>'`.
- `.gitignore`: `node_modules`, `dist`, `coverage`, `test-results`, `playwright-report`.

### Klasör ve dosyalar
```
src/
├── main.tsx                       render + font import'ları + AppProviders + App
├── index.css                      token'lar (lab.html --s-* → --color-*), reset, font aileleri
├── app/
│   ├── App.tsx                    Routes (D10)
│   ├── AppProviders.tsx           QueryClientProvider (D24), BrowserRouter, ErrorBoundary
│   ├── WorkspaceLayout.tsx        :workspaceId doğrulama, useWorkspaceQuery, AppHeader + SectionTabs + LastSearchProvider key + Outlet / NotFoundState
│   ├── AppHeader.tsx              logo "yanıt." (Link → /w/:id/arama) + WorkspaceSelect
│   ├── WorkspaceSelect.tsx        D11
│   ├── SectionTabs.tsx            Arama · Arama kayıtları (+ totalCount rozeti) · Bilgi kaynakları
│   └── NotFoundPage.tsx           "Sayfa bulunamadı" + /w/42/arama bağlantısı
├── shared/
│   ├── api/                       httpClient.ts, ApiError.ts, errorMessages.ts, pagination.ts
│   ├── workspace/                 types.ts, workspaceApi.ts, workspaceQueries.ts, workspaceRules.ts, useCurrentWorkspace.ts (D6)
│   ├── format/                    numberFormat.ts, dateFormat.ts (D21)
│   └── components/                ErrorAlert, StatusBadge, EmptyState, NotFoundState, Pagination, Breadcrumbs (+ .module.css)
├── features/search/
│   ├── api/                       searchApi.ts, searchQueries.ts
│   ├── components/                LastSearchProvider, SearchForm, SearchSuggestions, SearchInfoLine, SearchSettingsPanel,
│   │                              SearchResults, SearchResultHeader, SearchResultEmpty, SearchHitList, SearchHitItem,
│   │                              HighlightedText, SearchTechnicalDetails, SearchEventStats, SearchEventTable, ResultCountBadge
│   ├── hooks/                     useSearchSettings.ts, useLastSearch.ts
│   ├── pages/                     SearchPage.tsx, SearchEventsPage.tsx
│   └── model/                     types.ts, searchRules.ts, searchSettings.ts, searchHitText.ts, searchNotices.ts, resultCountTone.ts
└── features/source/
    ├── api/                       sourceApi.ts, sourceQueries.ts
    ├── components/                SourceTable, SourceRow, SourceStatusBadge, SourceInfoBoxes, ChunkTable, HighlightedChunkNotice
    ├── pages/                     SourcesPage.tsx, SourceDetailPage.tsx
    └── model/                     types.ts, sourceRules.ts
```
Her component kendi `X.module.css`'i ile. `dangerouslySetInnerHTML` hiçbir yerde yok.

### Type'lar (sözleşmeyle birebir)
- `shared/workspace/types.ts`: `WorkspaceResponse`.
- `features/search/model/types.ts`:
  - Enum'lar: `SEARCH_MODES`/`SearchMode`, `FILTER_STRATEGIES`/`FilterStrategy`, `SearchBranch` (`'KEYWORD' | 'SEMANTIC'`), `MatchedTermField`.
  - İstek/yanıt: `SearchChunksRequest`, `SearchResultResponse`, `SearchTimingsResponse`, `SearchHitResponse` (`chunkNo` dahil), `MatchedTermResponse`.
  - Kayıtlar: `SearchEventResponse` (`limit: number | null`), `SearchEventStatsResponse`.
- `features/source/model/types.ts`: `SourceKind`, `SourceStatus`, `SourceResponse` (0.3.0'daki alanlar; `latestJob` yok), `ChunkResponse`.
- `shared/api/pagination.ts`: `PageResponse<T>`, `PageParams`.

### API ve query hook'ları
| Dosya | Fonksiyon / hook | Not |
|-------|------------------|-----|
| `shared/workspace/workspaceApi.ts` | `listWorkspaces(params)`, `getWorkspace(workspaceId)` | `size=100` |
| `shared/workspace/workspaceQueries.ts` | `workspaceKeys = { all: ['workspaces'], list: () => [...all, 'list'], detail: (id) => [...all, id] }`; `useWorkspacesQuery()`, `useWorkspaceQuery(workspaceId)` | |
| `shared/workspace/workspaceRules.ts` | `DEFAULT_WORKSPACE_ID = 42`, `WORKSPACE_LIST_SIZE = 100`, `parseWorkspaceId(param: string \| undefined): number \| null` (pozitif tam sayı değilse null, G3) | |
| `shared/workspace/useCurrentWorkspace.ts` | `useCurrentWorkspace()` → `{ workspaceId, workspace }` (`WorkspaceLayout` içindeki sayfalar için; yüklü değilse layout render etmez) | |
| `features/search/api/searchApi.ts` | `searchChunks(workspaceId, request)`, `listSearchEvents(workspaceId, page)`, `getSearchEventStats(workspaceId)` | events `size=20` |
| `features/search/api/searchQueries.ts` | `searchKeys = { all: (id) => ['search', id], events: (id, page) => [...all(id), 'events', page], stats: (id) => [...all(id), 'stats'] }`. Hook'lar:<br>• `useSearchChunksMutation(workspaceId)`: `onSuccess`'te `searchKeys.all(workspaceId)` invalidate<br>• `useSearchEventsQuery(workspaceId, page)`: `placeholderData: keepPreviousData`<br>• `useSearchEventStatsQuery(workspaceId)` | |
| `features/source/api/sourceApi.ts` | `listSources(workspaceId)`, `getSource(workspaceId, sourceId)`, `listChunks(workspaceId, sourceId, page)`, `getChunk(workspaceId, sourceId, chunkId)` | Liste `size=20` (alan başına en fazla 10 kaynak) |
| `features/source/api/sourceQueries.ts` | `sourceKeys = { all: (id) => ['sources', id], list: (id) => [...all(id), 'list'], detail: (id, sourceId) => [...all(id), sourceId], chunks: (id, sourceId) => [...detail, 'chunks'], chunk: (id, sourceId, chunkId) => [...detail, 'chunk', chunkId] }`. Hook'lar:<br>• `useSourcesQuery`, `useSourceQuery`<br>• `useChunksInfiniteQuery`: `getNextPageParam` = `page + 1 < totalPages`<br>• `useChunkQuery(workspaceId, sourceId, chunkId \| null)`: `enabled: chunkId !== null` | |

### Kurallar ve sabitler (model)
**`searchRules.ts`**
- Sınırlar: `QUERY_MAX_LENGTH = 500`; `EF_SEARCH_MIN = 10`, `EF_SEARCH_MAX = 1000`, `EF_SEARCH_DEFAULT = 40`; `SEARCH_LIMIT_MIN = 1`, `SEARCH_LIMIT_MAX = 50`, `SEARCH_LIMIT_DEFAULT = 10`.
- Varsayılanlar: `DEFAULT_SEARCH_SETTINGS = { mode: 'HYBRID', filterStrategy: 'ITERATIVE_SCAN', efSearch: 40, limit: 10, explain: false }`.
- Sabitler: `SEARCH_CHANNEL = 'Temsilci paneli'`; `SUGGESTED_QUERIES` (TASK K14'teki 3 metin, aynı sırayla); `SEARCH_EVENTS_PAGE_SIZE = 20`.
- Fonksiyonlar: `normalizeQuery(input) = input.trim()`; `canSearch(input)`; `validateEfSearch(raw: string): string | null` ve `validateLimit(raw: string): string | null` (tam sayı ve aralık); `toSearchRequest(query, settings): SearchChunksRequest` (D15).
- Mesajlar: `EF_SEARCH_RULE_MESSAGE = 'ef_search 10 ile 1000 arasında bir tam sayı olmalı.'`, `LIMIT_RULE_MESSAGE = 'Sonuç sayısı 1 ile 50 arasında bir tam sayı olmalı.'`.
- Etiketler:
  - `MODE_LABELS = { KEYWORD: 'Kelime', VECTOR: 'Anlam', HYBRID: 'Hybrid' }`
  - `MODE_INFO_LABELS = { KEYWORD: 'Kelime', VECTOR: 'Anlam (vector)', HYBRID: 'Hybrid (kelime + anlam)' }`
  - `FILTER_LABELS = { POST_FILTER: 'post-filter', ITERATIVE_SCAN: 'iterative scan' }`

**Diğer search model dosyaları**
- `searchSettings.ts`: D8; anahtar `SEARCH_SETTINGS_STORAGE_KEY = 'emb:search-settings:v1'`.
- `searchHitText.ts`:
  - `SNIPPET_MAX_LENGTH = 190`, `SNIPPET_CUT_LENGTH = 188`; `buildSnippet(content): string`.
  - `toHighlightSegments(text: string, terms: MatchedTermResponse[], field: MatchedTermField): { text: string; isMatch: boolean }[]`: sadece `end <= text.length` olan terimler, sıralı, çakışmalar birleşik.
  - `hitTitle(heading)` (D22).
- `searchNotices.ts`:
  - `noResultHint(mode)`: KEYWORD → `'Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene.'`; diğerleri → `'Farklı kelimelerle aramayı dene.'`.
  - `shortVectorWarning(result)`: POST_FILTER → `` `Anlam araması istenen ${limit} sonuçtan sadece ${vectorCount} tanesini buldu (kısa kaldı). Gelişmiş ayarlarda filtre stratejisini iterative scan yapıp tekrar dene.` ``; ITERATIVE_SCAN → `` `Anlam araması istenen ${limit} sonuçtan sadece ${vectorCount} tanesini buldu. Bu çalışma alanında yeterli parça olmayabilir.` ``.
- `resultCountTone.ts`: `resultCountTone(resultCount, limit: number | null): 'bad' | 'run' | 'ok'` (0 → bad; `limit !== null && resultCount < limit` → run; diğer → ok).

**`sourceRules.ts`**
- `SOURCE_KIND_LABELS = { WEB_CRAWL: 'Web sitesi', PDF: 'PDF', MARKDOWN: 'Metin / Markdown', CSV: 'CSV', TICKET_EXPORT: 'Talep geçmişi' }`.
- `SOURCE_STATUS_LABELS = { READY: 'Hazır', INGESTING: 'İşleniyor', FAILED: 'Başarısız' }`.
- `CHUNK_PAGE_SIZE = 20`, `CHUNK_PREVIEW_LENGTH = 160`.

### `errorCode` → Türkçe mesaj (`shared/api/errorMessages.ts`)
Sözleşmedeki 21 kodun hepsi (AC-6), yüklemeye ait olanlar dahil; ekranda kullanılmasalar da eşleme tek yerde tam tutulur.
| `errorCode` | Mesaj |
|-------------|-------|
| `VALIDATION_FAILED` | Girdiğin bilgileri kontrol et. |
| `BAD_REQUEST` | İstek okunamadı. Sayfayı yenileyip tekrar dene. |
| `NOT_FOUND` | İstenen adres bulunamadı. |
| `METHOD_NOT_ALLOWED` | Bu işlem desteklenmiyor. |
| `NOT_ACCEPTABLE` | Sunucu bu isteğe uygun bir yanıt veremedi. |
| `UNSUPPORTED_MEDIA_TYPE` | İstek biçimi desteklenmiyor. |
| `EMBEDDING_UNAVAILABLE` | Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara. |
| `EMBEDDING_MODEL_NOT_FOUND` | Embedding modeli Ollama'da yüklü değil. Modeli indir (ollama pull bge-m3) ya da Kelime yöntemiyle ara. |
| `INTERNAL_ERROR` | Sunucuda beklenmeyen bir hata oluştu. Tekrar dene. |
| `WORKSPACE_NOT_FOUND` | Çalışma alanı bulunamadı. |
| `SOURCE_NOT_FOUND` | Kaynak bulunamadı. |
| `CHUNK_NOT_FOUND` | Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir. |
| `INGESTION_JOB_NOT_FOUND` | Yükleme işi bulunamadı. |
| `SOURCE_NAME_TAKEN` | Bu ad çalışma alanında başka bir kaynakta kullanılıyor. |
| `SOURCE_LIMIT_REACHED` | Bu çalışma alanı 10 kaynak sınırına ulaştı. |
| `SOURCE_KIND_MISMATCH` | Bu kaynağa sadece aynı türde dosya yüklenebilir. |
| `INGESTION_IN_PROGRESS` | Bu kaynağın yüklemesi sürüyor. Bitince tekrar dene. |
| `EMBEDDING_MODEL_MISMATCH` | Ayarlardaki embedding modeli, bu çalışma alanının parçalarını oluşturan modelden farklı. Anlam ve Hybrid arama ile dosya yükleme bu yüzden yapılamıyor; Kelime yöntemiyle arayabilirsin. |
| `FILE_TOO_LARGE` | Dosya 20 MB'tan büyük olamaz. |
| `UNSUPPORTED_FILE_TYPE` | Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç. |
| `UNSUPPORTED_FILE_ENCODING` | Metin dosyası UTF-8 olmalı. Dosyayı UTF-8 olarak kaydedip tekrar yükle. |
| `NETWORK_ERROR` (istemci) | Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et. |
| fallback (`UNEXPECTED_ERROR` ve bilinmeyenler) | Beklenmeyen bir hata oluştu. Tekrar dene. |

Alan hatası: `VALIDATION_FAILED` + `violations[].field = 'query'` → `` `Arama metni 1-${QUERY_MAX_LENGTH} karakter olmalı.` `` arama kutusunun altında. Diğer hatalar `role="alert"`. İş hata kodlarının (`NO_TEXT_EXTRACTED`, `FILE_UNREADABLE`, `INGESTION_INTERRUPTED`) metinleri sonraki task'ta eklenir.

### Sayfa davranışı
| Sayfa / component | Davranış |
|-------------------|----------|
| `WorkspaceLayout` | • Başlık her durumda görünür.<br>• `parseWorkspaceId` null → `NotFoundState` ("Çalışma alanı bulunamadı" + "Varsayılan çalışma alanına dön" → `/w/42/arama`); istek atılmaz.<br>• `useWorkspaceQuery` 404 → aynı ekran.<br>• Diğer hata → `ErrorAlert` + "Tekrar dene". |
| `SearchPage` | • `<title>Bilgi bankası · yanıt.</title>`.<br>• Başlık "Bilgi bankası"; açıklama `` `Müşterinin sorusunu kendi kelimeleriyle yaz; ${workspace.name} bilgi kaynaklarında aranır.` ``.<br>• `SearchForm` (`role="search"`): input etiketi "Arama metni" (görsel olarak gizli), placeholder "Örn. modemde kırmızı ışık yanıyor", buton "Ara" (pending'de "Aranıyor…" ve `disabled`).<br>• `SearchSuggestions`: "Sık aranan:" + 3 buton, pending'de `disabled`.<br>• `SearchInfoLine`: `Arama: <b>…</b> · Sonuç: <b>en fazla N</b> · Embedding: <b>…</b>`.<br>• `SearchSettingsPanel`.<br>• Sonuç alanı: pending → `“q”` + "aranıyor…" + 3 iskelet satır (`aria-busy`); hata → `ErrorAlert`; sonuç → `SearchResults`; hiçbiri → `EmptyState` "Henüz arama yapılmadı" / "Sık arananlardan birini seç ya da kendi aramanı yaz." |
| `SearchSettingsPanel` | • `<details>` "Gelişmiş ayarlar", kapalı başlar.<br>• Alanlar: radyo grubu "Arama yöntemi" (Kelime/Anlam/Hybrid), radyo grubu "Filtre stratejisi" (post-filter/iterative scan), sayı alanı "ef_search", sayı alanı "Sonuç sayısı", onay kutusu "Sorgu planını getir"; buton "Varsayılana dön".<br>• Kelime'de filtre, ef_search ve plan `disabled`.<br>• Geçersiz alanın altında kural mesajı (`aria-invalid`, `aria-describedby`); geçersiz alan varken submit arama yapmaz ve paneli açar. |
| `SearchResultHeader` | • `“q”` · `` `${returnedCount} sonuç` `` · `` `toplam ${timings.totalMs} ms` ``.<br>• `vectorShort` → uyarı kutusu (`shortVectorWarning`). |
| `SearchHitItem` | `<Link to="/w/{id}/kaynaklar/{sourceId}?parca={chunkId}">` içinde:<br>• sıra no ve başlık (HEADING vurgulu),<br>• etiketler `anlam` (SEMANTIC) ve `kelime` (KEYWORD), bu sırayla,<br>• snippet (CONTENT vurgulu),<br>• alt satır `` `${sourceName} · parça ${chunkNo} · #${chunkId}` ``. |
| `SearchTechnicalDetails` | • `<details>` "Teknik detay", kapalı başlar.<br>• Tanım listesi: Yöntem, Filtre stratejisi, ef_search, Kelime kolu satır, Vektör kolu satır, Embedding süresi, SQL süresi, Toplam süre.<br>• Tablo: Sıra, Parça (#id), Kelime sırası, Kelime puanı, Vektör sırası, Cosine mesafesi, RRF puanı.<br>• null → "—".<br>• `vectorPlan` varsa "Sorgu planı" başlığı + `<pre>` (mono, yatay kaydırma). |
| `SearchEventsPage` | • `<title>Arama kayıtları · yanıt.</title>`.<br>• Başlık "Arama kayıtları"; açıklama "Bu çalışma alanında yapılan aramalar. Sonuçsuz ve istenenden az sonuç dönen aramalar, bulunamayan içeriğin ilk işaretidir."<br>• `SearchEventStats`, 3 kutu: "Bugün" `todayCount` "arama"; "Sonuçsuz arama" `formatPercent(zeroResultRate)` "son 7 gün"; "Az sonuç dönen" `formatPercent(lowResultRate)` "son 7 gün".<br>• `SearchEventTable` sütunları: Saat, Nereden (`channel ?? '—'`), Arama, Yöntem (`MODE_LABELS`), Sonuç (`ResultCountBadge`: bad → "Sonuçsuz", run/ok → sayı), İlk sonuç (`topChunkHeading ?? '—'`).<br>• Kayıt yoksa "Henüz arama kaydı yok".<br>• `Pagination`: "Önceki", "Sonraki", `Sayfa ${page + 1} / ${totalPages}`; sayfa URL'de tutulmaz. |
| `SectionTabs` | • Sekmeler: "Arama", "Arama kayıtları" + rozet `formatInteger(stats.totalCount)` (yüklenmediyse rozet yok), "Bilgi kaynakları".<br>• "Bilgi kaynakları" kaynak detayında da aktif. |
| `SourcesPage` | • `<title>Bilgi kaynakları · yanıt.</title>`.<br>• `Breadcrumbs` [`workspace.name`, "Bilgi kaynakları"].<br>• Başlık "Bilgi kaynakları"; açıklama "Arama sadece buradaki içerikte yapılır. Her kaynak parçalara bölünür ve embedding'e çevrilir."<br>• `SourceTable`; altında `` `Toplam ${formatInteger(workspace.chunkCount)} parça · embedding modeli ${models}` ``.<br>• "Yeni kaynak ekle" butonu yok (D19). |
| `SourceRow` | • Satır tıklanabilir (`onClick` → detay); ad hücresinde ayrıca `<Link>` (klavye).<br>• Ad hücresi: ad + `` `${fileName ?? '—'} · ${version ? 'v' + version : 'sürüm yok'}` ``.<br>• Tür: `SOURCE_KIND_LABELS`. Parça: `formatInteger(chunkCount)`.<br>• Durum: `SourceStatusBadge` (D20).<br>• Son senkron: `formatDateTime(syncedAt)` ya da "—".<br>• Yükleme butonu ve ilerleme yok. |
| `SourceDetailPage` | • `<title>{name} · yanıt.</title>`.<br>• `Breadcrumbs` [Link "Bilgi kaynakları", `name`]; başlık `name`; "Yeni sürüm yükle" butonu yok.<br>• `SourceInfoBoxes`: Tür, Sürüm, Dosya adı, Parça, Embedding modeli, Son senkron (boş → "—"), ayrıca Durum rozeti.<br>• `?parca` varsa `HighlightedChunkNotice`: `"Arama sonucundan açılan parça vurgulandı: #{id}"` + vurgulu parça satırı en üstte; `CHUNK_NOT_FOUND` → `messageFor`.<br>• `ChunkTable` sütunları: "#", "Parça" (başlık kalın, içerik önizlemesi ilk 160 karakter + "…", altında `` `chunk ${chunkId}` ``), "Token".<br>• "Daha fazla göster" (son sayfada yok).<br>• `SOURCE_NOT_FOUND` → `NotFoundState` "Kaynak bulunamadı" + "Bilgi kaynaklarına dön". |

### Görsel dil
`frontend-design` skill'i kullanılır; kaynak `lab.html`'in ürün tarafıdır.
- **Taşınacak öğeler:** `--s-*` token'ları, `.res`, `.mt.a/.k`, `.rs mark`, `.kpis`, `.tbl`, `.st.ok/.run/.bad`, `.callout`, `.crumbs`, `.meta`, `.sbox`, `.sugg`, `.cfg`, `.rsk` iskeleti. `.dlg`, `.toast`, `.prog` sonraki task'ta.
- **Token adları:** `--color-bg`, `--color-surface`, `--color-ink`, `--color-muted`, `--color-line`, `--color-brand`, `--color-brand-strong`, `--color-brand-soft`, `--color-accent`, `--color-accent-soft`, `--color-danger`, `--color-danger-soft`, `--color-success`, `--color-success-soft`, `--font-site`, `--font-mono` + boşluk/radius ölçeği.
- **Yerleşim:** Sayfa en az 1024 px genişlikte tasarlanır, tam pencere. İçerik alanı yatay padding'li, tablolar tam genişlik. Görünür focus stili korunur.

## Test senaryoları
**Backend:** testler `backend/src/test/java/...` altında. TASK-002'deki `PostgresIntegrationTest`, `TestcontainersConfiguration`, fixture'lar ve `FakeEmbeddingClient` kullanılır; sabit saat için `FixedClockTestConfiguration`.

**Frontend:** testler, test ettiği dosyanın yanında `*.test.ts(x)`. `src/test/` altına tester şunları yazar:
- `setup.ts`: jest-dom, vitest-axe matcher'ı, MSW `server.listen({ onUnhandledRequest: 'error' })`, her testten sonra `localStorage.clear()`.
- `server.ts`, `handlers.ts` (varsayılan veri aşağıda), `problem.ts` (`problem(status, errorCode, violations?)`).
- `fixtures.ts`: sözleşmedeki bütün alanlarıyla tam nesneler.
- `renderWithProviders.tsx`: yeni `QueryClient` (`retry: false`), `MemoryRouter initialEntries`; `App` route'larıyla render eden `renderApp(path)`.

**Varsayılan MSW verisi:**
- Alanlar: 42 "Dalga Fiber" (4 kaynak, 42 parça, `['bge-m3@1024']`), 19 "Kıyı Net" (3 kaynak, 38 parça).
- 42'nin kaynakları: 301 WEB_CRAWL "Yardım merkezi" (21 parça, READY); 302 PDF "Dalga T5 modem kullanım kılavuzu" (v3.1, `Dalga-T5-Kullanim-Kilavuzu-v3.1.pdf`, 5 parça, READY); 303 CSV "Modem hata kodları tablosu"; 304 TICKET_EXPORT "Çözülmüş destek talepleri".
- 19'un kaynakları: 1191-1193.
- Stats sıfırlar (`totalCount: 0`, oranlar null); events boş sayfa.

| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| B1 | Arama sonucunda parça no (K18) | integration `SearchIntegrationTest` | `givenChunkNoSeven_whenSearchChunksInEachMode_thenHitCarriesChunkNoSeven` (parametreli KEYWORD/VECTOR/HYBRID) | Kaynakta `chunkNo=7` başlığı "TS-999 hata kodu" olan tek parça; üç modda ilk hit `chunkNo == 7`, `chunkId` o parçanın id'si |
| B2 | Kayıtta istenen sayı (K26) | integration | `givenSearchWithLimitFive_whenListSearchEvents_thenEventLimitIsFive` | `limit=5` arama → `listSearchEvents` ilk kayıt `limit == 5`, `resultCount` arama yanıtıyla aynı |
| B3 | Göstergeler (K25, S-6, G4) | integration `SearchEventStatsIntegrationTest` (saat `2026-10-25T10:00:00Z`) | `givenEventsAroundIstanbulDayBoundaries_whenGetSearchEventStats_thenCountsByIstanbulCalendarDays` | **Veri** (alan 42; createdAt, sonuç/limit):<br>• `2026-10-25T09:00Z` 10/10<br>• `2026-10-25T08:00Z` 0/10<br>• `2026-10-24T21:30Z` 3/10 (İstanbul 25 Eki 00:30)<br>• `2026-10-24T20:59Z` 10/10<br>• `2026-10-18T21:00Z` 0/5 (İstanbul 19 Eki 00:00)<br>• `2026-10-18T20:59Z` 0/10<br>• `2026-10-20T12:00Z` 4/null (native insert)<br>• Alan 19'da bugün 1 kayıt.<br>**Beklenen:** `totalCount=7`, `todayCount=3`, `last7DaysCount=6`, `zeroResultCount=2`, `lowResultCount=1`, `zeroResultRate≈0.333333` (`within(1e-6)`), `lowResultRate≈0.166667`, `todayStart=2026-10-24T21:00:00Z`, `last7DaysStart=2026-10-18T21:00:00Z`, `timeZone="Europe/Istanbul"` |
| B4 | Veri yokken (K25) | integration | `givenNoEvents_whenGetSearchEventStats_thenZeroCountsAndNullRates` | Sayılar 0, `zeroResultRate` ve `lowResultRate` null |
| B5 | Olmayan alan | controller `SearchEventControllerTest` | `givenUnknownWorkspace_whenGetSearchEventStats_thenReturns404WorkspaceNotFound` | 404, `application/problem+json`, `errorCode=WORKSPACE_NOT_FOUND` |
| B6 | Gösterge JSON şekli | controller | `givenStats_whenGetSearchEventStats_thenReturnsContractFields` | 10 alan sözleşmedeki adlarla; `zeroResultRate: null` JSON'da `null` olarak var |
| B7 | Gün sınırı Clock'tan (D2) | unit `SearchServiceTest` | `givenClockAtIstanbulLateEvening_whenGetSearchEventStats_thenTodayStartsAtPreviousUtcEvening` | Saat `2026-10-24T20:59:59Z` (İstanbul 23:59:59) → repository `todayStart=2026-10-23T21:00:00Z`, `last7DaysStart=2026-10-17T21:00:00Z` ile çağrılır (ArgumentCaptor) |
| B10 | Alanın modelleri (D5) | integration `WorkspaceIntegrationTest` | `givenChunksWithTwoModelTags_whenListWorkspaces_thenEmbeddingModelsSortedDistinct` | Alan 42 parçaları `bge-m3@1024` (3) + `aaa@8` (1) → `["aaa@8", "bge-m3@1024"]`; alan 19 parçasız → `[]`; `getWorkspace(42)` aynı liste |
| B11 | Tek parça (D4, G1) | integration `ChunkIntegrationTest` | 4 test:<br>• `givenChunkOfSource_whenGetChunk_thenReturnsChunk`<br>• `givenChunkOfOtherSource_whenGetChunk_thenReturns404ChunkNotFound`<br>• `givenSourceOfOtherWorkspace_whenGetChunk_thenReturns404SourceNotFound`<br>• `givenReplacedChunk_whenGetChunk_thenReturns404ChunkNotFound` | Alanlar sözleşmedeki gibi (`chunkId`, `chunkNo`, `heading`, `content`, `tokenCount`, `embeddingModel`); 404'ler ilgili `errorCode` ile; yeni sürüm sonrası eski id → `CHUNK_NOT_FOUND` |
| B12 | Controller şekli | controller `ChunkControllerTest` | `givenMissingChunk_whenGetChunk_thenReturns404ProblemDetail` | 404, `application/problem+json`, `errorCode=CHUNK_NOT_FOUND` |
| B13 | Sözleşme dokümanı (AC-5) | integration `OpenApiDocumentationIntegrationTest` | `givenApplication_whenGetApiDocs_thenContainsNewPathsAndChunkNotFound` | `info.version=0.3.0`; path'ler `/api/workspaces/{workspaceId}/search-events/stats`, `/api/workspaces/{workspaceId}/sources/{sourceId}/chunks/{chunkId}`; `ProblemDetail.errorCode` enum'unda `CHUNK_NOT_FOUND`, 21 değer; `SourceResponse`'ta `latestJob` yok |
| B14 | Eski kayıt (D3, G4) | integration | `givenEventStoredWithoutLimit_whenListSearchEvents_thenLimitIsNull` | Native insert `result_limit NULL` → yanıt `limit: null` |
| F1 | Arama metni kuralı (K12, D16) | unit `searchRules.test.ts` | `givenBlankOrWhitespaceQuery_whenCanSearch_thenFalse` (tablo) | `''`, `'   '`, `'\t\n'` → false; `' a '` → true; 500 karakter → true; 501 → false |
| F2 | Ayar kuralları (K16) | unit | `givenEfSearchAndLimitInputs_whenValidate_thenReturnsRuleMessageOutsideRange` (tablo) | ef `'9'`, `'1001'`, `'40.5'`, `''`, `'abc'` → `EF_SEARCH_RULE_MESSAGE`; `'10'`, `'1000'` → null; limit `'0'`, `'51'` → `LIMIT_RULE_MESSAGE`; `'1'`, `'50'` → null |
| F3 | Snippet (D18) | unit `searchHitText.test.ts` | `givenLongContent_whenBuildSnippet_thenCutsAtLastSpaceBefore188` | 200 karakterlik içerik (180. karakterde boşluk, sonrası boşluksuz) → ilk 180 karakter + `' …'`; 150 karakterlik içerik aynen |
| F4 | Vurgu segmentleri (K18, K7, G5) | unit | `givenMatchedTerms_whenToHighlightSegments_thenSplitsTextIntoMarkedSegments` | `'TS-999 hata kodu modemde'`, terim CONTENT 0-6 → `[{'TS-999', true}, {' hata kodu modemde', false}]`; `'Şifremi unuttum'` 0-7 → `'Şifremi'` işaretli; snippet uzunluğunu aşan terim atılır; HEADING terimi CONTENT'e uygulanmaz |
| F5 | Rozet tonu (K26, G4) | unit `resultCountTone.test.ts` | `givenResultCountAndLimit_whenResultCountTone_thenReturnsTone` (tablo) | (0,10) bad; (3,10) run; (10,10) ok; (4,null) ok; (0,null) bad |
| F6 | Biçimler (D21) | unit `numberFormat.test.ts`, `dateFormat.test.ts` | `givenValues_whenFormat_thenTurkishFormats` | `formatPercent(0.25)` → `'%25'`; `0.068` → `'%6,8'`; null → `'—'`; `formatInteger(1410)` → `'1.410'`; şimdi `2026-10-25T10:00Z` iken `formatClock('2026-10-25T11:05:00Z')` → `'14:05'`, `'2026-10-24T11:05:00Z'` → `'24 Eki 14:05'`; `formatDateTime('2026-10-12T11:31:40Z')` → `'12 Eki 2026 14:31'` |
| F7 | Bütün kodların Türkçesi (K6, AC-6) | unit `errorMessages.test.ts` | `givenContractErrorCode_whenMessageFor_thenReturnsSpecificTurkishMessage` (tablo) | Sözleşmenin 21 kodu + `NETWORK_ERROR` için yukarıdaki tablodaki metin (literal); `'SOMETHING_NEW'` → `'Beklenmeyen bir hata oluştu. Tekrar dene.'` |
| F8 | httpClient (K6, D14) | unit `httpClient.test.ts` (MSW) | 3 test:<br>• `givenProblemResponse_whenRequest_thenThrowsApiErrorWithCode`<br>• `givenBodyWithoutErrorCode_whenRequest_thenUnexpectedError`<br>• `givenNetworkFailure_whenRequest_thenNetworkError` | • 404 problem → `ApiError` `status=404`, `errorCode='WORKSPACE_NOT_FOUND'`<br>• düz metin 500 → `'UNEXPECTED_ERROR'`<br>• `HttpResponse.error()` → `'NETWORK_ERROR'` |
| F9 | Ayarların saklanması (K16, D8) | unit `searchSettings.test.ts` | `givenCorruptOrOutOfRangeStorage_whenReadSearchSettings_thenFallsBackPerField` | `'{bozuk'` → `DEFAULT_SEARCH_SETTINGS`; `{mode:'VECTOR', efSearch:5000}` → mode VECTOR, efSearch 40; yazılan değer aynen okunur |
| F10 | Açılış (S-1, K3, K5, K17) | component `App.test.tsx` | `givenRootPath_whenAppOpens_thenRedirectsToDalgaSearchWithEmptyState` | `/` → `/w/42/arama`; heading "Bilgi bankası"; "Henüz arama yapılmadı"; seçicide "Dalga Fiber · 4 kaynak · 42 parça" seçili; "Arama kayıtları" sekmesinde `0`; ekranda "RAG" ve "lab" yok |
| F11 | Olmayan alan (K10, G3, S-12) | component | 2 test:<br>• `givenUnknownWorkspaceId_whenOpenSearch_thenShowsWorkspaceNotFound`<br>• `givenNonNumericWorkspaceId_whenOpenSearch_thenShowsNotFoundWithoutRequest` | • 999 → MSW 404 `WORKSPACE_NOT_FOUND` → "Çalışma alanı bulunamadı" + bağlantı `/w/42/arama`<br>• `/w/abc/arama` → aynı ekran; `GET /api/workspaces/abc` hiç çağrılmaz |
| F12 | Alan değiştirme (K9, S-11) | component | `givenSourcesPage_whenSelectKiyiNet_thenNavigatesAndListsKiyiNetSources` | `/w/42/kaynaklar`'da "Kıyı Net" seçilir → konum `/w/19/kaynaklar`; satırlarda 1191-1193 adları, "Yardım merkezi" (301) yok |
| F13 | Alan değişince sonuç silinir (K9) | component | `givenSearchResult_whenWorkspaceChanged_thenResultIsCleared` | 42'de arama sonucu görünür → 19 seçilir → "Henüz arama yapılmadı", eski hit başlığı yok |
| F14 | Arama gönderimi (K11, K12, K23, D15) | component `SearchPage.test.tsx` | 2 test:<br>• `givenValidQuery_whenPressEnter_thenPostsFullRequestWithAgentChannel`<br>• `givenWhitespaceQuery_whenTyping_thenSearchButtonDisabled` | • Gövde `{query:'TS-999 hata kodu', mode:'HYBRID', filterStrategy:'ITERATIVE_SCAN', efSearch:40, limit:10, channel:'Temsilci paneli', explain:false}`<br>• Kelime seçiliyken gövdede `filterStrategy`, `efSearch`, `explain` yok<br>• `'   '` → "Ara" `disabled`, istek 0<br>• Açıklamada "Dalga Fiber" geçer |
| F15 | Arama sürerken (K13) | component | `givenPendingSearch_whenSubmittingAgain_thenSendsSingleRequest` | Geciken handler; "Aranıyor…" butonu `disabled`, `“q”` + "aranıyor…" görünür, iskelet `aria-busy="true"`; ikinci Enter → istek sayacı 1 |
| F16 | Sık arananlar (K14) | component | `givenSuggestions_whenClickTs999_thenSearchesThatQuery` | 3 buton TASK metinleriyle; "TS-999 hata kodu" tıklanınca gövde `query='TS-999 hata kodu'`, input değeri de aynı |
| F17 | Bilgi satırı (K15) | component | `givenSettingsChanged_whenRendered_thenInfoLineShowsModeLimitAndModel` | Varsayılan "Hybrid (kelime + anlam)", "en fazla 10", "bge-m3@1024"; Kelime + 5 → "Kelime", "en fazla 5" |
| F18 | Gelişmiş ayarlar (K16, K43) | component `SearchSettingsPanel.test.tsx` | 4 test:<br>• `givenKeywordMode_whenOpenSettings_thenVectorSettingsDisabled`<br>• `givenInvalidEfSearch_whenSubmit_thenShowsRuleAndDoesNotSearch`<br>• `givenChangedSettings_whenResetToDefaults_thenRestoresDefaults`<br>• `givenChangedSettings_whenRemount_thenSettingsRestoredFromStorage` | • Panel başta kapalı (`details` `open` yok)<br>• Kelime → filtre radyoları, ef_search, plan `disabled`<br>• ef `'5'` → `EF_SEARCH_RULE_MESSAGE` alan altında, `aria-invalid="true"`, istek 0<br>• "Varsayılana dön" → Hybrid/iterative scan/40/10/kapalı<br>• Yeniden mount → VECTOR + 20 korunur<br>• Bütün alanların görünür etiketi var (`getByLabelText`) |
| F19 | Sonuç listesi (K18, S-3) | component `SearchResults.test.tsx` | `givenHybridResult_whenRendered_thenShowsRankTitleTagsSnippetAndSourceLine` | **Fixture:** hit 1 `chunkId 1380`, `chunkNo 31`, heading "TS-999 hata kodu", `matchedBy [KEYWORD, SEMANTIC]`, CONTENT terimi 0-6; hit 2 `[SEMANTIC]`.<br>**Beklenen:**<br>• Başlık satırı `“TS-999 hata kodu”`, "2 sonuç", "toplam 57 ms"<br>• Hit 1'de "anlam" ve "kelime" etiketleri, `<mark>` içinde "TS-999", alt satır "Modem hata kodları tablosu · parça 31 · #1380"<br>• Hit 2'de sadece "anlam" |
| F20 | Sonuç bağlantısı (K22) | component | `givenHit_whenRendered_thenLinksToSourceDetailWithChunk` | Link `href="/w/42/kaynaklar/303?parca=1380"` |
| F21 | Sonuç yok (K19, S-2) | component | `givenKeywordModeNoHits_whenSearch_thenShowsEmptyStateWithSemanticHint` | • Kelime: `“Şifremi unuttum, online işlemlere giremiyorum” için sonuç bulunamadı` + "Anlam ya da Hybrid yöntemiyle tekrar dene."<br>• Hybrid: "Farklı kelimelerle aramayı dene." |
| F22 | Kısa kaldı (K20, S-4) | component | `givenShortVectorBranchWithPostFilter_whenRendered_thenWarnsAndSuggestsIterativeScan` | • `vectorShort: true`, `vectorCount: 4`, `limit: 10`, POST_FILTER → "istenen 10 sonuçtan sadece 4 tanesini buldu (kısa kaldı)" + "iterative scan"<br>• ITERATIVE_SCAN → "yeterli parça olmayabilir" |
| F23 | Teknik detay (K21) | component `SearchTechnicalDetails.test.tsx` | `givenHybridResultWithPlan_whenOpenTechnicalDetails_thenShowsBranchCountsRanksAndPlan` | • Kapalı başlar<br>• Açınca "Kelime kolu" 1, "Vektör kolu" 10, embedding/SQL/toplam ms<br>• Hit 1 satırı: kelime sırası 1, puan `0,1000`, vektör sırası 1, mesafe `0,2345`, RRF `0,0328`<br>• Hit 2 kelime sırası "—"<br>• `vectorPlan` metni `<pre>` içinde "Index Scan using idx_chunks_embedding" |
| F24 | Arama hataları (K6, K24, S-10) | component | `givenSearchError_whenSearch_thenShowsTurkishMessage` (tablo) | 409 `EMBEDDING_MODEL_MISMATCH`, 503 `EMBEDDING_UNAVAILABLE`, 503 `EMBEDDING_MODEL_NOT_FOUND`, ağ hatası → `role="alert"` içinde tablodaki metin; `detail` metni ("Embedding service is not reachable") ekranda yok |
| F25 | Düz metin (K7, G5) | component | `givenHtmlInContentAndName_whenRendered_thenShownAsText` | `content: '<img src=x onerror=alert(1)> TS-999'`, `sourceName: '<b>Kaynak</b>'` → metin olarak görünür; `container.querySelector('img')` ve `b` öğesi null |
| F26 | Arama kayıtları (K25, K26, S-6) | component `SearchEventsPage.test.tsx` | 2 test:<br>• `givenStatsAndEvents_whenOpenSearchEvents_thenShowsIndicatorsAndBadges`<br>• `givenNoEventsInLast7Days_whenOpen_thenRatesShowDash` | • Stats `todayCount 4`, oranlar 0.25/0.25 → "4", "%25", "%25"<br>• Satırlar verilen sırayla (yeniden eskiye), "Temsilci paneli", "Hybrid"<br>• Rozetler: 0 sonuç → "Sonuçsuz" (bad sınıfı), 3/10 → "3" (run), 10/10 → "10" (ok)<br>• `topChunkHeading: null` → "—"<br>• Oranlar null → iki kutuda "—" |
| F27 | Sayfalama (K26) | component | `givenTwoPagesOfEvents_whenClickNext_thenRequestsSecondPage` | `totalPages: 2` → "Sayfa 1 / 2"; "Sonraki" → istek `page=1&size=20`, "Sayfa 2 / 2", "Sonraki" `disabled` |
| F28 | Sayaç güncellenir (K27) | component | `givenSearchCompleted_whenBackOnSearchEvents_thenTabCountAndTableUpdated` | Stats handler aramadan önce `totalCount 0`, sonra `1` döner → sekme rozeti "0" → "1"; tabloda yeni kayıt |
| F29 | Kaynak listesi (K28, K29, D19, D20, G2) | component `SourcesPage.test.tsx` | `givenDalgaSources_whenOpenSources_thenShowsKindsStatusesFooterWithoutUploadButtons` | • Yol "Dalga Fiber › Bilgi kaynakları"<br>• Türler "Web sitesi", "PDF", "CSV", "Talep geçmişi"<br>• Fixture'da 303 `INGESTING`, 304 `FAILED` → rozetler "Hazır", "Hazır", "İşleniyor", "Başarısız"<br>• Altta "Toplam 42 parça · embedding modeli bge-m3@1024"<br>• "Yeni kaynak ekle" ve "Yeni sürüm yükle" butonu yok<br>• 302 satırına tıklama → `/w/42/kaynaklar/302` |
| F37 | Kaynak detayı (K35-K37) | component `SourceDetailPage.test.tsx` | 2 test:<br>• `givenPdfSource_whenOpenDetail_thenShowsInfoBoxesAndFirstTwentyChunks`<br>• `givenMoreChunks_whenClickShowMore_thenAppendsNextTwenty` | **Fixture:** 302'nin `chunkCount`'u 45 ve 45 parça.<br>**Beklenen:**<br>• Yol "Bilgi kaynakları › Dalga T5 modem kullanım kılavuzu"<br>• Kutular: PDF, 3.1, `Dalga-T5-Kullanim-Kilavuzu-v3.1.pdf`, 45, bge-m3@1024, "12 Eki 2026 14:31"<br>• "Yeni sürüm yükle" butonu yok<br>• 20 satır; "Daha fazla göster" → 40 → 45, sonra buton kaybolur<br>• Satırda "chunk 1380" ve token sayısı |
| F38 | Vurgulu parça (K22, K38, G1) | component | 2 test:<br>• `givenParcaQuery_whenOpenDetail_thenHighlightedChunkPinnedFirst`<br>• `givenReplacedChunk_whenOpenDetail_thenShowsChunkGoneNotice` | • `?parca=1380` → "Arama sonucundan açılan parça vurgulandı: #1380"; tablonun ilk satırı 1380 ve vurgu sınıflı<br>• `getChunk` 404 `CHUNK_NOT_FOUND` → "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir."; liste yine görünür |
| F39 | Olmayan kaynak (K10) | component | `givenUnknownSource_whenOpenDetail_thenShowsSourceNotFound` | 404 `SOURCE_NOT_FOUND` → "Kaynak bulunamadı" + bağlantı `/w/42/kaynaklar` |
| F40 | Erişilebilirlik (K43) | component (`vitest-axe`) | `givenPage_whenRendered_thenHasNoAxeViolations` | Sayfalar: SearchPage (sonuçlu + ayarlar açık + teknik detay açık), SearchEventsPage, SourcesPage, SourceDetailPage. Her biri `expect(await axe(container)).toHaveNoViolations()` |
| F41 | Sekmeler (K1, K43) | component | `givenSearchEventsPath_whenRendered_thenTabMarkedCurrentAndKeyboardNavigable` | • `/w/42/arama-kayitlari` → "Arama kayıtları" linki `aria-current="page"`<br>• Tab ile sırayla logo, seçici, 3 sekme<br>• Detayda "Bilgi kaynakları" aktif |
| F42 | Logo (K1) | component | `givenSourcesPage_whenClickLogo_thenGoesToSearch` | "yanıt." → `/w/42/arama` |
| E1 | Hybrid arama + kaynağa gitme + geri (AC-2, S-2, S-5) | e2e `search.spec.ts` | `givenDalgaWorkspace_whenHybridSuggestionAndOpenHit_thenSourceDetailHighlightsChunkAndBackShowsResults` | Adım sırası D9'a uyar: yenileme son arama sonucunu siler, bu yüzden `goBack` yenilemeden önce yapılır.<br>• `/w/42/arama` → "Şifremi unuttum, online işlemlere giremiyorum" butonu<br>• Sonuç listesinde "Online İşlemler parolanı sıfırlama" ve aynı öğede "anlam"<br>• Tıklanır → URL `/w/42/kaynaklar/\d+\?parca=\d+`; "Arama sonucundan açılan parça vurgulandı" görünür; detay URL'si saklanır<br>• `page.goBack()` → aynı sonuç listesi görünür (aynı öğe, "anlam" etiketi)<br>• `page.goForward()` → detay sayfası, vurgu bilgisi görünür<br>• `page.reload()` → URL aynı, vurgu bilgisi ve vurgulu parça satırı yine görünür |
| E3 | Alan değiştirme (AC-2, S-11) | e2e `workspace.spec.ts` | `givenDalgaSelected_whenSwitchToKiyiNet_thenUrlSourcesAndSearchScopedToKiyiNet` | • Seçici "Kıyı Net" → URL `/w/19/arama`<br>• "Bilgi kaynakları" → Kıyı Net kaynak adları görünür (beklenen adlar `GET /api/workspaces/19/sources`'tan okunur); "Yardım merkezi" ve diğer Dalga adları yok<br>• "Arama" → "Şifremi unuttum" Hybrid → her sonucun alt satırındaki kaynak adı Kıyı Net kaynaklarından |
| E4 | Olmayan alan (S-12) | e2e | `givenUnknownWorkspaceUrl_whenOpen_thenShowsNotFound` | `/w/999/arama` → "Çalışma alanı bulunamadı" |
| E5 | Kayıt sayısı (K23, K27) | e2e | `givenSearchFromSite_whenOpenSearchEvents_thenTopRowIsAgentPanelAndCountIncreased` | • Sekme rozetindeki sayı okunur<br>• "TS-999 hata kodu" aranır → rozet +1<br>• "Arama kayıtları" ilk satır "Temsilci paneli", "TS-999 hata kodu" |

Sonraki task'a kalan test numaraları (ilk taslaktan): B8, B9, F30-F36, F43, E2.

### qa canlı kontrolleri (gerçek Ollama + Docker, tarayıcıdan)
qa kullanıcının Ollama servisini durdurmaz. "Ollama kapalı" durumu için backend container'ını `OLLAMA_BASE_URL=http://127.0.0.1:9` ile ayrıca başlatır (ya da compose override), sonra eski haline döndürür. Ekran görüntüleri raporda.

**Senaryolar**
- Q0 (AC-3, K41):
  - `docker compose ... up --build -d` → 3 servis `healthy`.
  - `http://localhost:3000` açılır; `curl -s localhost:3000/api/workspaces/42` JSON döner.
  - `/w/42/kaynaklar` sayfası yenilenince 404 vermez (SPA fallback).
- Q1 (S-1): Dalga Fiber seçili Arama sayfası, "Henüz arama yapılmadı", kayıt sekmesinin sayısı. Boş veritabanında 0 olmalı; TASK-002'den kalan kayıtlar varsa sayı raporlanır, `down -v` sonrası 0 doğrulanır.
- Q2 (S-2), Q3 (S-3), Q4 (S-4, sorgu planı dahil), Q5 (S-5).
- Q6 (S-6): temiz veritabanında 4 arama, rozetler, "Bugün 4", "%25".
- Q10 (S-10'un arama kısmı), Q11 (S-11), Q12 (S-12).

**Diğer kontroller**
- Q-UI:
  - lab ürün ekranıyla yan yana ekran görüntüsü;
  - çerçeve/konsol yok, "RAG"/"lab" metni yok;
  - 1024 px ve geniş ekranda pencereyi dolduruyor;
  - yükleme butonları yok.
- Q-KB: sadece klavyeyle sekmeler, arama, ayarlar paneli, teknik detay ve sonuç bağlantısı.
- Sözleşme uyumu:
  - `/v3/api-docs.yaml` ↔ `docs/api/openapi.yaml`;
  - frontend `model/types.ts` alanları ↔ şemalar;
  - `errorMessages.ts` ↔ errorCode enum'u.
- Komutlar: `npm run build`, `npm run lint`, `npm test`, `npm run test:coverage` (%80), `E2E_BASE_URL=http://localhost:3000 npm run test:e2e`, `OLLAMA_BASE_URL=http://127.0.0.1:9 ./mvnw verify`.

## Deployment planı
**`frontend/Dockerfile` (multi-stage)**
- Build: `node:<D26 ana sürüm>-alpine` → `npm ci` → `npm run build`.
- Runtime: `nginxinc/nginx-unprivileged:<1.29.x>-alpine` (sabit etiket, root değil, 8080). `dist/` → `/usr/share/nginx/html`, `nginx.conf` → `/etc/nginx/conf.d/default.conf`.

**`frontend/nginx.conf`**
- `listen 8080`.
- `location /api/`: `proxy_pass http://backend:8080`, `client_max_body_size 21m` (sonraki task'ın 20 MB yüklemeleri için şimdiden), `proxy_read_timeout 120s`, `proxy_set_header Host $host`, `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`.
- `location /assets/`: `expires 1y`, `add_header Cache-Control "public, immutable"`.
- `location /`: `try_files $uri /index.html`, `add_header Cache-Control "no-cache"`.
- Güvenlik başlıkları:
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: same-origin`
  - `Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'`
- `'unsafe-inline'` sadece style için: React inline style özniteliği; sonraki task'ta ilerleme çubuğu da kullanacak.

**`frontend/.dockerignore`:** `node_modules`, `dist`, `coverage`, `test-results`, `playwright-report`, `e2e`.

**`deployment/docker-compose.yml`:** yeni `frontend` servisi. `postgres` ve `backend` değişmez (8080 rehber ve curl için açık kalır).
- `build.context: ../frontend`
- `ports: ["3000:8080"]`
- `depends_on: backend: condition: service_healthy`
- `healthcheck: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1"]`, interval 10s, retries 5, start_period 10s. İmajda `wget` yoksa devops imajdaki araca göre değiştirir ve raporlar.
- `restart: unless-stopped`

**`deployment/CLAUDE.md`:** frontend satırı gerçek değerlerle; smoke test ve E2E komutları.

**`ollama.md`:** yeni bölüm "Siteyi açma ve ilk arama". Komut çıktıları gerçek çalıştırmadan alınır.
- (a) Docker: `up --build -d`, `ps` (3 servis healthy), `http://localhost:3000` → Dalga Fiber Arama sayfası. "Sık aranan"dan "Şifremi unuttum…" ile ilk arama, sonuçta "anlam" etiketi, Teknik detay, Arama kayıtları sekmesi.
- (b) Geliştirme: `up -d postgres`, backend `./mvnw spring-boot:run`, `cd frontend && npm ci && npm run dev` → `http://localhost:5173`.
- (c) Hata tablosuna eklenecekler: 3000 portu dolu; site açılıyor ama "Sunucuya ulaşılamıyor" (backend kapalı); "Embedding servisine ulaşılamıyor" (Ollama kapalı).

**E2E:** devops, compose ayağa kalkınca `cd frontend && npx playwright install chromium && E2E_BASE_URL=http://localhost:3000 npm run test:e2e` çalıştırır ve sonucu raporlar.

## Açık sorular ve riskler
- **Kullanıcı kararları işlendi:** S1 = B (bölündü), S2 = A (bütün paketler + Fontsource). Açık soru yok.
- **Sonraki task önerisi (TASK.md'yi kullanıcı yazar):** "Arayüz: dosya yükleme ve içe alma ilerlemesi". Kapsam önerisi:
  - TASK-003 Kural 30-34, Kural 35'in "Yeni sürüm yükle" butonu, Kural 43'ün diyalog kısmı;
  - örnek senaryolar 7-9 ve 10'un yükleme kısmı; "yeni sürüm yükleme ve ilerleme" E2E'si;
  - sözleşmeye `SourceResponse.latestJob` (Kural 33);
  - diyalog ve toast bileşenleri; multipart `httpClient` desteği; iş hata kodlarının Türkçesi.
- **Gerçek veride "kısa kaldı":** S-4 TASK-002'de ölçüldü (post-filter vektör kolu 4). Hybrid'de kelime kolu sonuç ekleyebilir; uyarı `vectorShort`'a bağlı olduğu için yine çıkar.
- **Paket sürümleri:** Vite 8 / TS 6 / React Router 7 / Vitest sürümleri kurulumda doğrulanır. Uyumsuzluk varsa frontend-coder en yakın uyumlu sürümü seçip raporlar (yeni paket eklemez).
- **E2E arama kaydı ekler:** Her E2E koşusu arama kayıtlarına birkaç satır ekler. qa'nın S-6 kontrolü (Bugün 4, %25) bu yüzden `down -v` sonrası temiz veritabanında, E2E'den önce yapılır.
- **Kapsam dışı not:** `frontend-conventions` §5 route örnekleri (`/workspaces/:workspaceId/...`, `/lab`) ve §9 `shared/preferences/labPreferences.ts` bu task'ta uygulanmadı. Nedeni TASK Kural 3/5 ve feature sınırı (D8, D10); skill metnini kullanıcı/lead günceller. `deployment/CLAUDE.md`'deki "frontend 3000 (planlı)" satırını devops günceller.
- **Kapsam dışı not:** Kök `CLAUDE.md`'deki rol-yol tablosunda `ollama.md` yok; TASK-002'deki gibi yetki planla devops'a verildi.

## Düzeltme turu 1
Kaynak: frontend-coder'ın bildirimi, lead'in isteği.

| Madde | Plandaki değişiklik |
|-------|---------------------|
| E1'in adım sırası D9 ile çelişiyor. Test önce `page.reload()`, sonra `page.goBack()` yapıp arama sonuçlarını bekliyordu. D9'a ve onay kartına göre yenileme son arama sonucunu siler, bu yüzden geri dönünce "Henüz arama yapılmadı" görünür | "Test senaryoları" tablosundaki E1 satırı yeni sıraya göre yazıldı:<br>1. Sonuca tıkla → vurgu görünür.<br>2. `page.goBack()` → aynı sonuç listesi görünür.<br>3. `page.goForward()` → detay sayfası, vurgu görünür.<br>4. `page.reload()` → URL aynı, vurgu bilgisi ve vurgulu satır yine görünür. |

- Değişmeyenler: D9, sözleşme, onay kartı, frontend kodu.
- Yeniden onay gerekmez.

STATUS: DONE
