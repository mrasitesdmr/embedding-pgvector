TASK: TASK-006
# Onay kartı: Demo veriyi kaldır, kendi belgeni yükle ve sor

## Ne yapılacak
- **Hazır demo veri kalkıyor:** Hayali şirketlerin (Poyraz Telekom, Dalga Fiber ve diğerleri) verisi ve sabit "Sık aranan" soruları projeden çıkar. Temiz kurulumda tek, boş bir çalışma alanı vardır: "Belgelerim".
- **Eski kurulum otomatik temizlenir:** Daha önce kurulmuş bir sistemde, güncellemeden sonraki ilk açılışta eski verinin tamamı silinir: demo çalışma alanları, senin yüklediğin belgeler, parçalar, yükleme işleri ve arama kayıtları. Sistem boş "Belgelerim" ile açılır. Bu silme bir kez olur ve geri alınamaz; sonra yüklediğin belgeler yerinde kalır. Temiz kurulumda silinecek bir şey yoktur. Kod örneklerinde, testlerde ve rehberlerde bu şirketlerin adı geçmez; örnek gerekirse `ornek-dokuman/` altındaki Atlaskap el kitabı kullanılır.
- **Site yükleme sayfasıyla açılır:** Hiç belge yokken site "Belge yükle" sayfasını açar. Belge varsa arama sayfası açılır. Menüde her zaman "Belge yükle" bağlantısı vardır. Tek çalışma alanı olduğu için üstteki çalışma alanı seçicisi görünmez.
- **Yükleme sayfası:** Dosyayı seçersin, istersen bir ad yazarsın. Ad boş kalırsa dosyanın adı uzantısız kullanılır. Yükleyince ilerleme çubuğu aynı sayfada görünür; sayfayı yenilesen de kaldığı yerden devam eder. Bitince "Belge hazır" yazar ve "Soru sor" bağlantısı arama sayfasını açar. Yükleme başarısız olursa bugünkü Türkçe hata mesajı çıkar, "Yeniden dene" ile aynı belgeyi tekrar yüklersin.
- **Arama sayfası:** Hiç belge yokken arama kutusu yerine "Henüz belge yüklenmedi" bilgisi ve yükleme sayfasına bağlantı görünür. Belge varken henüz arama yapılmadıysa "Belgene bir soru sorarak başla" yazar.
- **Gerçek "Sık aranan":** Bu çalışma alanında en çok tekrarlanan en fazla 5 arama gösterilir. Büyük/küçük harf ve baştaki/sondaki boşluk farkı aynı arama sayılır. Belgeyle ilgisiz aramalar sayılmaz. Bir arama yapınca liste sayfa yenilenmeden güncellenir. Listedeki bir soruya tıklayınca o soru kutuya yazılır ve aranır.
- **Rehber:** `README.md`'ye "Örnek belgeyle dene" (yükleme adımları ve örnek sorular) ve "Önceki sürümden güncelleme" bölümleri eklenir. İkincisi güncellemenin eski veriyi otomatik ve geri alınamaz şekilde sildiğini anlatır. `ollama.md`'deki örnekler örnek belgeyle yeniden yazılır.

## Bittiğini nasıl anlayacaksın
1. **Temiz kurulum:** Eski Docker verisini silip tam sistemi başlatırsın. `http://localhost:3000` "Belge yükle" sayfasıyla açılır. Çalışma alanı seçicisi yoktur. "Bilgi kaynakları" boştur. Arama sayfasında "Henüz belge yüklenmedi" yazar. Hiçbir yerde Poyraz, Dalga ya da "Şifremi unuttum, online işlemlere giremiyorum" gibi hazır bir soru yoktur.
2. **Örnek belge:** `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` dosyasını seçersin, adı boş bırakıp yüklersin. Kaynak "Atlaskap-Lojistik-Surec-El-Kitabi" adıyla oluşur. İlerleme çubuğu dolarken sayfayı yenilersen çubuk yine görünür. Sonunda "Belge hazır" ve "Soru sor" çıkar.
3. **Üç soru:** "Soru sor"a tıklarsın; "Sık aranan" alanı yoktur. "Yıllık izin kaç gün?", "Masraf beyanı kaç gün içinde yapılır?" ve "parolamı unuttum hesabım kilitlendi" diye ararsın. Her birinde ilgili bölümün parçası ilk 3 sonuç içindedir.
4. **"Sık aranan":** İlk aramadan sonra "Yıllık izin kaç gün?" listede belirir. Parola sorusunu bir kez daha (ör. "PAROLAMI unuttum hesabım kilitlendi") ararsın; liste sırası parola, masraf, izin olur ve parola sorusu son yazdığın hâliyle görünür. "kuantum fiziği" diye ararsın; listeye eklenmez.
5. **Kaynak varken:** Siteyi yeniden açarsın, doğrudan arama sayfası gelir. Menüden "Belge yükle"ye gidip ikinci bir belge yükleyebilirsin.
6. **Eski kurulum:** Demo verisi olan eski bir sistem yeni sürümle ilk kez açılınca site boş "Belgelerim" ve yükleme sayfasıyla gelir; seçici görünmez. Bunu geliştirme sırasında iki kez gözleriz (local geliştirme veritabanında ve tam sistemde), önceki ve sonraki kayıt sayıları raporlara yazılır. Ayrıca otomatik bir test hem demo veriyi hem senin yüklemiş olabileceğin belgeleri sildiğini ve silmenin sadece bir kez olduğunu doğrular. Sistemi sonra yeniden başlatınca yüklediğin belgeler yerinde kalır.
7. **Rehber:** `README.md`'deki "Örnek belgeyle dene" adımlarını uygulayan biri aynı sonuçlara ulaşır. "Önceki sürümden güncelleme" bölümü otomatik silmeyi ve geri alınamaz olduğunu yazar; veriyi elle sıfırlama komutu rehberde durur.
8. **Testler:** Derleme, kod kontrolü, site testleri, backend testleri ve uçtan uca testler hatasız geçer. "Sık aranan" kuralları (en fazla 5, sıralama, harf ve boşluk birleştirme, ilgisiz aramaların sayılmaması, arama sonrası güncellenme) otomatik testlerle doğrulanır. Backend, site, Docker, API sözleşmesi, `README.md` ve `ollama.md` içinde "Poyraz" ve "Dalga" geçmez; arama sonucu kontrol raporunda yazar.

## Yapılmayacaklar
- Çalışma alanı oluşturma, silme, yeniden adlandırma.
- Giriş sistemi, kişiye özel "Sık aranan".
- Yeni dosya türü, görüntüden metin okuma (taranmış PDF), 20 MB sınırının değişmesi.
- Soruya yapay zekâyla cümle hâlinde yanıt üretme; arama bugünkü gibi ilgili parçaları listeler.
- Arama sonuç listesinin değişmesi: "kuantum fiziği" gibi ilgisiz bir aramada bugünkü gibi en yakın parçalar yine listelenir; sadece "Sık aranan"a girmez.
- Belge silme. Çalışma alanı 10 belgede dolar; bugünkü "kaynak sınırına ulaştı" mesajı çıkar. Silme ayrı bir task olmalı.
- Kökteki anlatım sayfaları, `embedding-pgvector.md` ve önceki task dosyaları.
- Canlı bağlantı (WebSocket); ilerleme bugünkü gibi düzenli sorgulamayla izlenir.

## Benim verdiğim kararlar
- **Adres aynı kalır:** "Belgelerim" çalışma alanının numarası bugünkü varsayılan alanla aynıdır (42). Adresler `/w/42/arama`, `/w/42/kaynaklar` gibi kalır. Yükleme sayfası `/w/42/belge-yukle`.
- **Ad boş bırakılınca:** Dosya adının son noktadan sonrası atılır, 80 karakterden uzunsa kısaltılır. Kullanılan ad, yüklerken ad kutusunda da görünür. Aynı dosyayı aynı adla ikinci kez yüklersen bugünkü "Bu ad çalışma alanında başka bir kaynakta kullanılıyor." mesajı çıkar, adı değiştirip yüklersin. Aynı kural "Bilgi kaynakları"ndaki "Yeni kaynak ekle" penceresinde de geçerli olur.
- **Yenileme sonrası:** Yükleme başlayınca adrese belgenin numarası eklenir (`?kaynak=10001`). Sayfayı yenilesen ya da adresi başka sekmede açsan da aynı belgenin durumu görünür.
- **"Yeniden dene":** İlk yükleme başarısız olunca belge adıyla birlikte listede kalır (silme yok). Bu yüzden "Yeniden dene" yeni belge açmaz, dosyayı aynı belgeye yeniden yükler.
- **"Sık aranan"da aynı arama:** Büyük/küçük harf, baştaki/sondaki boşluk ve kelimeler arasındaki fazla boşluk yok sayılır. Kaynak adlarındaki kural burada da geçerli: I, ı, İ ve i aynı harf sayılır ("kılavuz" = "kilavuz"). Noktalama farkı ayrı arama sayılır: "Yıllık izin kaç gün?" ile "Yıllık izin kaç gün" iki ayrı aramadır. Gösterilen metin, sayılan aramaların en son yazılmış hâlidir.
- **"Sık aranan" yüklenemezse:** Alan gizlenir, arama normal çalışır.
- **Menü sırası:** Arama · Arama kayıtları · Bilgi kaynakları · Belge yükle.
- **Arama sayfasının açıklaması:** "Müşterinin sorusunu kendi kelimeleriyle yaz…" yerine "Sorunu kendi kelimelerinle yaz; Belgelerim çalışma alanındaki belgelerde aranır." yazar.
- **Uçtan uca testler veriyi siler:** Seed olmadığı için testler kendi verisini kurar. Her test koşusu başlarken çalışma alanındaki bütün belgeleri ve arama kayıtlarını siler, sonra örnek PDF'i yükleme sayfasından yükler. Testleri kendi denemelerini yaptığın sistemde çalıştırırsan yüklediğin belgeler gider. Bu uyarı rehberde de yazar.
- **Eski örnek dosyalar silinir:** `backend/samples/` altındaki demo dosyaları (modem kılavuzu, yardım notları, hata kodları) kalkar; örnek olarak sadece `ornek-dokuman/` kalır.

## Çalışacak roller
planner, backend-coder, frontend-coder, tester, devops, qa. Atlanan rol yok.

## Kullanıcının kararları
Bekleyen soru yok. Verdiğin cevaplar (2026-10-25):
1. **"Sık aranan"da hangi aramalar sayılır? → A.** Aramadaki kelimeler belgede geçiyorsa ya da en yakın parça soruya yeterince yakınsa sayılır. Yakınlık sınırı bir ayardır. Örnek belgeyle ölçülerek seçilir: üç örnek soru içeride, "kuantum fiziği" gibi ilgisiz sorular dışarıda kalır. Sınır tutmazsa backend adımında görürüz ve sana döneriz.
2. **Eski kurulumdaki veri ne olur? → B, otomatik silinir.** Güncellemeden sonraki ilk açılışta eski verinin tamamı silinir: demo çalışma alanları, kaynaklar ve parçalar, yükleme işleri, arama kayıtları. Senin yüklediğin belgeler de buna dahil. Sistem boş "Belgelerim" ile açılır. Silme bir kez olur, geri alınamaz; temiz kurulumda hiçbir şeye dokunmaz. Rehberdeki eski "sıfırlama adımı" zorunlu olmaktan çıkar; yerine bu otomatik silmeyi anlatan bir bilgi bölümü gelir.
3. **"Belgelerim" adı, tek alanda seçicinin gizlenmesi, en fazla 5 arama → A, böyle kalır.**

## Riskler
- **Eski veri geri gelmez:** Güncellemeden sonraki ilk açılışta eski kurulumdaki bütün belgeler silinir. Saklamak istediğin dosyaların asıllarını güncellemeden önce elinde tut. Rehber bunu güncelleme adımının başında yazar. Geliştirme sırasında bu bilgisayardaki local geliştirme ve tam sistem veritabanları da bu yüzden boşalır.
- **Yakınlık sınırı:** Örnek belgede ilgili ve ilgisiz soruların yakınlık değerleri birbirine çok yakın çıkarsa "kuantum fiziği" senaryosu sağlanamaz. Bunu backend adımında ölçüyoruz; tutmazsa sana yeni bir öneriyle döneriz.
- **Testlerin süresi:** Uçtan uca testler her koşuda örnek PDF'i gerçek modelle işlediği için bugünkünden uzun sürer.
- **Belge sınırı:** Silme olmadığı için çalışma alanı 10 belgede dolar. Bugünkü mesaj "birini sil" der ama silme yolu yoktur; ayrı bir task önerilir.

ONAY: VERİLDİ
