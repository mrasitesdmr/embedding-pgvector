TASK: TASK-004
# QA raporu

## Kullanılan skill'ler
`code-review-and-quality`, `java-code-review` / `springboot-verification` (backend `latestJob` ekleme için komut ve diff taraması), `frontend-conventions`, `frontend-a11y`, `web-design-guidelines` (yeni diyaloglar), `writing-good-tests` (mutation sonucu), `e2e-testing`, `webapp-testing` (Playwright ile elle senaryolar).

Not: Proje git deposu değil, `git diff` yok. Değişiklikler handoff dosyaları ve kaynak okunarak incelendi.

## Kural kontrolü
Sistem temiz veritabanıyla açıldı (`down -v` + `up --build -d`; postgres, backend, frontend `healthy`). Elle senaryolar kendi Playwright betiklerimle gerçek tarayıcıda, gerçek Ollama (`bge-m3`) ve PostgreSQL'e karşı koşuldu.

| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç |
|---|------------------------|--------------------|-------|
| 1 | "Yeni kaynak ekle" butonu + diyalog (ad zorunlu, dosya) | `CreateSourceDialog.tsx`; test `CreateSourceDialog.test.tsx`; elle: diyalog "Kaynak adı" + "Dosya" | ✅ |
| 2 | Dosya türü/20 MB/boş/ad kuralı, uyarı alanın altında | `uploadRules.ts`; elle: `.docx` "Bu dosya türü desteklenmiyor…", 25 MB ve 20 MB+1 B "Dosya 20 MB'tan büyük olamaz.", boş `.txt` "Dosya boş…", tam 20 MB uyarısız; boş/tek harfli ad "Kaynak adı 2-80 karakter olmalı."; 0 POST gitti | ✅ |
| 3 | Backend reddi `errorCode` ile Türkçe | `uploadFormErrors.ts`, `errorMessages.ts`; `CreateSourceDialog.test.tsx` (SOURCE_NAME_TAKEN, UNSUPPORTED_FILE_ENCODING, FILE_TOO_LARGE, SOURCE_LIMIT_REACHED) | ✅ |
| 4 | Yükleme başlayınca diyalog kapanır, kaynak "İşleniyor" | elle: `yardim-notlari.md` ile ekleme → diyalog kapandı, satır "Parçalara bölünüyor · İşleniyor" | ✅ |
| 5 | "Yeni sürüm yükle" sadece dosya kaynaklarında, satırda + detay başlığında | elle: PDF ve CSV'de 1 buton (satır + CSV detayı); Web sitesi ve Talep geçmişi satır ve detayında 0; `SourcesPage.test.tsx`, `SourceDetailPage.test.tsx` | ✅ |
| 6 | Diyalog içeriği (ad, sürüm, parça, dosya adı/boyut, etiket, bilgi metni) | elle: "Dalga T5 modem kullanım kılavuzu · şu an v3.1 · 5 parça", "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür.", seçilen "…v3.2.pdf 32,7 KB", etiket alanı | ✅ |
| 7 | Dosya kuralları sürüm diyaloğunda da | elle: PDF kaynakta `accept=".pdf"`, `.md` seçilince "Bu kaynağa sadece PDF dosyası yüklenebilir." | ✅ |
| 8 | İş sürerken buton pasif; "iş sürüyor" hatası Türkçe | elle: iş sürerken `isDisabled() = true`; `INGESTION_IN_PROGRESS` metni var; `UploadVersionDialog.test.tsx` | ✅ |
| 9 | Aşama metni + çubuk + "İşleniyor" rozeti | elle (Ollama yük altında yavaşlatıldı): "Dosya okunuyor" → "Embed ediliyor · 0/12 parça" → toast; `IngestionProgress.test.tsx` 5 aşamayı sırayla doğruluyor | ✅ |
| 10 | ~1 sn sorgu, bitince durur | elle: iş bitince 3 sn boyunca 0 yeni `ingestion-jobs` isteği; test: toplam 5 istek, artmıyor | ✅ |
| 11 | DONE: liste yenilenir, satır vurgusu, toast | elle: toast "Dalga T5 modem kullanım kılavuzu v3.4 hazır · 12 parça"; hücre arka planı vurgu rengi (`rgb(255,248,232)`) sonra 3,5 sn'de şeffaf; toast ~6 sn'de kayboldu; detay sayfası sürüm 3.2→yeni, parça, parça listesi yeni (chunk 1411…) | ✅ |
| 12 | FAILED: "Son yükleme başarısız: <neden>"; 7 iş kodunun Türkçesi | `errorMessages.ts` 7 kod (`NO_TEXT_EXTRACTED`, `FILE_UNREADABLE`, `EMBEDDING_UNAVAILABLE`, `EMBEDDING_MODEL_NOT_FOUND`, `EMBEDDING_MODEL_MISMATCH`, `INGESTION_INTERRUPTED`, `INTERNAL_ERROR`) = sözleşme enum'u; elle: görsel PDF ve Ollama kapalı (aşağıda) | ✅ |
| 13 | Yenileme / yeni sekmede iş görünür | elle: yükleme sırasında `page.reload()` → "Embed ediliyor · 0/12 parça" + çubuk devam; E1 | ✅ |
| 14 | Yükleme sırasında arama ve diğer sayfalar çalışır | elle: iş sürerken arama chunk 1423/1425 (eski sürüm) döndü; iş bitince 1435/1437 (yeni). Arama sayfasına geçip orada toast alındı | ✅ |
| 15 | Odak diyaloğa, Esc/Vazgeç, odak butona döner, Tab dışarı çıkmaz | elle (gerçek klavye): odak "Kaynak adı"na; Tab döngüsü `file > Vazgeç > Kaynağı ekle > ad > file…` hiç OUTSIDE yok; Shift+Tab ters döngü; Esc kapatır, odak açan butonda; sürüm diyaloğunda odak satırdaki butona döndü | ✅ |
| 16 | Form alanı etiketleri, uyarılar ekran okuyucuya | `FileField.tsx` `<label htmlFor>`, `role="alert"`, toast `role=status`; `a11y.test.tsx` axe (5 test); elle `getByLabel` ile tüm alanlar bulundu | ✅ |
| 17 | Akış sadece klavyeyle | elle: Enter ile aç, ad yaz, Tab ile dosya alanına, dosya seç, Tab×2 ile "Kaynağı ekle", Enter → yüklendi | ✅ |
| 18 | `latestJob` backend + sözleşme | `SourceService`, `SourceResponse`, `IngestionJobRepository#findLatestBySourceIds` (tek sorgu); canlı `/v3/api-docs` ↔ `openapi.yaml` birebir | ✅ |
| 19 | CSP `font-src` konsol hatası yok | `vite.config.ts` `assetsInlineLimit: 0`; nginx CSP değişmedi; elle: Arama, Kaynaklar, Detay, yükleme akışı boyunca konsolda 0 mesaj; E5 | ✅ |
| AC | Bittiğini nasıl anlayacaksın 1-10 | 1 açılış ✅; 2 modem 3.2 (3.3, 3.4 ile tekrar) ✅; 3 arama ✅; 4 yeni kaynak + kelime araması (`modemi kapatıp 30 saniye sonra yeniden aç` → "QA yardım notları" parçası) ✅; 5 ✅; 6 ✅; 7 ✅; 8 ✅; 9 ✅; 10 ✅ | ✅ |
| Karar | Başarısız yeni sürüm: kaynak "Hazır" kalır, neden satırda, yenilemede de kalır, yeni yüklemeyle silinir; eski işler için toast yok | elle: görsel PDF ve Ollama kapalı sonrası kaynak `READY`/`3.4`/12 parça, neden satırda + detayda + yenilemeden sonra; yeni yüklemede "Dosya okunuyor"a döndü; bitmiş işte sayfa açınca toast çıkmadı; başarısızlıkta toast yok | ✅ |

### Elle senaryo sonuçları (kısa)
- **Modem kılavuzu:** 3.1 → 3.2 / 3.3 / 3.4. Gerçek Ollama ile iş çok hızlı (0,4–1,4 sn, 12 parça); aşamaları gözlemlemek için Ollama'ya paralel embed isteği yükledim. "Dosya okunuyor" (~160 ms) → "Embed ediliyor · 0/12 parça" (yenileme sonrası) → toast.
- **Ollama kapalı:** Ollama durduruldu, PDF yüklendi: satırda ve detayda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor", kaynak `READY`, sürüm 3.4, eski parçalar (1435, 1437) aranabilir.
- **Görsel PDF** (elle yazdığım, sadece resim içeren PDF): "Son yükleme başarısız: dosyada metin yok (sadece görsel içeren PDF ya da sadece başlık satırı olan CSV olabilir)", toast yok.
- **Başka sayfada bildirim:** `.md` yüklenirken Arama sayfasına geçildi; orada "QA yardım notları hazır · 3 parça" çıktı (sürümsüz biçim doğru).

## Sözleşme uyumu
- Canlı `/v3/api-docs` ile `docs/api/openapi.yaml` (JSON'a çevrildi) karşılaştırıldı: `info.version` 0.4.0 = 0.4.0; path kümesi aynı; her şemada `properties` ve `required` kümesi aynı; `errorCode` enum'u 21 = 21, fark yok.
- `SourceResponse.latestJob`: canlıda `anyOf: [IngestionJobResponse, {type: null}]`, `required` dışında. Uyumlu.
- Frontend: `SourceResponse.latestJob` tipi ve çağrılar sözleşmeyle uyumlu; `httpClient` FormData'da JSON `Content-Type` koymuyor (gerçek tarayıcıda yükleme çalıştı).
- Türkçe karşılıklar: 21 HTTP `errorCode`'unun hepsi `ERROR_MESSAGES`'ta (+`NETWORK_ERROR`); 7 iş hata kodunun hepsi `INGESTION_FAILURE_REASONS`'ta.

## Komutlar (kendi çalıştırdıkların)
| Komut | Sonuç | Raporla tutarlı mı |
|-------|-------|--------------------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, 377 test, 0 hata/başarısız, JaCoCo eşiği geçti | Evet |
| `cd frontend && npm run build` | Geçti (`tsc -b` + `vite build`) | Evet |
| `cd frontend && npm run lint` | Geçti, 0 uyarı/hata | Evet |
| `cd frontend && npm test` | 361 test geçti | Evet |
| `docker compose -f deployment/docker-compose.yml down -v && up --build -d` | 3 servis `healthy` | Evet |
| `E2E_BASE_URL=http://localhost:3000 npm run test:e2e` | 10 geçti (4 TASK-003 + 6 yeni), 8,9 sn | Evet |
| E1 (yeni sürüm + yenileme) 6 ek koşu | 6/6 geçti; yenileme sonrası çubuk 93-109 ms'de görünüyor | Evet |

## Mutation testi
- **Backend (PIT, `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage`, hedef: `SourceService`, `OpenApiConfig`):** 33 mutant, 13 öldü (%39). `SourceService` 10/10 öldü. 20 hayatta kalan mutantın hepsi `OpenApiConfig`'te (`nullableLatestJobCustomizer` satır 72/76, `requiredMultipartPartsCustomizer`, `problemDetailSchemaCustomizer`, `nullableReference`, `violationSchema`). Tester'ın açıkladığı PIT + önbellekteki Spring context sınırı geçerli. Etkisini bağımsız doğruladım: canlı `/v3/api-docs` çıktısında `latestJob` şeması ve `required` doğru ve `openapi.yaml` ile birebir; yani mutant bazında test yok ama davranış gerçek sistemde doğrulandı. Çoğu hayatta kalan mutant bu task'tan önceki customizer'larda. Skor eşiği `pom.xml`'de tanımlı değil, build'i etkilemiyor.
- **Frontend (Stryker):** Kurulu değil. Skor yok.

## Uçtan uca
Yukarıdaki 10 Playwright testi geçti (yeni sürüm + yenileme, yeni kaynak + kelime araması, geçersiz dosya, klavye diyalogları, CSP). Ek olarak elle senaryoların hepsi koşuldu (Kural kontrolü bölümü).

**E1 kırılganlığı (tester notu a):** E1'in "iş bitmeden yenileme" penceresi gerçek Ollama'da işin süresi kadar. Bu makinede toplam 1,4 sn (12 parça) ve sonradan yaptığım doğrudan ölçümde işin kendisi 0,4 sn'ye kadar kısalabiliyor (backend `startedAt`→`finishedAt` 0,37 sn). Test yenileme sonrası çubuğu 100 ms içinde arıyor; 12 koşuda sorun çıkmadı, ama makine/Ollama daha da hızlanırsa ya da CI yavaş çalışırsa kırılabilir. Zorunlu değil, Optional olarak yazıldı.

**Ortam durumu:** Sistem açık bırakıldı: `postgres`, `backend`, `frontend` `healthy`. Ollama'yı Ollama-kapalı senaryosu için kapattım, sonra `ollama serve` ile yeniden başlattım (bana devredildiği hâle döndü: tester'ın başlattığı gibi açık; günlük `/private/tmp/claude-501/ollama-qa.log`). Ollama task'tan önce kapalıydı; kapatmak isterseniz `ollama serve` sürecini sonlandırmak yeterli. Veritabanında çalışma alanı 42'de QA'nın eklediği "QA yardım notları" kaynağı ve E2E'nin eklediği bir kaynak var; modem kılavuzu E2E koşusu sonrası 3.2 sürümünde (12 parça). Kaynak silme kapsam dışı.

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|----------|-------------|-------|-------------|
| Optional | `frontend/src/features/source/components/FileField.tsx:36` | Kural 5 (Türkçe arayüz): yerel `<input type="file">` düğmesi ve "No file chosen" metni tarayıcının dilinden geliyor (headless Chromium en-US'te "Choose File / No file chosen"; Türkçe tarayıcıda Türkçe gelir). Diyalogdaki tüm uygulama metni Türkçe. Düzeltmek için özel "Dosya seç" düğmesi + gizli input gerekir, klavye/a11y davranışını yeniden kurmak demek; riski kazancından büyük. Severity Optional: kullanıcı tarayıcı dili Türkçe ise sorun yok; TASK'ta bu kural dosya seçici için ayrıca istenmedi. İstenirse ayrı task | frontend-coder (ayrı task) |
| Optional | `frontend/e2e/upload.spec.ts` (E1) | Yenileme penceresi işin süresine bağlı (0,4–1,4 sn). Kırılganlık riski gerçek ama 12 koşuda tutarlı geçti. Kalıcı çözüm için yavaşlatılmış embedding gerekir (kapsam dışı) | tester |
| Optional | `backend/.../config/OpenApiConfig.java` (satır 60-122) | PIT'te 20 mutant hayatta (Spring context önbelleği). Davranış canlı sistemde doğrulandı. Bir sözleşme-karşılaştırma testi (üretilen şemadan `required`/`anyOf` kontrolü) mevcut; mutant bazında kapatmak için context'siz birim test yazılabilir | tester |
| FYI | `frontend/src/test/setup.ts` | `showModal` taklidi, `FormData` uyum katmanı ve `LocationProbe` yalnızca vitest `setupFiles`'ında ve `src/test/**`'te. Üretim kodundan hiçbir import yok (`grep`), `dist` içinde `LocationProbe`/taklit izi yok; gerçek davranış E4/E2E'de gerçek tarayıcıda doğrulandı | — |
| FYI | `frontend/src/test/LocationProbe.tsx` | `useEffect` → `useLayoutEffect` düzeltmesi mantıklı (kök neden testteydi), tam takım 361 test yeşil | — |
| FYI | `IngestionWatcherProvider.tsx` | Ref tabanlı "görülen/bitmiş iş" takibi karmaşık ama davranışı (eski işte toast yok, bir kez toast, iş bitince polling durur) elle doğruladım; `console.log`, `any`, ölü kod yok | — |
| FYI | Frontend mutation | Stryker kurulu değil; skor ölçülemedi | — |

Critical ya da Required bulgu yok. `console.log`/`debugger`/`any`/`dangerouslySetInnerHTML` taraması üretim kodunda temiz.

## Düzeltme
Yok.

STATUS: DONE
