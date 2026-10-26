# TASK-006: Demo veriyi kaldır, kendi belgeni yükle ve sor

## Ne isteniyor
Projede hazır gelen hayali şirket verisi (Poyraz Telekom, Dalga Fiber ve diğer çalışma alanları) ile sabit yazılmış "Sık aranan" sorular kaldırılır. Projeyi klonlayan kişi boş bir sistemle karşılaşır: site açılınca belge yükleme sayfası gelir. Kişi kendi belgesini (ör. şirketinin süreç dokümanı) yükler, içe alma bitince belgeye soru sorar. "Sık aranan" alanı, o çalışma alanında yapılmış gerçek aramalarla dolar.

Denemek için projeye örnek bir belge eklenmiştir: `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` (hayali bir lojistik şirketinin 22 bölümlük süreç el kitabı: izin, masraf, satın alma, sevkiyat, iade, BT destek, KVKK, İSG vb.).

## Kurallar

### Demo verinin kaldırılması
1. Başlangıç verisi (seed) kaldırılır. Temiz kurulumda veritabanında hiçbir kaynak, parça, içe alma işi ve arama kaydı yoktur.
2. Uygulama kodunda, API sözleşmesindeki örneklerde, testlerde ve rehberlerde (README, `ollama.md`) Poyraz Telekom, Dalga Fiber ve diğer hayali çalışma alanlarına ait veri ya da metin kalmaz. Örnek gerekiyorsa `ornek-dokuman/` altındaki belge kullanılır.
3. Kökteki `*.html` sayfaları, `embedding-pgvector.md` ve önceki task'ların `docs/tasks/TASK-00X/` dosyaları değişmez (geçmiş kayıt ve referanstır).
4. Daha önce kurulmuş bir sistemde eski demo veri varsa nasıl temizleneceği (veriyi sıfırlama adımı) rehberde yazılır.

### Çalışma alanı
5. Temiz kurulumda tek bir boş çalışma alanı vardır ve site bu çalışma alanıyla açılır. Adı "Belgelerim"dir.
6. Tek çalışma alanı varken üst kısımdaki çalışma alanı seçicisi gösterilmez.
7. Yeni çalışma alanı oluşturma bu task'ın kapsamında değildir.

### Yükleme sayfası
8. Çalışma alanında hiç kaynak yokken site açılınca (`/` ve çalışma alanının ana adresi) belge yükleme sayfası gelir. En az bir kaynak varsa site arama sayfasıyla açılır.
9. Yükleme sayfasına her zaman menüden ulaşılır ("Belge yükle").
10. Yükleme sayfasında dosya seçilir, kaynak adı girilir (boş bırakılırsa dosya adı uzantısız kullanılır) ve yükleme başlatılır. Kabul edilen dosya türleri ve boyut sınırı bugünküyle aynıdır (`.pdf`, `.txt`, `.md`, `.markdown`, `.csv`, en fazla 20 MB).
11. Yükleme başlayınca içe alma ilerlemesi aynı sayfada görünür (bugünkü ilerleme gösterimi). Sayfa yenilense de ilerleme kaldığı yerden görünür.
12. İçe alma bitince kullanıcıya "Belge hazır" bilgisi ve "Soru sor" bağlantısı gösterilir; bağlantı arama sayfasını açar. İçe alma başarısız olursa bugünkü Türkçe hata mesajı gösterilir ve kullanıcı yeniden deneyebilir.
13. Hata mesajları bugünkü `errorCode` eşlemesiyle Türkçe gösterilir (desteklenmeyen dosya, boş dosya, 20 MB sınırı, ad kullanımda, embedding servisine ulaşılamıyor vb.).

### Arama sayfası ve "Sık aranan"
14. Çalışma alanında hiç kaynak yokken arama sayfası boş arama formu yerine "Henüz belge yüklenmedi" bilgisini ve yükleme sayfasına bağlantıyı gösterir.
15. "Sık aranan" alanı sabit sorular içermez. Bu çalışma alanında yapılmış aramalardan en çok tekrarlanan en fazla 5 arama metni gösterilir.
16. Sayılırken arama metninin başındaki/sonundaki boşluklar ve Türkçe büyük/küçük harf farkı yok sayılır ("İzin nasıl alınır" ile "izin nasıl alınır " aynı aramadır). Gösterilen metin, o aramanın en son yazılmış halidir.
17. Sadece en az bir sonuç döndüren aramalar sayılır. Eşit sayıda tekrarlananlar arasında en son yapılan önce gelir.
18. Hiç arama yapılmamışsa "Sık aranan" alanı gösterilmez; arama sayfası boş durumunda "Belgene bir soru sorarak başla" gibi bir yönlendirme görünür.
19. Bir arama yapılıp sonuçlar geldikten sonra "Sık aranan" alanı sayfa yenilenmeden güncellenir.
20. "Sık aranan"daki bir öğeye tıklanınca o metin arama kutusuna yazılır ve arama yapılır (bugünkü davranış).

### Örnek belge
21. `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` projede kalır, uygulama tarafından otomatik yüklenmez. Rehberde "denemek için bu belgeyi yükle" adımı ve birkaç örnek soru yazılır.
22. Örnek belge yükleme sayfasından hatasız içe alınır ve parçalara ayrılır.

## Örnek senaryolar
- Yeni bir geliştirici projeyi klonlar, tam sistemi başlatır ve siteyi açar. Yükleme sayfası gelir; çalışma alanı seçicisi yoktur, kaynak listesi boştur. Hiçbir yerde Poyraz Telekom ya da "Şifremi unuttum, online işlemlere giremiyorum" gibi hazır bir soru görünmez.
- Geliştirici `Atlaskap-Lojistik-Surec-El-Kitabi.pdf` dosyasını seçer, adı boş bırakır ve yükler. Kaynak "Atlaskap-Lojistik-Surec-El-Kitabi" adıyla oluşur, ilerleme çubuğu dolar, "Belge hazır" ve "Soru sor" görünür.
- "Soru sor"a tıklar. "Sık aranan" alanı yoktur. "Yıllık izin kaç gün?" diye arar; izin bölümündeki parça ilk sonuçlar arasında gelir. "Sık aranan" alanında "Yıllık izin kaç gün?" belirir.
- "parolamı unuttum hesabım kilitlendi" diye iki kez, "Masraf beyanı kaç gün içinde yapılır?" diye bir kez arar. "Sık aranan"da önce parola sorusu, sonra masraf sorusu görünür.
- "kuantum fiziği" diye arar ve sonuç gelmez (ya da eşik altı kalır). Bu arama "Sık aranan"a eklenmez.
- Kaynak varken siteyi tekrar açar; doğrudan arama sayfası gelir. Menüden "Belge yükle"ye gidip ikinci bir belge yükleyebilir.
- Eski kurulumu olan geliştirici rehberdeki veriyi sıfırlama adımını uygular; sistem boş çalışma alanıyla açılır.

## Kabul kriterleri
- Komutlar hatasız geçiyor:
  - `cd backend && ./mvnw verify`
  - `cd frontend && npm run build`
  - `cd frontend && npm run lint`
  - frontend testleri ve uçtan uca testler
- Temiz kurulumda (eski Docker volume'ları silinmiş) tam sistem başlıyor, site yükleme sayfasıyla açılıyor, veritabanında kaynak/parça/arama kaydı yok. Bu deneme qa raporunda yazıyor.
- Örnek PDF yükleme sayfasından yükleniyor, içe alma tamamlanıyor, en az 3 örnek soru (izin, masraf, parola/BT) ilgili bölümün parçasını ilk 3 sonuç içinde getiriyor. Sonuçlar qa raporunda yazıyor.
- "Sık aranan" kuralları (en fazla 5, sıralama, büyük/küçük harf ve boşluk birleştirme, sonuçsuz aramaların hariç tutulması, arama sonrası güncellenme) otomatik testlerle doğrulanıyor.
- `backend/`, `frontend/`, `deployment/`, `docs/api/`, `README.md` ve `ollama.md` içinde "Poyraz" ve "Dalga" geçmiyor (`grep` sonucu qa raporunda).
- Rehberde örnek belgeyle deneme adımları ve eski veriyi sıfırlama adımı var.

## Kapsam dışı
- Çalışma alanı oluşturma, silme, yeniden adlandırma.
- Kimlik doğrulama, kullanıcıya özel "Sık aranan".
- Yeni dosya türü desteği, OCR (görüntü PDF'leri), 20 MB sınırının değişmesi.
- Soruya yapay zekâyla cümle halinde yanıt üretme (RAG/LLM). Arama bugünkü gibi ilgili parçaları listeler.
- Kökteki `*.html` sayfalarının ve önceki task dosyalarının değiştirilmesi.
- WebSocket; ilerleme bugünkü gibi polling ile izlenir.

## Etkilenen katmanlar
- [x] Backend (seed kaldırma, varsayılan çalışma alanı, sık aranan sorgusu)
- [x] Frontend (yükleme sayfası, açılış yönlendirmesi, sık aranan, çalışma alanı seçicisi)
- [x] API sözleşmesi (sık aranan için endpoint, Poyraz/Dalga örneklerinin değişmesi)
- [x] Veritabanı (migration: varsayılan çalışma alanı, gerekirse eski demo verinin temizlenmesi — planner belirler)
- [ ] WebSocket
- [x] Docker (seed ayarının compose'tan kalkması, rehber)
