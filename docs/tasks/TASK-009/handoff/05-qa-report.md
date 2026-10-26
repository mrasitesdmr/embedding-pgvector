TASK: TASK-009
# QA raporu

## Kullanılan skill'ler
`code-review-and-quality`, `frontend-conventions`, `frontend-a11y`, `e2e-testing`, `writing-good-tests` (ölçütler olarak).

## Kural kontrolü
| # | Kural / Kabul kriteri | Kanıt (kod + test) | Sonuç |
|---|---|---|---|
| 1 | Sonuca tıklama/Enter → panel, sayfa değişmez | `SearchHitItem` (`button`) + `SearchHitList` `openHit`; SR1, SP2; EP1 (URL `/arama` kalıyor). Elle: panel açıldı | ✅ |
| 2 | Başlık / "Başlıksız parça", kaynak adı, parça no, id, tam metin, "…" yok | `SearchHitDialog`; HD1, HD2; EP1 (md5 panel = REST = DB). Elle ekran görüntüsü: "parça 6 · #6249" | ✅ |
| 3 | Satır/paragraf yapısı, panel içinde kayar, taşmaz | `pre-wrap`, `.content` overflow; EP1 360×640 ölçümü | ✅ |
| 4 | Kelime vurgusu tüm metinde | `hitContentSegments`; HT1, HT2, HD3; EP2 | ✅ |
| 5 | anlam/kelime etiketleri | `SearchHitTags`; HD4; ekran görüntüsünde "anlam" | ✅ |
| 6 | × / Esc / dışına tıklama kapatır | `Dialog` `hasCloseButton`, `closeOnBackdropClick`; DT1, DT3, HD10, SP3; elle Esc | ✅ |
| 7 | Kapanınca sonuçlar aynı, yeni arama/kayıt yok, odak sonuca döner | `returnFocusRef`; DT4, SP1 (POST sayacı 1); EP1 (DB `search_events` sayısı sabit); elle odak kontrolü: `true` | ✅ |
| 8 | Arka sayfa kaymaz, odak panel dışına çıkmaz | `index.css` `:has(dialog[open])`; DT5, SP2; EP1 (`scrollY` sabit), EP3 | ✅ |
| 9 | "Kaynakta göster" → kaynak detayı, parça vurgulu | `Link` `?parca=`; HD9, SP4; EP1 | ✅ |
| 10 | Kaynak detayında vurgulu parça tam metin, diğerleri önizleme | `ChunkTable` `fullText`; CT1; EP1 | ✅ |
| 11 | Parça yok → Türkçe mesaj, sayfa bozulmaz | `useChunkQuery` + `isChunkGoneError` + `messageForError` (yeni kod/metin yok); HD6, HD7, HD8; EP4 | ✅ |
| 12 | HTML/script metin olarak gösterilir | `dangerouslySetInnerHTML` yok (grep boş); HD5 | ✅ |
| AC | Panel axe | HD11 (metin ve "parça yok" durumları) | ✅ |
| AC | Açılıp kapanınca yeni arama kaydı yok | SP1 + EP1 (DB sayımı) | ✅ |
| AC | Tüm komutlar hatasız (build, lint, unit, e2e) | Aşağıya bak: e2e tam koşu 3/4 kaldı | ❌ |

## Sözleşme uyumu
`openapi.yaml` değişmedi, backend değişmedi. `shared/chunk` `ChunkResponse`/`getChunk` gövdeleri eskisiyle aynı; EP1 REST cevabını şemaya karşı doğruluyor (`expectMatchesSchema`). Yeni `errorCode` eklenmedi; `CHUNK_NOT_FOUND`/`SOURCE_NOT_FOUND`/`WORKSPACE_NOT_FOUND` mevcut eşlemeden geliyor. Sapma yok.

## Komutlar (kendi çalıştırdıklarım)
| Komut | Sonuç | Raporla tutarlı mı |
|---|---|---|
| `cd frontend && npm run build` | geçti | evet |
| `npm run lint` | geçti, uyarı yok | evet |
| `npm test` | 32 dosya / 470 test geçti | evet |
| `npm run test:coverage` | satır %96,47, branch %93,17 (eşik %80) | evet |
| `npx playwright test` (Docker sitesi, :3000, Ollama açık) — 4 tam koşu | 1. koşu: 21 geçti, 2 atlandı, 0 kaldı. 2., 3. ve 4. koşu: **test 13 kaldı**, ardındaki 8 test (EP1-EP4 dahil) koşmadı | **hayır** (tester "tekrar etmedi" demişti; 4 koşunun 3'ünde tekrar etti) |
| `npx playwright test --grep-invert givenVectorSearch_whenOpenSearchEventsPage` | 20 geçti, 2 atlandı, 0 kaldı (EP1-EP4 dahil) | — |
| `./mvnw verify` | koşulmadı (backend değişmedi) | evet |

### Test 13 (flaky şüphesi) incelemesi
Test: `givenVectorSearch_whenOpenSearchEventsPage_thenEventStatsAndPopularMatchDatabase` (`document-flow.spec.ts:516`). Hata: `expectTopThreeContain(page, 'Yıllık izin')` false. Kök neden: kendisinden önceki testlerin bıraktığı "Uzun belge" (500 parça, hepsi zeytinyağı metni) çalışma alanında kalıyor. Anlam aramasında "Yıllık izin kaç gün?" için ilk 10'un tamamı bu belgeden geliyor (canlı API ile doğruladım: 10/10 sonuç kaynak 10003, mesafe ≈0,68). Yani test sırayla çalışırken veri durumuna ve HNSW grafiğinin oluşumuna bağlı. Bu TASK-009 kodundan bağımsız (test paneli açmıyor, uygulama kodu aramaya dokunmuyor). Ama test paketi güvenilir yeşil değil ve ondan sonra gelen EP1-EP4 de koşmuyor; bu yüzden kabul kriteri "e2e geçiyor" sağlanmıyor. Ürün tarafı için FYI: ef_search=40 ile büyük, birbirine benzer bir parça kümesi varken ilgili parça sonuçtan düşebiliyor (recall); TASK-009 kapsamı dışı.

## Mutation testi
Stryker kurulu değil (frontend/CLAUDE.md "Henüz olmayanlar"); planda yok. Tester'ın elle mutasyon tablosunu gözden geçirdim: kritik koşullar (`isChunkGoneError`, backdrop `target === currentTarget && canClose`, `returnFocusRef`, `handleNativeClose`, `ChunkTable` vurgulu dalı) için adlandırılmış, davranışa bakan testler var (`writing-good-tests` ile uyumlu). Gerçek mutation koşusunun yokluğu bu task için bloklayıcı değil; Optional: Stryker ayrı task olarak planlanabilir.

## Uçtan uca
Test 13 hariç tüm koşular (EP1 örnek belge → arama → tıkla → tam metin → kapat → "Kaynakta göster" → geri; EP2 "masraf beyanı" vurgu; EP3 klavye; EP4 yeni sürüm sonrası "parça artık yok") geçti. 21 MB gövde testleri nginx'te geçti.
Elle denemeler (çalışan sistemde, Playwright betiğiyle; betik repoya eklenmedi):
- "Yıllık izin kaç gün?" (anlam): ilk sonuç panelde tam metinle açıldı, Esc sonrası odak ilk sonuçta (`true`), sonuçlar yerinde. Panel görünümü lab düzeniyle uyumlu (başlık+×, etiket+kaynak satırı, kaydırılabilir metin, Kapat/Kaynakta göster).
- "Yeni sürüm yükle" penceresi: eski görünüm, bozulma yok; dışına tıklamayla kapanmıyor. "Yeni kaynak ekle" penceresi mevcut testlerle (geçti) ve aynı `Dialog` varsayılanlarıyla çalışıyor.
- 360 px: EP1 kutu ölçümü (ekran içinde, metin panelde kayıyor).
- Arama kayıtları: panel açılıp kapanınca sayı değişmiyor (EP1 DB sayımı).

## Kod review bulguları
| Severity | Dosya:satır | Bulgu | Sorumlu rol |
|---|---|---|---|
| Required | `frontend/e2e/document-flow.spec.ts:516` | Test 13 önceki testlerin bıraktığı 500 parçalık "Uzun belge" verisine bağlı; 4 koşunun 3'ünde kalıyor ve ardındaki EP1-EP4'ü engelliyor. Kabul kriteri "e2e geçiyor" sağlanmıyor | tester |
| FYI | Dialog.handleNativeClose | StrictMode'da açılışta kapanma hatasına gerçek neden bulunmuş, tek satır koşulla çözülmüş; mevcut diyaloglar etkilenmiyor (StrictMode testi var). Sapma kabul | — |
| FYI | Dialog.tsx lint yorumu | `<dialog onClick>` için tek satırlık `oxlint-disable` gerekçeli (klavye karşılığı Esc). Kabul | — |
| FYI | returnFocusRef | Değer effect başında okunuyor; panel açıkken değişmediği için davranış planla aynı, odak dönüşü test ve elle doğrulandı. Kabul | — |
| Optional | `e2e/` | Stryker/gerçek mutation koşusu yok; ayrı task | planner |
| FYI | arama (ürün) | Büyük benzer-parça kümesinde HNSW recall düşüşü (yukarıda) | — |

## Düzeltme
**tester:**
1. `document-flow.spec.ts` test 13'ü (ve gerekiyorsa ondan önceki testleri) veri durumundan bağımsız hale getir: ör. "Uzun belge" ve eşzamanlı yükleme testlerinin kaynaklarını testlerin sonunda sil ya da test 13'ten önce temizle; ya da test 13 kendi ihtiyacını (örnek belge) temiz bir çalışma alanında kurar. Hedef: `npx playwright test` üst üste en az 3 kez, 0 kaldı.
2. Raporuna `## Düzeltme turu 1` ekle (3 koşunun sonucu dahil).
Not: uygulama kodunda değişiklik gerekmiyor; sadece tester → qa yeniden çalışır.

## Düzeltme turu 1
| Madde | Kanıt | Sonuç |
|---|---|---|
| tester: test 13 veri durumundan bağımsız olsun, `npx playwright test` 3 kez 0 kaldı | `document-flow.spec.ts:127` `removeLargeSourcesLeftByEarlierTests()` (10001 ve 10002 dışındaki kaynak/parça/işleri siler), test 13 Arrange'inde çağrılıyor (satır 526). Sadece e2e dosyası değişti. Ben Docker sitesine karşı 3 kez koşturdum: üçünde de 21 geçti, 2 atlandı (`ollama-down`), 0 kaldı; EP1-EP4 dahil. | ✅ |

Yeniden doğrulama (adım 1-7): build, lint, `npm test` (470 geçti) ilk turdaki gibi; uygulama kodu değişmedi (sadece e2e dosyası). Kural tablosundaki son satır ("komutlar hatasız") artık ✅. Açık Required bulgu yok. Optional: Stryker ayrı task olarak planlanabilir.

### Düzeltme turu 1: ek kontroller (lead isteği)
- **Test değişikliği review:** `removeLargeSourcesLeftByEarlierTests()` sadece test 13 Arrange'inde ve 10001 (örnek PDF) ile 10002 (test 19'un ad çakışması için gereken) dışındaki kaynak, parça ve işleri siliyor. Sonraki testler (14-23) üç tam koşuda geçti, yani bu temizlik onların verisini bozmuyor. Test 13 artık "Uzun belge" gibi artıklara bağlı değil. Optional: `KEPT_SOURCE_IDS` sabit id'lere dayanıyor; global-setup id sırasını sıfırladığı sürece sorun yok.
- **E2E:** Docker sitesine karşı 3 tam koşu: 21 geçti / 2 atlandı / 0 kaldı (her seferinde).
- **"Yeni kaynak ekle" penceresi (elle, ekran görüntüsü):** başlık, açıklama, "Kaynak adı" alanında odak halkası, dosya alanı, Vazgeç / Kaynağı ekle düzgün; eski görünümle aynı. Dışına tıklayınca kapanmıyor (yükleme pencereleri için kararlaştırılan davranış), Esc kapatıyor. "Yeni sürüm yükle" penceresi de ilk turda aynı şekilde doğrulandı.
- **360 px panel (elle):** panel kutusu x 16,5 / y 10 / 327×620; ekran görüntüsünde başlık, ×, etiket, kaynak satırı, kayan metin, Kapat ve Kaynakta göster ekrana sığıyor, yatay taşma yok.
- **Arama kaydı sayısı (elle, DB `search_events`):** panel Esc ile, sonra "Kapat" düğmesiyle açılıp kapatıldı; sayı 17 → 17, değişmedi.


## Düzeltme turu 1 – elle kontroller
Çalışan Docker sitesinde (http://localhost:3000), Playwright betiğiyle elle denendi (betik repoya eklenmedi):
| Kontrol | Sonuç |
|---|---|
| (a) "Yeni kaynak ekle" penceresi görünüm | Başlık, açıklama, odaklı "Kaynak adı", dosya alanı, Vazgeç/Kaynağı ekle düzgün; eski görünümle aynı (ekran görüntüsü incelendi) ✅ |
| (a) Dışına tıklama | Pencere AÇIK kaldı (beklenen: kapanmaz) ✅ |
| (a) Esc | Pencere kapandı ✅ |
| (a) Arka sayfa kayması | Pencere açıkken tekerlek sonrası `scrollY` 0 → 0, `<html>` `overflow: hidden` ✅ |
| (b) 360×640 panel | Kutu x 16,5 / y 10 / 327×620 (ekran içinde); metin bölgesi `scrollHeight 1317 > clientHeight 416`, `scrollWidth 293 = clientWidth 293`; tekerlekle bölge içi `scrollTop` 500'e kaydı, sayfa `scrollY` değişmedi (705 → 705; 0 → 705 sıçraması Playwright'ın tıklamadan önce düğmeyi görünür alanına kaydırmasından, panel açıkken değil) ✅ |
| (c) `search_events` sayısı | Panel Esc ve "Kapat" ile açılıp kapandı: DB'de 17 → 17 ve 18 → 18 (iki bağımsız deneme; arama kendisi 1 kayıt ekler, panel eklemez). Arama kayıtları sayfası açıldı, rozet ve DB uyumlu ✅ |

**Sabit id (10001/10002) review'u:** `KEPT_SOURCE_IDS` ve `OLD_SAMPLE_QUESTION_CHUNK_SOURCE` zaten dosyadaki diğer testlerin (EP1, `sources/10001/chunks/…`) kullandığı varsayıma dayanıyor; global-setup her koşuda dizileri sıfırlıyor (`ALTER SEQUENCE`), dolayısıyla id'ler deterministik ve 3 tam koşuda doğrulandı. Hata durumunda (id değişirse) test sessizce yanlış şeyi silmek yerine EP testleri belirgin şekilde kırılır. Sınıflandırma: **Optional** (isim/ad ile bulmak daha dayanıklı olurdu); işi durdurmaz. Sorumlu rol: tester.

Açık Critical/Required bulgu yok.

STATUS: DONE
