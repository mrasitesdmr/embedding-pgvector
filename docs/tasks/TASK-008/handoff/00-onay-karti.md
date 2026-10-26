TASK: TASK-008
# Onay kartı: Uçtan uca doğrulama: backend, frontend ve PostgreSQL akışlarının baştan sona testi

## Ne yapılacak
- Sitede yapabildiğin her iş gerçek çalışan sistemde baştan sona denenir: sistemi açma, belge yükleme (PDF dahil), yükleme ilerlemesi ve sayfa yenileme, yeni sürüm yükleme, üç arama yöntemi, arama kayıtları, istatistikler, "Sık aranan" listesi, belge ve parça ekranları.
- Her iş üç yerden kontrol edilir: ekranda ne görünüyor, sistemin arka ucu ne cevap veriyor (API tanımına uyuyor mu), veritabanına ne yazıldı (parça sayısı, her parçanın 1024 sayılık vektörü, model adı, arama dizininin kullanılması).
- Hata durumları da denenir: boş ya da çok uzun arama metni, olmayan belge, desteklenmeyen ya da boş dosya, 10 belge sınırı, 20 MB sınırı, Ollama kapalıyken arama ve yükleme. Her hatada ekranda doğru Türkçe mesaj görünmeli, veritabanında yarım kayıt kalmamalı.
- Aynı anda birkaç yükleme, yükleme sürerken arama, yükleme sırasında backend'in yeniden başlaması, sistemi kapatıp açma ve tamamen sıfırlama denenir. README ve `ollama.md`'deki adımlar yazıldığı gibi tek tek çalıştırılır.
- Bulunan her hata kaydedilir: ne oluyor, nasıl tekrarlanır, hangi bölümde, ne kadar önemli. Hata düzeltilir ve tekrar etmemesi için onu yakalayan bir test eklenir. Yeni özellik eklenmez.

## Bittiğini nasıl anlayacaksın
1. Raporlarda her iş için bir satır vardır: ekranda, cevapta ve veritabanında nasıl denendi, sonuç ne.
2. Raporda bulunan hataların listesi vardır. Her hatanın yanında düzeltildiği ve onu yakalayan testin adı yazar. Düzeltilmeyen varsa nedeni yazar.
3. Bütün testler geçer ve sayıları raporda yazılıdır. Bugünkü sayılar: backend 417, site 405, uçtan uca 8. Bunlara yeni testler eklenir.
4. İş bitince sistem temiz kurulumla açık bırakılır. Kendin deneyebilirsin:
   - http://localhost:3000 adresini aç. "Belge yükle" sayfası gelir; arama sayfasında "Henüz belge yüklenmedi" yazar.
   - Örnek PDF'i yükle. Yüklenirken sayfayı yenile; ilerleme kaldığı yerden devam eder. Sonunda yaklaşık 24 parça görürsün.
   - "Yıllık izin kaç gün?" diye sor. "Yıllık izin ve diğer izinler" bölümü ilk sonuçlardadır. Arama, "Arama kayıtları" sayfasında ve "Sık aranan" listesinde görünür.
   - Dosya seçiminde Word dosyası (`.docx`) ya da boş bir dosya seç. Anlamlı bir Türkçe uyarı görürsün ve yükleme yapılmaz.

## Yapılmayacaklar
- Yeni özellik, yeni ekran ya da yeni adres eklenmez.
- Canlı bağlantı (WebSocket) eklenmez; ilerleme bugünkü gibi saniyede bir sorulur.
- Hız iyileştirmesi ve yük testi yapılmaz. Sadece hata sayılacak kadar ciddi bir yavaşlık görülürse raporlanır.
- Kökteki anlatım sayfalarına dokunulmaz.
- Belge silme özelliği eklenmez. Başarısız bir belge 10 belge sınırında yer tutmaya devam eder (bkz. soru 2).
- PDF parçalarının başlığı bugünkü gibi sayfanın ilk satırı olarak kalır. Örnek belgede bu satır şirket adıdır.

## Benim verdiğim kararlar
- **Hangi hatalar düzeltilir:** Hatalar dört derecede değerlendirilir. "Kritik": veri kaybı, sistem açılmıyor ya da yükleme veya arama çalışmıyor. "Yüksek": yanlış sonuç ya da tanıma uymayan cevap. "Orta": yanlış veya eksik hata mesajı, dokümanda çalışmayan bir adım. "Düşük": sadece doğrudan API kullanınca görülen ya da kozmetik sorun. İlk üç derecenin hepsi düzeltilir. Düşük dereceli hata, düzeltmesi küçükse düzeltilir; değilse gerekçesiyle listelenir. Yeni özellik gerektiren düzeltme yapılmaz; listelenir ve sana ayrı bir iş olarak önerilir.
- **Ollama kapatılmaz:** "Ollama kapalı" denemesi için bilgisayarındaki Ollama'ya dokunulmaz. Backend geçici olarak ulaşılamayan bir adrese yönlendirilir, deneme bitince eski hâline döner.
- **Ollama kapalıyken yükleme mesajı:** Yükleme önce kabul edilir, sonra başarısız olur. Ekranda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" satırı görünür. Aramada ise "Embedding servisine (Ollama) ulaşılamıyor…" uyarısı çıkar. İkisini de görevdeki örnekle aynı sayıyorum.
- **Sistem yeniden başlarken görünen mesaj:** Backend kapalıyken ya da yeniden açılırken ekranda bugün "Beklenmeyen bir hata oluştu" çıkıyor olabilir. Doğrusu "Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et." mesajıdır. Böyle çıkmazsa hata sayılır ve düzeltilir.
- **API tanımında düzelttiğim yanlışlar (davranış değişmedi):**
  - PDF imzası: tanım "dosya `%PDF-` ile başlamalı" diyordu. Sistem bu imzayı dosyanın ilk 1024 baytında arıyor.
  - Örnek parça sayısı: tanımda 48 yazıyordu, örnek belge 24 parça üretiyor.
  - Sayısal olmayan adres: belge numarası yerine `abc` gibi bir değer gelirse cevap "istek okunamadı" (400) olur. Var olmayan bir numara "bulunamadı" (404) döner.
- **Hata sayılmayacak davranışlar:**
  - "Kelime" yönteminde soru cümlesi sonuç vermeyebilir, çünkü sorudaki bütün kelimeler aynı parçada geçmelidir. Varsayılan "Hybrid" yöntem sonuç verir.
  - Uçtan uca testlerden sonra yükleme işlerinin numarası 1'den başlamaz.
- **Uzun test belgesi:** Yenileme ve yeniden başlatma denemeleri için yaklaşık 500 parçalık bir belge testin içinde üretilir; projeye dosya eklenmez.
- **Karşılaştırma kopyası:** Proje bir sürüm kontrol deposu olmadığı için değişiklikler başlamadan önce kodun bir kopyası geçici klasöre alınır. Son kontrol bu kopyaya göre yapılır, kopya iş bitince silinir.

## Çalışacak roller
Sıra:
1. **planner:** Bu kart ve plan.
2. **frontend-coder:** Sadece 1. soruya "evet" dersen. Üç test kütüphanesini kurar; başka bir şeye dokunmaz.
3. **tester (asıl iş):** Bütün testleri çalıştırır, eksik testleri yazar, şüpheli noktaları dener ve hata listesini çıkarır.
4. **backend-coder ve frontend-coder:** Sadece hata bulunursa, kendi bölümlerindeki hataları düzeltir. Sonra tester testlerin geçtiğini yeniden doğrular.
5. **devops:** Sistemi kapatıp açma ve sıfırlama denemelerini yapar. Yükleme sırasında backend'i yeniden başlatır. README ve `ollama.md` adımlarını çalıştırır, yanlış yazılmış bir yer varsa düzeltir.
6. **qa:** Her işi üç yerden son kez kontrol eder ve hata listesini denetler.

Atlanan rol yok.

## Kararını bekleyen sorular
1. Sistemin arka ucundan gelen her cevabın API tanımına birebir uyduğunu uçtan uca testlerde otomatik kontrol etmek için üç küçük test kütüphanesi ekleyelim mi?
   Kütüphaneler: ajv (bir cevabın tanımdaki yapıya uyup uymadığını denetler), ajv-formats (tarih, adres gibi biçimleri tanır), yaml (API tanım dosyasını okur).
   Neden önemli: Görev her işte cevabın tanıma uymasını istiyor; kütüphane olmadan sadece seçilmiş alanlar elle kontrol edilir ve tanımda olmayan fazladan bir alan ya da yanlış tip gözden kaçabilir.
   Seçenekler: A) Ekle. Sadece testlerde kullanılır; siteye ve Docker imajına girmez. B) Ekleme. Testler önemli alanları tek tek kontrol eder; son kontrolde tanım dosyası backend'in ürettiği tanımla elle karşılaştırılır.
   Önerim: A, çünkü kontrol her cevapta ve eksiksiz yapılır, siteye hiçbir yük getirmez.

2. Metin içermeyen bir dosya yüklendiğinde belge listede "başarısız" olarak kalıyor ve 10 belge sınırından bir yer tutuyor; bu davranış doğru kabul edilsin mi?
   Böyle dosyalar: yalnızca boşluktan oluşan bir metin dosyası, yalnızca başlık satırı olan bir CSV ya da yalnızca görsel içeren bir PDF.
   Neden önemli: Görev "boş dosya yüklenince veritabanında yarım kayıt kalmaz" diyor. Bu dosyalar 0 bayt değil ama içlerinde metin yok; testin neyi beklediği buna göre değişir.
   Seçenekler:
   A) Doğru kabul edilsin. Belge "Son yükleme başarısız: dosyada metin yok…" nedeniyle listede kalır. "Yeniden dene" ile aynı belgeye doğru dosya yüklenebilir. Bu durum hata olarak raporlanmaz.
   B) Değişsin. Yalnızca boşluk içeren metin dosyası ve yalnızca başlık satırı olan CSV yükleme anında reddedilir, belge hiç oluşmaz. PDF'ler arka planda okunduğu için onlarda bugünkü davranış sürer. Bu küçük bir kural eklemesidir; API tanımı, backend ve site birlikte değişir.
   Önerim: A, çünkü kullanıcı nedeni görüyor ve aynı yerden düzeltebiliyor. B yeni bir kural ekler (görev "yeni özellik yok" diyor) ve PDF'leri yine çözmez.

3. Denemeler sırasında tam sistemdeki ve local geliştirme ortamındaki veriler silinecek; bunda sakınca var mı?
   Neden silinir: Uçtan uca testler her çalıştığında çalışma alanını boşaltır. Temiz kurulum denemesi de veritabanını tamamen sıfırlar.
   Neden önemli: Silinen veri geri gelmez. Yüklediğin belgelerin asıl dosyası sistemde saklanmıyor.
   Seçenekler: A) Silinebilir. İş sonunda sistem boş, temiz kurulumla açık kalır. B) Önce yedek alınsın. İş sonunda yedek geri yüklenir ve sistem senin verinle açılır.
   Önerim: Kendi yüklediğin ve saklamak istediğin bir belge yoksa A; varsa B.

## Riskler
- **Hazır olması gerekenler:** Denemeler için Docker Desktop ve Ollama (bge-m3 modeli indirilmiş) açık olmalı. Değilse iş durur ve sana haber verilir.
- **Bilgisayarın hızına bağlı testler:** Yenileme ve yeniden başlatma testleri, uzun belgenin yüklenmesi sürerken yapılır. Bilgisayar çok hızlıysa belge büyütülür; raporda yazılır.
- **Durma noktası:** tester düzeltmelerden sonra yeni bir hata daha bulursa ekip durur ve sana sorar. Bunu önlemek için ilk turda bütün işler tek seferde denenir.

## Kullanıcının cevapları
1. A: ajv, ajv-formats ve yaml test paketleri eklenir.
2. A: metinsiz dosyada belgenin "başarısız" kalması doğru kabul edilir, hata sayılmaz.
3. A: tam sistem ve local geliştirme verisi silinebilir; yedek alınmaz.

ONAY: VERİLDİ
