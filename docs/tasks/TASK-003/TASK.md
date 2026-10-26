# TASK-003: Arayüz: arama, arama kayıtları ve bilgi kaynakları ekranları

## Ne isteniyor
`lab.html`'deki "yanıt." bilgi bankası ürünü gerçek bir web sitesi olarak React ile yapılır ve TASK-002'deki backend'e bağlanır. Site, tarayıcı penceresinin tamamını kullanan normal bir web sitesidir. Kullanıcı bir çalışma alanı seçer, bilgi bankasında arar, sonuçların hangi yolla (kelime / anlam) bulunduğunu ve isterse teknik detayını görür, arama kayıtlarını ve göstergelerini izler, kaynakları görüntüler ve dosya yükleyip içe alma ilerlemesini takip eder.

Arama ayarları (yöntem, filtre, `ef_search`) arama sayfasındaki katlanır bir panelde bulunur.

Bu projede LLM ile cevap üretme yoktur; sadece arama vardır. Canlı güncelleme için WebSocket kullanılmaz; içe alma ilerlemesi iş durumu sorgulanarak (polling) izlenir.

## Kurallar

### Genel yerleşim
1. Site, `lab.html`'deki "yanıt." ürün ekranının yapısındadır ve tarayıcı penceresinin tamamını kullanır:
   - **Başlık:** "yanıt." logosu (tıklanınca Arama sayfasına gider) ve çalışma alanı seçici.
   - **Sekmeler:** başlığın altında yatay sekmeler: Arama, Arama kayıtları, Bilgi kaynakları.
   - **İçerik:** sekmelerin altında sayfa içeriği.

   Renkler, yazı tipleri (Source Sans 3, JetBrains Mono), tablolar, durum rozetleri, diyaloglar ve bildirimler (toast) `lab.html`'den alınır.
2. `lab.html`'de ürün ekranına ait olmayan şeyler yapılmaz:
   - **Sahte tarayıcı çerçevesi:** pencere noktaları, adres çubuğu ve gri dış zemin. Sayfanın adresi tarayıcının kendi adres çubuğunda görünür.
   - **"Sistem konsolu" ve içindeki her şey:** mimari diyagramı, adım adım akış, Network / Veritabanı / Sıralama / Index / Loglar / Kod sekmeleri, "sorgu modeli" düğmesi ve "sıfırla" butonu.
   - **Senaryolar:** `#senaryo=…` adresleriyle açılan senaryolar.
3. Her sayfanın kendi adresi vardır ve adreste çalışma alanı bulunur (`lab.html`'deki `/w/dalga/arama` gibi). Sayfa yenilenince ya da adres yeni sekmede açılınca aynı çalışma alanı ve aynı sayfa gelir. Tarayıcının geri / ileri butonları çalışır.
4. Kimlik doğrulama yoktur. Başlıkta kullanıcı adı ya da rol gösterilmez.
5. Arayüz metinlerinin hepsi Türkçedir. Ürünün adı "yanıt.", arama sayfasının başlığı "Bilgi bankası"dır. Ekranda "RAG" ya da "lab" kelimesi geçmez.
6. Backend'den gelen hatalar `errorCode`'a göre Türkçe ve anlaşılır bir mesajla gösterilir. Backend'in ham hata metni, stack trace ya da HTTP gövdesi gösterilmez. Backend'e hiç ulaşılamazsa da ayrı bir Türkçe mesaj çıkar.
7. Parça içerikleri, kaynak adları ve arama metinleri her zaman düz metin olarak gösterilir. İçlerindeki HTML ya da script çalışmaz.

### Çalışma alanı
8. Başlıkta çalışma alanı seçici vardır. Listede her alanın adı, kaynak sayısı ve parça sayısı görünür. Varsayılan alan Dalga Fiber'dir (id 42).
9. Alan değiştirilince bütün sayfalar yeni alanın verisini gösterir. Önceki alanın arama sonucu ekranda kalmaz.
10. Adreste olmayan bir çalışma alanı varsa "çalışma alanı bulunamadı" ekranı ve varsayılan alana dönüş bağlantısı gösterilir. Olmayan bir kaynak için de aynısı geçerlidir.

### Arama
11. Arama sayfası `lab.html`'deki gibidir. Üstte "Bilgi bankası" başlığı ve açıklaması bulunur; açıklamada seçili çalışma alanının adı geçer. Altında arama kutusu ve "Ara" butonu yer alır. Arama Enter tuşuyla da yapılır.
12. Arama metni boşsa ya da sadece boşluksa arama yapılmaz ve buton pasif olur. Metin en fazla 500 karakter girilebilir.
13. Arama sürerken buton pasif olur. Sonuç alanında aranan metin ve "aranıyor…" yazar, altında iskelet satırlar görünür. Aynı anda ikinci bir arama gönderilmez.
14. "Sık aranan" bölümünde `lab.html`'deki 3 sorgu buton olarak gösterilir:
    - "Şifremi unuttum, online işlemlere giremiyorum"
    - "TS-999 hata kodu"
    - "Taşınıyorum, internetimi yeni eve nasıl aldırırım?"

    Tıklanınca o sorguyla arama yapılır.
15. Arama kutusunun altında bir bilgi satırı bulunur: arama yöntemi, en fazla sonuç sayısı ve embedding modeli.
16. **Gelişmiş ayarlar** katlanır bir paneldir ve varsayılan olarak kapalıdır. Şu ayarları içerir:
    - **Arama yöntemi:** Kelime / Anlam / Hybrid. Varsayılan Hybrid.
    - **Filtre stratejisi:** post-filter / iterative scan. Varsayılan iterative scan.
    - **`ef_search`:** 10 ile 1000 arası. Varsayılan 40.
    - **Sonuç sayısı:** 1 ile 50 arası. Varsayılan 10.
    - **Sorgu planını getir:** açık / kapalı. Varsayılan kapalı.

    Kelime yönteminde filtre stratejisi, `ef_search` ve sorgu planı pasiftir. Geçersiz bir değer girilirse arama yapılmaz ve alanın altında Türkçe bir uyarı çıkar. "Varsayılana dön" butonu bütün ayarları sıfırlar. Ayarlar tarayıcıda hatırlanır; sayfa yenilense de kalır.
17. Henüz arama yapılmadıysa "Henüz arama yapılmadı" boş durumu gösterilir.
18. **Sonuç listesi** `lab.html`'deki gibidir. Her sonuçta şunlar bulunur:
    - sıra numarası ve başlık,
    - hangi yolla bulunduğunu gösteren "anlam" ve / veya "kelime" etiketi,
    - içerikten kısa bir bölüm,
    - kaynak adı, parça numarası ve parça id'si.

    Kelime kolunun bulduğu sonuçlarda sorguyla eşleşen kelimeler, backend'in döndürdüğü konumlara göre vurgulanır. Sonuç başlığında aranan metin, dönen sonuç sayısı ve toplam süre görünür.
19. Sonuç yoksa "“…” için sonuç bulunamadı" boş durumu gösterilir. Kelime yönteminde sonuç çıkmazsa Anlam ya da Hybrid ile denenmesi önerilir.
20. Vektör kolu istenenden az sonuç döndürdüyse ("kısa kaldı"), sonuç başlığında bir uyarı gösterilir ve iterative scan'in denenmesi önerilir.
21. **Teknik detay** açılır bir alandır ve varsayılan olarak kapalıdır.
    - **Arama düzeyinde:** yöntem, filtre stratejisi, `ef_search`, kelime kolunun ve vektör kolunun bulduğu satır sayısı, embedding / SQL / toplam süre (ms).
    - **Her sonuç için:** kelime kolundaki sırası ve puanı, vektör kolundaki sırası ve cosine mesafesi, Hybrid'de RRF puanı. Değeri olmayan alan "—" olarak gösterilir.
    - **Sorgu planı:** "Sorgu planını getir" açıksa vektör kolunun sorgu planı sabit genişlikli yazıyla gösterilir.
22. Bir sonuca tıklanınca o sonucun kaynağının detay sayfası açılır. Tıklanan parça listenin en üstünde vurgulu görünür ve "Arama sonucundan açılan parça vurgulandı: #id" bilgisi çıkar.
23. Siteden yapılan aramalar, arama kayıtlarına "Temsilci paneli" kanalıyla yazılır.
24. Anlam ya da Hybrid aramada model uyuşmazlığı hatası gelirse bunun nedeni açıkça yazılır ve Kelime yöntemiyle aranması önerilir. Ollama'ya ulaşılamıyorsa ya da model indirilmemişse ayrı bir mesaj gösterilir.

### Arama kayıtları
25. Sayfanın üstünde "Arama kayıtları" başlığı ve açıklaması bulunur. Altında `lab.html`'deki gibi 3 gösterge yer alır. Göstergeleri seçili çalışma alanı için **backend hesaplar**:
    - **Bugün:** bugün yapılan arama sayısı. Gün, Türkiye saatine (Europe/Istanbul) göre belirlenir.
    - **Sonuçsuz arama:** son 7 günde 0 sonuç dönen aramaların oranı (%).
    - **Az sonuç dönen:** son 7 günde en az 1 ama istenen sayıdan az sonuç dönen aramaların oranı (%). Sonuçsuz aramalar bu orana girmez.

    Son 7 günde hiç arama yoksa oranlar "—" olarak gösterilir.
26. Göstergelerin altında kayıt tablosu bulunur. Sütunlar: Saat, Nereden (kanal), Arama, Yöntem, Sonuç, İlk sonuç.
    - Sonuç sütunu: 0 ise kırmızı "Sonuçsuz" rozeti, istenen sayıdan azsa sarı rozet içinde sayı, tamsa yeşil rozet içinde sayı.
    - İlk sonucun başlığı yoksa "—" yazar.
    - Liste yeniden eskiye sıralanır ve sayfalıdır (20'şer).
27. "Arama kayıtları" sekmesinin yanında seçili alanın toplam kayıt sayısı görünür. Siteden yeni bir arama yapılınca bu sayı ve tablo güncellenir.

### Bilgi kaynakları
28. Sayfanın üstünde "Çalışma alanı adı › Bilgi kaynakları" yol gösterici bağlantısı, ardından "Bilgi kaynakları" başlığı ve açıklaması bulunur.
29. Kaynak tablosunun sütunları: Kaynak (ad, altında dosya adı ve sürüm), Tür, Parça, Durum, Son senkron.
    - **Tür** Türkçe gösterilir: Web sitesi, PDF, Metin / Markdown, CSV, Talep geçmişi.
    - **Durum** rozettir: Hazır, İşleniyor, Başarısız.

    Tablonun altında alanın toplam parça sayısı ve embedding modeli yazar. Bir satıra tıklanınca kaynağın detay sayfası açılır.
30. **Yeni kaynak ekle:** diyalogda kaynak adı (zorunlu) ve dosya seçimi bulunur. Kabul edilen dosyalar `.pdf`, `.txt`, `.md`, `.markdown` ve `.csv`'dir.
    - Desteklenmeyen tür, 20 MB'tan büyük dosya ya da boş dosya seçilirse yükleme yapılmaz ve Türkçe bir uyarı çıkar.
    - Backend yine de reddederse onun hatası da Türkçe gösterilir.
31. **Yeni sürüm yükle:** buton sadece dosyadan gelen kaynaklarda (PDF, Metin / Markdown, CSV) bulunur; Web sitesi ve Talep geçmişi kaynaklarında yoktur.
    - Diyalogda kaynağın adı, mevcut sürümü ve parça sayısı, seçilen dosyanın adı ve boyutu ve isteğe bağlı bir sürüm etiketi (ör. `3.2`) bulunur.
    - Diyalogda "Yeni sürüm hazır olana kadar arama mevcut sürümün parçalarını döndürür" bilgisi yazar.
    - Dosya kuralları Kural 30'daki gibidir.
32. **Yükleme sonrası ilerleme:** kaynağın satırında `lab.html`'deki gibi aşama metni ve ilerleme çubuğu, durum sütununda "İşleniyor" rozeti gösterilir. Aşama metinleri: "Sıraya alındı", "Dosya okunuyor", "Parçalara bölünüyor", "Embed ediliyor · X/Y parça", "Yazılıyor".
    - İş durumu yaklaşık 1 saniyede bir sorgulanır. İş bitince (`DONE` ya da `FAILED`) sorgulama durur.
    - `DONE` olunca kaynak listesi yenilenir, satır kısa bir süre vurgulanır ve "<Kaynak adı> <sürüm> hazır · N parça" bildirimi çıkar.
    - `FAILED` olunca hata kodunun Türkçe açıklaması satırda görünür.
33. Kullanıcı başka sayfaya gidip dönerse ya da sayfayı yenilerse süren işin ilerlemesi görünmeye devam eder.
34. Bir kaynağın işi sürerken o kaynağın "Yeni sürüm yükle" butonu pasiftir. Yine de "iş sürüyor" hatası gelirse Türkçe mesajla gösterilir.

### Kaynak detayı
35. Sayfanın üstünde "Bilgi kaynakları › Kaynak adı" yol gösterici bağlantısı, kaynağın adı ve kaynak dosyadan geliyorsa "Yeni sürüm yükle" butonu bulunur.
36. Bilgi kutularında şunlar yazar: tür, sürüm, dosya adı, parça sayısı, embedding modeli, son senkron.
37. Parça tablosunun sütunları: sıra no, başlık ve içerik önizlemesi (altında parça id'si), token sayısı. Liste sayfalıdır: önce 20 parça gelir, "Daha fazla göster" ile 20'şer eklenir.
38. Sayfaya bir arama sonucundan gelindiyse Kural 22 uygulanır.

### Backend ve sözleşme
39. Arama kayıtları göstergeleri (Kural 25) için backend'e çalışma alanı bazında yeni bir endpoint eklenir. Bu endpoint `docs/api/openapi.yaml`'da tanımlanır.
40. Sitenin ihtiyaç duyduğu ama sözleşmede olmayan alanlar planner tarafından sözleşmeye ve backend'e eklenir. Örnekler:
    - arama sonucunda parça numarası (Kural 18),
    - arama kaydında istenen sonuç sayısı (Kural 26),
    - sayfa yenilenince süren işi bulabilmek (Kural 33).

    Var olan endpoint'lerin davranışı değişmez; sadece alan eklenebilir.

### Çalıştırma
41. Frontend de Docker Compose'a eklenir. `docker compose -f deployment/docker-compose.yml up --build -d` sonrası site tarayıcıda bir adresten açılır ve `/api` istekleri backend'e gider. Geliştirmede `npm run dev` ile çalışır ve Vite proxy'si üzerinden lokal backend'e bağlanır.
42. `ollama.md` rehberine siteyi açma (Docker ve geliştirme) ve siteden ilk aramayı yapma adımları eklenir.
43. Klavyeyle bütün akış yapılabilir: sekmeler, arama, ayarlar, diyaloglar, sonuçlar. Diyalog açılınca odak diyaloğa gider, Esc ile kapanır, kapanınca odak açan butona döner. Form alanlarının etiketi vardır.

## Örnek senaryolar
- Kullanıcı sistemi Docker ile açar ve tarayıcıda siteye girer. Dalga Fiber seçili olarak Arama sayfası gelir ve "Henüz arama yapılmadı" yazar. "Arama kayıtları" sekmesindeki sayı 0'dır.
- Kullanıcı "Sık aranan"daki "Şifremi unuttum, online işlemlere giremiyorum" sorgusuna tıklar (Hybrid). "Online İşlemler parolanı sıfırlama" ilk 10'da, "anlam" etiketiyle çıkar. Ayarlarda Kelime yöntemi seçilip aynı arama yapılınca "sonuç bulunamadı" mesajı ve Anlam / Hybrid önerisi çıkar.
- "TS-999 hata kodu" aranır. TS-999 parçası hem "kelime" hem "anlam" etiketiyle en üstte çıkar ve "TS-999" kelimesi vurgulanır. Teknik detay açılınca kelime sırası, mesafe ve RRF puanı görünür.
- "Taşınıyorum, internetimi yeni eve nasıl aldırırım?" post-filter ve `ef_search` 40 ile aranır. 10'dan az sonuç döner ve "kısa kaldı" uyarısı çıkar. Iterative scan ile tekrar aranınca 10 sonuç döner. "Sorgu planını getir" açılınca teknik detayda HNSW index'inin kullanıldığı plan görünür.
- Kullanıcı bir sonuca tıklar. Kaynağın detay sayfası açılır ve o parça en üstte vurgulu görünür. Tarayıcının geri butonuyla arama sonuçlarına döner.
- Arama kayıtları sayfasında yapılan 4 arama yeniden eskiye, "Temsilci paneli" kanalıyla görünür. Sonuçsuz arama kırmızı, kısa kalan arama sarı rozetlidir. "Bugün" göstergesi 4, sonuçsuz oranı %25'tir.
- Kullanıcı modem kılavuzuna "Yeni sürüm yükle" ile `3.2` etiketli bir PDF yükler. Satırda "Dosya okunuyor" → "Parçalara bölünüyor" → "Embed ediliyor · 96/…" → "Yazılıyor" ilerlemesi görünür. Bu sırada sayfa yenilenir ve ilerleme kaldığı yerden görünmeye devam eder. Bitince kaynak `3.2` sürümünde, yeni parça sayısıyla Hazır olur ve "hazır" bildirimi çıkar.
- Kullanıcı "Yeni kaynak ekle" ile bir `.md` dosyası yükler. Yeni kaynak listede İşleniyor olarak çıkar, sonra Hazır olur. Kaynakta geçen bir kelime arandığında kaynağın parçası sonuçlarda görünür.
- `.docx` ya da 25 MB'lık bir PDF seçilir. Yükleme yapılmaz ve Türkçe bir uyarı çıkar.
- Ollama kapatılır. Hybrid arama "embedding servisine ulaşılamıyor" mesajı verir, Kelime araması çalışır. Bu durumda yüklenen dosyanın işi Başarısız olur ve nedeni satırda Türkçe yazar.
- Kullanıcı çalışma alanını Kıyı Net yapar. Adres değişir, kaynak listesinde Kıyı Net'in kaynakları görünür. "Şifremi unuttum" araması sadece Kıyı Net parçalarını döndürür.
- Olmayan bir çalışma alanının adresi açılır. "Çalışma alanı bulunamadı" ekranı çıkar.

## Kabul kriterleri
- Komutlar hatasız geçiyor:
  - `cd frontend && npm run build`
  - `cd frontend && npm run lint`
  - frontend testleri
  - `cd backend && ./mvnw verify`
- Frontend test altyapısı kuruluyor:
  - component testleri: Vitest + React Testing Library, API taklidi MSW ile,
  - uçtan uca testler: Playwright, gerçek sisteme karşı.

  En azından şunlar uçtan uca test ediliyor: Hybrid arama ve sonuçtan kaynağa gitme, yeni sürüm yükleme ve ilerleme, çalışma alanı değiştirme.
- `docker compose -f deployment/docker-compose.yml up --build -d` sonrası `postgres`, `backend` ve `frontend` servislerinin hepsi `healthy`. Site tarayıcıda açılıyor.
- Örnek senaryoların hepsi çalışan sistemde (gerçek Ollama + PostgreSQL) tarayıcıdan deneniyor; sonuçları qa raporunda yazıyor.
- Yeni ve değişen endpoint'ler `docs/api/openapi.yaml`'da tanımlı. Backend ve frontend sözleşmeyle birebir uyumlu.
- Sözleşmedeki bütün `errorCode`'ların sitede bir Türkçe karşılığı var.
- Site `lab.html`'deki "yanıt." ürün ekranının yapısını ve görsel dilini taşıyor ve pencerenin tamamını kullanıyor. Sahte tarayıcı çerçevesi ve Sistem konsolu yok.

## Kapsam dışı
- WebSocket / STOMP ile canlı güncelleme. İlerleme polling ile izlenir. Başka bir sekmede başlatılan yükleme, sayfa yenilenince görünür.
- LLM ile cevap üretme. Bu proje sadece arama yapar.
- `lab.html`'deki Sistem konsolu, sahte tarayıcı çerçevesi ve `#senaryo=` senaryoları.
- Konu anlatım sayfaları (`embedding-benzerlik-arama.html`, `pgvector.html`).
- Kimlik doğrulama, kullanıcı ve rol.
- Kaynak silme, çalışma alanı oluşturma ve silme, model değiştirme ve yeniden embed etme.
- Web sitesi tarama ve talep geçmişi aktarımı.
- Mobil yerleşim. Hedef masaüstü tarayıcıdır (en az 1024 px genişlik).

## Etkilenen katmanlar
- [x] Backend
- [x] Frontend
- [x] API sözleşmesi
- [ ] Veritabanı (migration, pgvector) — gerekirse planner belirler (ör. arama kaydına sonuç sayısı kolonu)
- [ ] WebSocket
- [x] Docker
