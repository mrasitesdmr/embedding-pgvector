TASK: TASK-003
# QA raporu

Kapsam: onay kartındaki bölünmüş kapsam (yükleme, ilerleme, yükleme diyalogları ve yükleme senaryoları bu task'ta yok). Tüm komutlar ve senaryolar kendim çalıştırıldı. Sistem `http://localhost:3000`, üç servis healthy.

## Kullanılan skill'ler
`code-review-and-quality`, `java-code-review`, `springboot-verification`, `frontend-conventions`. Tarayıcı senaryoları için Playwright (Chromium) betikleri kullanıldı. Betikler ve ekran görüntüleri scratchpad'de, repoda değil.

## Kural kontrolü
Kapsam dışı olanlar (K30-34, K35'teki "Yeni sürüm yükle", K43 diyalog kısmı, senaryo 7-9 ve 10'un yükleme kısmı) sayılmadı. Onların yokluğu aşağıda ayrıca doğrulandı.

| # | Kural / Kabul kriteri | Kanıt (kod + test + gerçek sistem) | Sonuç |
|---|------------------------|------------------------------------|-------|
| K1-K2 | Başlık (logo + seçici), 3 sekme, çerçeve/konsol/senaryo yok | Ekran görüntüleri (`q1`, `q6`, `q10`): sahte tarayıcı çerçevesi, Sistem konsolu yok; `a11y.test.tsx`, `App.test.tsx` | ✅ |
| K3 | Her sayfanın adresi, yenileme/yeni sekme, geri/ileri | Betik: `/` → `/w/42/arama`; `?parca=` adresi yeni sekmede ve yenilemede aynı vurgu; geri/ileri çalıştı | ✅ |
| K4-K5 | Kullanıcı adı yok; Türkçe; "RAG"/"lab" yok | Ekran metni taraması: `\bRAG\b\|\blab\b` yok; kaynak kodda da yok | ✅ |
| K6 | Hata Türkçe, ham metin yok | `errorMessages.ts`: 21 HTTP `errorCode` + `NETWORK_ERROR` + fallback; gerçek sistemde 503 → Türkçe `role=alert` | ✅ |
| K7 | Düz metin | `<img onerror>` sorgusu: dialog yok, `main img` = 0; kodda `dangerouslySetInnerHTML` yok; `SearchResults.test.tsx` | ✅ |
| K8-K9 | Seçici "Dalga Fiber · 4 kaynak · 42 parça"; alan değişince veri değişir | Betik: seçici metni doğru; Kıyı Net'e geçince adres `/w/19/kaynaklar`, kaynaklar ve arama sonuçları sadece Kıyı Net'in; E2E E3 | ✅ |
| K10 | Olmayan alan/kaynak | `/w/999/arama` → "Çalışma alanı bulunamadı" + "Varsayılan çalışma alanına dön" (`/w/42/arama`); `/w/42/kaynaklar/99999` → "Kaynak bulunamadı" + dönüş bağlantısı | ✅ |
| K11-K13 | Başlık, Enter, boş/boşluk pasif, 500 sınırı, aranıyor + iskelet | Betik: boşlukta "Ara" pasif, 600 karakter 500'e kesildi, aramada "aranıyor…" + iskelet (ekran görüntüsü `q4a`) | ✅ |
| K14-K15 | 3 sık aranan, bilgi satırı | Ekran görüntüsü: 3 düğme; "Arama: Hybrid · Sonuç: en fazla 10 · Embedding: bge-m3@1024" | ✅ |
| K16 | Gelişmiş ayarlar: 5 ayar, varsayılanlar, Kelime'de pasifler, uyarı, sıfırlama, hatırlama | Kelime'de filtre/ef_search/plan pasif; ef_search=5 → "ef_search 10 ile 1000 arasında bir tam sayı olmalı."; yenilemede Kelime kaldı, geçersiz ef yenilemede 40'a döndü; "Varsayılana dön" çalıştı; panel her açılışta kapalı | ✅ |
| K17 | "Henüz arama yapılmadı" | Açılışta ve yenilemeden sonra görüldü | ✅ |
| K18 | Sonuç: sıra, başlık, etiket, içerik, kaynak · parça no · id, vurgu | Ekran görüntüsü `q3`: `Yardım merkezi · parça 1 · #1374`; "TS-999 hata kodu" `<mark>` | ✅ |
| K19-K20 | Sonuç yok + öneri; kısa kaldı | Kelime + parola sorusu: "0 sonuç … sonuç bulunamadı. Kelime araması sadece aynı kelimeleri bulur. Anlam ya da Hybrid yöntemiyle tekrar dene."; post-filter: "…sadece 4 tanesini buldu (kısa kaldı)… iterative scan yapıp tekrar dene." | ✅ |
| K21 | Teknik detay | Arama düzeyi + sonuç başına sıra, puan, mesafe, RRF, "—" ve plan görünüyor (`q3`, `q4c`) | ✅ |
| K22, K38 | Sonuç → kaynak, parça üstte vurgulu + bilgi | `?parca=1380`: "Arama sonucundan açılan parça vurgulandı: #1380", satır en üstte (`q5`) | ✅ |
| K23 | Kanal "Temsilci paneli" | İstek gövdesi `channel` ve kayıt tablosu | ✅ |
| K24 | Ollama / model hataları | Ollama kapalı → 503, "Embedding servisine (Ollama) ulaşılamıyor… ya da Kelime yöntemiyle ara." Model uyuşmazlığı ve model yok mesajları `errorMessages.test.ts`'te (gerçek sistemde zorlanmadı) | ✅ |
| K25-K27 | Göstergeler backend'de; tablo, rozetler, sayaç | Temiz alanda (id 1) 4 arama: Bugün 4, Sonuçsuz %25, Az sonuç %25; kırmızı "Sonuçsuz", sarı "2", yeşil "10", ilk sonuç "—"; sekme sayacı 0 → 4; E2E E5 | ✅ |
| K28-K29 | Kaynak tablosu | Tür Türkçe (Web sitesi, PDF, CSV, Talep geçmişi), Hazır rozeti, "Toplam 42 parça · embedding modeli bge-m3@1024", satır tıklanınca detay; `main` içinde buton yok | ✅ |
| K35-K37 | Detay | Yol gösterici, ad, 7 bilgi kutusu, 20 parça + "Daha fazla göster" (`q5`) | ✅ |
| K39-K40 | Yeni endpoint'ler ve alanlar sözleşmede | Bkz. Sözleşme uyumu | ✅ |
| K41-K42 | Docker + `npm run dev` proxy, `ollama.md` | Compose ps: 3 servis healthy; site 3000'de; `ollama.md` 5.6 bölümü var (adımları tarayıcıda kendim denedim) | ✅ |
| K43 | Klavye, etiketler | Tab sırası mantıklı; `details` Enter/Space ile açıldı; sonuç bağlantısı Enter ile açıldı; etiketsiz input 0; odak çerçevesi görünür (2px) | ✅ |
| AC-1 | build, lint, test, verify | Aşağıda | ✅ |
| AC-2 | Test altyapısı ve E2E (Hybrid + kaynağa git, alan değiştirme) | Aşağıda | ✅ |
| AC-6 | Bütün HTTP `errorCode`'ların Türkçesi | 21/21 var. Yükleme işinin kendi `errorCode`'ları (`NO_TEXT_EXTRACTED`, `FILE_UNREADABLE`, `INGESTION_INTERRUPTED`) kasıtlı olarak sonraki task'ta (onay kartı) | ✅ |
| AC-7 | `lab.html` görsel dili, tam pencere | Ekran görüntüleri: tam genişlik başlık/sekme, Source Sans 3, aynı rozet ve tablo dili; 1024 px'te yatay taşma yok | ✅ |

### Yükleme kapsam dışı: görünmemesi gerekenler
Kaynaklar tablosunda, kaynak detayında ve sayfa başlıklarında "Yeni kaynak ekle" / "Yeni sürüm yükle" butonu yok (`main button` boş), ilerleme çubuğu ve "Son yükleme başarısız" metni yok. ✅

### Onay kartı "Bittiğini nasıl anlayacaksın" ve örnek senaryolar
| # | Senaryo | Sonuç |
|---|---------|-------|
| 1 | Açılış: Dalga Fiber seçili Arama sayfası, "Henüz arama yapılmadı", sekme sayısı | ✅ `/` → `/w/42/arama`. Not: veritabanı e2e ve QA aramalarından sonra olduğu için bu alanda sekme sayısı 0 değildi; sayaç 0 durumu temiz alan 1'de görüldü ("Arama kayıtları" sayaçsız → 4) |
| 2 | Parola sorusu: Hybrid'de "Online İşlemler parolanı sıfırlama" ilk 10'da "anlam"; Kelime'de "sonuç bulunamadı" + öneri | ✅ Sıra 1 ve 7, "anlam" etiketli. Kelime: 0 sonuç + Anlam / Hybrid önerisi |
| 3 | TS-999 | ✅ İlk dört sonuç TS-999 parçaları; "anlam" + "kelime" etiketli; "TS-999 hata kodu" işaretli; teknik detayda sıra, cosine mesafesi, RRF puanı (ör. #1403: kelime sırası 1, mesafe 0,3117, RRF 0,0328) |
| 4 | Taşınma: post-filter + ef 40 → "kısa kaldı" (4/10); iterative → 10 sonuç; plan | ✅ Plan: `Index Scan using idx_chunks_embedding on chunks (… rows=10.00 …)`. İstek gövdeleri ayarlarla birebir (`POST_FILTER`/`ITERATIVE_SCAN`, `efSearch 40`, `explain`) |
| 5 | Sonuçtan kaynağa | ✅ Parça 1380 üstte vurgulu; yenileyince ve yeni sekmede aynı; geri butonu (yenilemeden) 10 sonucu geri getirdi. Yenileme sonrası geri: sonuç silinmiş + "Henüz arama yapılmadı" (D9 kararıyla uyumlu) |
| 6 | Arama kayıtları | ✅ Temiz alan (Poyraz Telekom, 0 kayıttan başlayarak 4 arama: 2 Hybrid 10 sonuç, 1 Kelime sonuçsuz, 1 post-filter 2 sonuç). Yeniden eskiye, "Temsilci paneli", rozet renkleri doğru; Bugün 4, Sonuçsuz %25, Az sonuç %25. API: `zeroResultRate 0.25` |
| 7 | Ollama kapalıyken | ✅ Ollama durduruldu: Hybrid → 503 + Türkçe mesaj; Kelime → 200, "4 sonuç". **Sistem eski hâline getirildi:** `ollama serve` yeniden başlatıldı (yeni PID 92414, eskisi elle başlatılmış 47852'ydi), `bge-m3` listede, VECTOR arama 200 döndü |
| 8 | Alan değiştirme: Kıyı Net | ✅ Adres `/w/19/kaynaklar`; Kıyı Net kaynakları; arama sonuçları sadece Kıyı Net kaynaklarından. Kaynak detayındayken değişince yeni alanın kaynaklar sayfasına gidiyor |
| 9 | `/w/999/arama` | ✅ |
| 10 | Kaynak ekranları | ✅ Türkçe türler, durum rozetli, alt satırda toplam parça + model; yükleme butonu yok |
| 11 | Klavye | ✅ Yukarıda (K43) |
| 12 | Testler | ✅ Aşağıda |

Çalıştırılamayan senaryo: yok (yükleme senaryoları kapsam dışı). Model uyuşmazlığı hatasını (409) gerçek sistemde zorlamadım (yapılandırmayı değiştirmek gerekir); Vitest'te mesaj doğrulanıyor.

## Sözleşme uyumu
- `docs/api/openapi.yaml` (0.3.0) ile backend'in canlı `/v3/api-docs` çıktısını betikle karşılaştırdım: path + method kümesi, her işlemin response status'ları ve parametreleri, şema ve alan adları, `required` listeleri, alan tipleri aynı; `info.version` 0.3.0 iki tarafta. Tek fark enum'da `null` değerinin yazılış biçimi (sözleşmede `enum` içinde `null`, üretilen dokümanda yok): şekil olarak aynı, OpenAPI 3.1'de eşdeğer, sorun değil.
- `errorCode` kümesi iki tarafta aynı (21 HTTP kodu + yükleme işinin 7 iş kodu). Frontend `errorMessages.ts` 21 HTTP kodunun hepsini kapsıyor.
- Frontend type'ları (`features/*/model/types.ts`, `shared/workspace/types.ts`) sözleşmedeki yeni alanları kullanıyor (`chunkNo`, `limit`, `embeddingModels`, `SearchEventStatsResponse`); gerçek sistemle denendi, tip uyuşmazlığı yok (sayfalarda hata yok, `pageerror` 0).
- Yeni endpoint'ler canlı sistemde çalışıyor: `GET .../search-events/stats` (alan 1: `todayCount 4, zeroResultRate 0.25, lowResultRate 0.25`), `GET .../sources/{id}/chunks/{id}`.

## Komutlar (kendi çalıştırdıklarım)
| Komut | Sonuç | Raporla tutarlı mı |
|-------|-------|--------------------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, 366 test, 0 hata/başarısız, JaCoCo eşiği geçti | Evet (366) |
| `cd frontend && npm run build` | Geçti (tip kontrolü dahil) | Evet |
| `cd frontend && npm run lint` | Geçti, çıktı yok (0 uyarı) | Evet |
| `cd frontend && npm test` | 20 dosya, 208 test geçti | Evet |
| `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | 4 test geçti (2,6 sn) | Evet |
| PIT (backend, yeni sınıflar) | Aşağıda | Evet |

## Mutation testi
- **Backend (PIT):** `ChunkService`, `SearchEventStatsResponse`, `WorkspaceService` için çalıştırdım: 3/3 ve 16/16 mutant öldü (%100), test gücü %100, hayatta kalan yok. Tester'ın raporladığı kapsam ve sonuçla uyumlu.
- **Frontend (Stryker):** Kurulu değil; planda D27 ile kasıtlı (onay kartında da "mutation testi" yapılmayacaklar listesinde). Tester, axe ve MSW kontrollerini elle yaptığını yazmış.

## Uçtan uca
4/4 Playwright testi gerçek sisteme (3000) karşı geçti: Hybrid + kaynağa gitme + geri, alan değiştirme, olmayan alan, arama kaydı sayacı. Tarayıcıdaki ek senaryolar yukarıdaki tabloda.

Yan etki: QA aramaları arama kayıtlarına satır ekledi (alan 42, 19 ve 1). Yeni bir "temiz sistem" kontrolü için `down -v` gerekir; Docker'a dokunmadım.

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|----------|-------------|-------|-------------|
| Optional | `frontend/nginx.conf:21,29` (CSP `font-src 'self'`) + Vite varsayılan `assetsInlineLimit` | Üretim build'inde `@fontsource/jetbrains-mono`'nun küçük alt kümeleri (cyrillic-ext, vietnamese; 4 `@font-face`) CSS içine `data:` URI olarak gömülüyor. CSP bunu engelliyor ve her sayfa açılışında konsola 6 "violates font-src" hatası düşüyor. Türkçe metinde bu alt kümeler kullanılmadığı için görünümde fark yok; sadece konsol gürültüsü ve bir CSP ihlali. Çözüm: `build.assetsInlineLimit: 0` (frontend-coder) ya da CSP'ye `font-src 'self' data:` (devops) | frontend-coder |
| FYI | `handoff/03-test-report.md` | `react-testing` skill'indeki `onUnhandledRequest` örneği msw 3'te yok (`onUnhandledFrame`); tester zaten bildirmiş | lead (skill metni) |
| FYI | Backend, `./mvnw verify` | Satır içi yorum, `System.out`, `printStackTrace`, Lombok, `console.log`, `any`, default export bulunmadı; `GlobalExceptionHandler.java:42` backend `getMessage()`'ı yalnız `detail` alanına yazıyor, frontend bunu göstermiyor | — |
| FYI | `V7__*.sql` | Eski kayıtlar için `result_limit` NULL; stats sorgusu ve rozet mantığı bunu D3'e göre ele alıyor (B14 testi + gerçek sistem) | — |

Gözlenen iyi uygulamalar: alan id'li query key'ler ve `LastSearchProvider key={workspaceId}` ile alan değişince sonucun temizlenmesi; vurgunun segment dizisiyle (HTML üretmeden) yapılması; tek parça endpoint'i sayesinde `?parca=` adresinin yenilemede çalışması; PIT'te yeni sınıflarda hayatta kalan mutant olmaması.

## Düzeltme
Gerekmiyor. `Critical` ya da `Required` bulgu yok; tek bulgu `Optional` (konsol uyarısı).

STATUS: DONE
