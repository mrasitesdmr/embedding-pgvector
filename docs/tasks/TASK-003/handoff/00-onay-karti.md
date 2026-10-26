TASK: TASK-003
# Onay kartı: Arayüz: arama, arama kayıtları ve bilgi kaynakları ekranları

> **Bu kartın ikinci hali.** Senin kararınla task ikiye bölündü. Dosya yükleme ve yükleme ilerlemesi sonraki task'a kaldı. Bu task'ta kaynaklar sadece görüntülenir. Yeni kütüphanelerin hepsi eklenir.

## Ne yapılacak
- **Site:** Lab'daki "yanıt." ürün ekranı, tarayıcı penceresinin tamamını kullanan gerçek bir web sitesi olur ve TASK-002'deki backend'e bağlanır.
  - En üstte "yanıt." logosu ve çalışma alanı seçici bulunur.
  - Altında üç sekme vardır: Arama, Arama kayıtları, Bilgi kaynakları.
  - Lab'daki sahte tarayıcı çerçevesi, Sistem konsolu ve senaryolar alınmaz.
- **Arama:**
  - Soru yazıp Enter'a basınca ya da "Sık aranan" sorgulardan birine tıklayınca sonuçlar lab'daki gibi listelenir.
  - Her sonuçta "anlam" ve/veya "kelime" etiketi, kaynak adı ve parça numarası bulunur. Kelime aramasının bulduğu kelimeler işaretlenir.
  - Katlanır "Gelişmiş ayarlar" panelinde yöntem, filtre stratejisi, `ef_search`, sonuç sayısı ve sorgu planı seçilir. Bu ayarlar tarayıcıda hatırlanır.
  - "Teknik detay" alanında sıralar, puanlar, mesafeler, süreler ve sorgu planı görünür.
  - Bir sonuca tıklayınca o parça, kaynağın sayfasında en üstte vurgulu açılır.
- **Arama kayıtları:**
  - Üstte üç gösterge vardır: bugün yapılan arama sayısı, son 7 günde sonuçsuz aramaların oranı, az sonuç dönen aramaların oranı. Göstergeleri backend hesaplar.
  - Altında renkli rozetli kayıt tablosu 20'şer satırlık sayfalarla gösterilir.
  - Sekmenin yanındaki sayı, siteden arama yapınca artar.
- **Bilgi kaynakları (sadece görüntüleme):** Kaynak tablosunda tür, parça sayısı, durum rozeti (Hazır / İşleniyor / Başarısız) ve son senkron görünür. Bir satıra tıklayınca kaynağın sayfası açılır.
- **Kaynak sayfası (sadece görüntüleme):** Kaynağın bilgileri ve parçaları görünür. Parçalar önce 20 tane gelir, "Daha fazla göster" ile 20'şer eklenir.
- **Çalıştırma:** Site Docker ile `http://localhost:3000` adresinden açılır, geliştirmede `npm run dev` ile çalışır. `ollama.md`'ye siteyi açma ve ilk arama adımları eklenir.

## Bittiğini nasıl anlayacaksın
1. **Açılış:** `docker compose -f deployment/docker-compose.yml up --build -d` sonrası üç servis (veritabanı, backend, site) "healthy" olur. `http://localhost:3000` açılınca Dalga Fiber seçili Arama sayfası gelir ve "Henüz arama yapılmadı" yazar.
2. **Parola sorusu:** "Şifremi unuttum, online işlemlere giremiyorum" sorgusuna tıklarsın.
   - "Online İşlemler parolanı sıfırlama" ilk 10'da "anlam" etiketiyle çıkar.
   - Ayarlardan Kelime seçip aynı aramayı yapınca "sonuç bulunamadı" mesajı ve Anlam / Hybrid önerisi çıkar.
3. **TS-999:** "TS-999 hata kodu" aranır.
   - TS-999 parçası "kelime" ve "anlam" etiketleriyle en üstte çıkar; "TS-999" kelimesi işaretlidir.
   - Teknik detayda sıra, mesafe ve RRF puanı görünür.
4. **Taşınma sorusu:**
   - Post-filter ve `ef_search` 40 ile aranınca "kısa kaldı" uyarısı çıkar.
   - Iterative scan ile 10 sonuç döner.
   - "Sorgu planını getir" açıkken teknik detayda index'in kullanıldığı plan görünür.
5. **Sonuçtan kaynağa:** Bir sonuca tıklarsın; kaynağın sayfası açılır ve parça en üstte vurgulu görünür. Sayfayı yenilersen de aynı parça vurgulu kalır. Tarayıcının geri butonu seni aynı sonuçlara döndürür.
6. **Arama kayıtları:**
   - Yaptığın aramalar yeniden eskiye, "Temsilci paneli" kanalıyla görünür.
   - Sonuçsuz arama kırmızı, az sonuç dönen sarı rozetlidir.
   - Temiz bir sistemde 4 aramadan 1'i sonuçsuzsa "Bugün" göstergesi 4, sonuçsuz oranı %25 olur.
7. **Ollama kapalıyken:** Hybrid arama "embedding servisine ulaşılamıyor" der, Kelime araması çalışır.
8. **Alan değiştirme:** Çalışma alanını Kıyı Net yaparsın. Adres değişir; kaynaklar ve arama sonuçları sadece Kıyı Net'e aittir.
9. **Olmayan alan:** `/w/999/arama` adresi "Çalışma alanı bulunamadı" ekranını ve varsayılan alana dönüş bağlantısını gösterir.
10. **Kaynak ekranları:** Bilgi kaynakları tablosunda türler Türkçe ("Web sitesi", "Talep geçmişi" …) ve durum rozetli görünür. Tablonun altında toplam parça sayısı ve embedding modeli yazar. Bu task'ta yükleme butonları yoktur.
11. **Klavye:** Sekmeler, arama, ayarlar, teknik detay ve sonuçlar sadece klavyeyle kullanılabilir. Her form alanının etiketi vardır.
12. **Testler:** Site testleri (ekran parçaları ve gerçek sisteme karşı uçtan uca), backend testleri, derleme ve kod kontrolü hatasız geçer. Uçtan uca testler en az iki akışı dener: Hybrid arama ve sonuçtan kaynağa gitme; çalışma alanı değiştirme.

## Yapılmayacaklar
- **Sonraki task'a kalanlar (senin bölme kararın):**
  - "Yeni kaynak ekle" ve "Yeni sürüm yükle" butonları ve diyalogları; dosya türü ve boyutu uyarıları.
  - Yükleme ilerlemesi: aşama metni, ilerleme çubuğu, saniyede bir sorgulama, "hazır" bildirimi, sayfa yenilenince süren işin görünmeye devam etmesi.
  - Başarısız bir yüklemenin nedeninin satırda yazması ("Son yükleme başarısız: …").
  - Diyaloglarda klavye davranışı (odak, Esc, odağın geri dönmesi).
  - Örnek senaryolardan modem kılavuzu 3.2 yüklemesi, `.md` ile yeni kaynak, `.docx` / 25 MB uyarısı ve Ollama kapalıyken yükleme.
  - "Yeni sürüm yükleme ve ilerleme" uçtan uca testi.
  - Süren işi bulmak için backend'e eklenecek bilgi (kaynağın son yükleme işi). Kullanan bir ekran olmadığı için bu task'ta eklenmiyor.
- Canlı güncelleme (WebSocket).
- Yapay zekâ ile cevap üretme; site sadece arama yapar.
- Lab'daki Sistem konsolu, sahte tarayıcı çerçevesi ve `#senaryo=` senaryoları.
- Konu anlatım sayfaları (embedding ve pgvector anlatımları).
- Giriş, kullanıcı ve rol.
- Kaynak silme, çalışma alanı oluşturma ve silme, model değiştirme ve yeniden embed etme.
- Web sitesi tarama ve talep geçmişi aktarımı.
- Mobil yerleşim; hedef en az 1024 px genişliğinde masaüstü tarayıcıdır.
- Kodda kasıtlı hata üreterek testleri sınayan ek araç (mutation testi).

## Benim verdiğim kararlar
- **Adresler:** Lab'daki gibi, çalışma alanı numarasıyla: `/w/42/arama`, `/w/42/arama-kayitlari`, `/w/42/kaynaklar`, `/w/42/kaynaklar/302?parca=1380`. Ana adres `/w/42/arama`'ya gider.
- **Son arama sonucu:** Site içinde gezinirken ve geri butonuyla dönünce ekranda kalır. Sayfa yenilenince ya da çalışma alanı değişince silinir. Yenileme aramayı tekrar çalıştırmaz, çünkü her tekrar yeni bir arama kaydı yazar ve "Bugün" göstergesini şişirir.
- **"Son 7 gün":** Bugün ve önceki 6 takvim günüdür, Türkiye saatiyle gece 00:00'dan başlar. Oranlar ondalıklı gösterilir (ör. %6,8).
- **Eski arama kayıtları:** Bu task'tan önce yapılan aramalarda "istenen sonuç sayısı" kaydedilmemişti.
  - Bu kayıtlar "az sonuç dönen" oranına girmez.
  - Tabloda sonuç sayısı 0 değilse yeşil, 0 ise "Sonuçsuz" rozetiyle görünür.
- **Arama ayarları:**
  - Tarayıcıda saklanır ve bütün çalışma alanlarında aynıdır.
  - Geçersiz bir değer saklanmaz; sayfa yenilenince son geçerli değer gelir.
  - "Gelişmiş ayarlar" paneli her açılışta kapalı başlar.
- **Çalışma alanı seçici:** Tarayıcının standart açılır listesidir ("Dalga Fiber · 4 kaynak · 42 parça"). Kaynak sayfasındayken alan değiştirirsen, o kaynak yeni alana ait olmadığı için yeni alanın Bilgi kaynakları sayfasına gidersin.
- **Silinmiş parça:** Kaynağın yeni bir sürümü yüklendiyse eski parçaları silinmiştir. Eski bir adresten bu parçalardan birini açarsan "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir." yazar, kaynağın sayfası yine açılır.
- **Durum rozeti:** Kaynağın durumunu olduğu gibi gösterir: Hazır, İşleniyor, Başarısız. Bir kaynak başka yoldan (ör. Swagger ile) yükleniyorsa satırda "İşleniyor" görünür. Bu task'ta ilerleme çubuğu yoktur ve liste kendiliğinden yenilenmez; sayfayı yenileyince son durum görünür.
- **"Kısa kaldı" uyarısı:** Post-filter kullanılmışsa iterative scan önerilir. Iterative scan ile de az sonuç dönerse "bu çalışma alanında yeterli parça olmayabilir" yazar.
- **Gösterilen embedding modeli:** Çalışma alanının parçalarını gerçekten oluşturan modeldir (ör. `bge-m3@1024`).
- **Başlıksız parça:** Başlığı olmayan parçalarda başlık yerine "Başlıksız parça" yazar.
- **Backend'e eklenenler:** Var olan özelliklerin davranışı değişmez. Ekranın ihtiyacı için şunlar eklenir:
  - göstergeleri hesaplayan yeni bir adres;
  - vurgulu parçanın sayfa yenilense de bulunması için tek parçayı getiren yeni bir adres ve "parça bulunamadı" hata kodu;
  - arama sonuçlarına parça numarası;
  - arama kayıtlarına istenen sonuç sayısı (veritabanına bir kolon eklenir);
  - çalışma alanına embedding modelinin adı.
- **Hata mesajları:** Backend'in bütün hata kodlarının Türkçe karşılığı bu task'ta yazılır; yüklemeye ait olanlar da. Yükleme işinin kendi hata nedenleri (ör. "dosyada metin yok") sonraki task'ta eklenir.
- **Docker'da site:** Site kendi container'ında çalışır ve `http://localhost:3000` adresinden açılır; backend'e giden istekleri bu container iletir. Backend, rehberdeki komutlar için 8080'den de erişilebilir kalır.
- **Uçtan uca testler:** Veriyi değiştirmez, sadece arama yapar (arama kayıtlarına satır ekler). Bu yüzden "Bugün 4, %25" kontrolü temiz bir veritabanında, uçtan uca testlerden önce yapılır.

## Verilen kararlar (senin cevapların)
1. **Task bölünsün mü?** B: bölünsün. Dosya yükleme ve ilerleme (TASK Kural 30-34) sonraki task'a kaldı. Kaynak listesi ve kaynak sayfası bu task'ta sadece görüntüleme için yapılıyor.
2. **Yeni kütüphaneler eklensin mi?** A: hepsi eklensin, yazı tipleri sitenin kendi sunucusundan gelsin.
   - **Site için:**
     - React Router: sayfa adresleri, geri/ileri.
     - TanStack Query: backend'den veri çekme ve önbellek.
     - react-error-boundary: beklenmeyen ekran hatasında boş sayfa kalmaması.
     - Fontsource yazı tipi paketleri: Source Sans 3 ve JetBrains Mono.
   - **Sadece test ve geliştirme için:**
     - Vitest, jsdom, Vitest coverage.
     - Testing Library.
     - MSW.
     - vitest-axe.
     - Playwright.
     - oxlint.

## Çalışacak roller
planner, backend-coder, frontend-coder, tester, devops, qa.
Atlanan yok.

## Kararını bekleyen sorular
Yok.

## Sonraki task için öneri
TASK.md'yi sen yazarsın; önerdiğim başlık ve kapsam:

**Başlık:** "Arayüz: dosya yükleme ve içe alma ilerlemesi".

**Kapsam:**
- TASK-003'teki Kural 30-34, Kural 35'teki "Yeni sürüm yükle" butonu ve Kural 43'ün diyalog kısmı.
- Örnek senaryolar: modem kılavuzu 3.2, `.md` ile yeni kaynak, `.docx` / 25 MB ve Ollama kapalıyken yükleme.
- "Yeni sürüm yükleme ve ilerleme" uçtan uca testi.
- Backend'de kaynağın son yükleme işinin bilgisi (sayfa yenilenince süren işi bulmak ve başarısızlık nedenini göstermek için).

## Riskler
- **Sürüm uyumu:** Kütüphanelerin en yeni sürümleri birbiriyle uyumsuz çıkarsa en yakın uyumlu sürüm seçilir ve raporda yazılır. Yeni kütüphane eklenmez.
- **Uçtan uca test süresi:** Testler gerçek Ollama ile çalışır. Mac meşgulse arama yavaşlayabilir; zaman aşımı olursa raporda yazılır.

ONAY: VERİLDİ
