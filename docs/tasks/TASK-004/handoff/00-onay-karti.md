TASK: TASK-004
# Onay kartı: Arayüz: dosya yükleme ve içe alma ilerlemesi

## Ne yapılacak
- **Yeni kaynak ekleme:** Bilgi kaynakları sayfasında "Yeni kaynak ekle" butonu olur. Açılan pencerede kaynağa bir ad verip dosya seçersin (PDF, TXT, MD, MARKDOWN, CSV). Yeni kaynak listede "İşleniyor" olarak görünür, iş bitince "Hazır" olur.
- **Yeni sürüm yükleme:** PDF, Metin / Markdown ve CSV kaynaklarının satırında ve kaynak sayfasının başlığında "Yeni sürüm yükle" butonu olur. Pencerede şunlar bulunur:
  - kaynağın şu anki sürümü ve parça sayısı,
  - seçtiğin dosyanın adı ve boyutu,
  - isteğe bağlı bir sürüm etiketi (ör. 3.2),
  - "yeni sürüm hazır olana kadar arama eski parçaları döndürür" bilgisi.
- **İlerleme:** Yükleme sürerken kaynağın satırında aşama yazısı ("Sıraya alındı", "Dosya okunuyor", "Parçalara bölünüyor", "Embed ediliyor · 96/214 parça", "Yazılıyor") ve dolan bir çubuk görünür. Durum saniyede bir sorulur.
- **Bitiş:** İş bitince liste yenilenir, satır kısa bir süre renklenir ve alt ortada "Dalga T5 modem kullanım kılavuzu v3.2 hazır · 214 parça" gibi bir bildirim çıkar. İş başarısız olursa satırda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" gibi Türkçe neden yazar.
- **Kaldığı yerden devam:** Sayfayı yenilesen, başka sayfaya gidip dönsen ya da adresi yeni sekmede açsan da süren yüklemenin ilerlemesi görünmeye devam eder. Bunun için backend, her kaynakla birlikte o kaynağın son yükleme işinin bilgisini de gönderir.
- **Konsol hatası:** Üretimde site açılınca tarayıcı konsolunda çıkan yazı tipi güvenlik hatası giderilir. Güvenlik politikası gevşetilmez; yazı tipleri ayrı dosya olarak yüklenir.

## Bittiğini nasıl anlayacaksın
1. **Açılış:** Sistemi temiz bir veritabanıyla Docker'dan açarsın. Üç servis "healthy" olur. Site açılınca tarayıcı konsolunda güvenlik politikası hatası görünmez.
2. **Modem kılavuzu 3.2:** Bilgi kaynakları sayfasında modem kılavuzunun satırında "Yeni sürüm yükle"ye basarsın. `backend/samples/Dalga-T5-Kullanim-Kilavuzu-v3.2.pdf` dosyasını seçip sürüme `3.2` yazarsın.
   - Pencere kapanır, satırda aşamalar sırayla görünür.
   - O sırada sayfayı yenilersen ilerleme kaldığı yerden görünmeye devam eder.
   - Bitince kaynak "Hazır" olur, sürümü 3.2 ve yeni parça sayısıyla görünür; "hazır" bildirimi çıkar.
   - İş sürerken aynı kaynağın "Yeni sürüm yükle" butonu pasiftir.
3. **Arama yükleme sırasında:** Yükleme sürerken kılavuzu ararsan sonuçlar eski sürümün parçalarından gelir. İş bitince aynı arama yeni parçaları döndürür.
4. **Yeni kaynak:** "Yeni kaynak ekle" ile `backend/samples/yardim-notlari.md` dosyasını yüklersin. Kaynak listede "İşleniyor", sonra "Hazır" olur. İçinde geçen bir kelimeyi arayınca parçası sonuçlarda çıkar.
5. **Geçersiz dosyalar:** `.docx`, 25 MB'lık PDF ya da boş `.txt` seçersin. Dosya alanının altında Türkçe uyarı çıkar, yükleme yapılmaz.
6. **Ollama kapalı:** Dosya yüklersin. İş başarısız olur ve satırda "Son yükleme başarısız: embedding servisine (Ollama) ulaşılamıyor" yazar. Kaynağın eski sürümü aranabilir kalır.
7. **Görsel PDF:** Sadece resim içeren bir PDF yüklersin. Satırda "Son yükleme başarısız: dosyada metin yok …" yazar.
8. **Buton olmayanlar:** Web sitesi ve Talep geçmişi kaynaklarında "Yeni sürüm yükle" butonu yoktur.
9. **Klavye:** Pencereyi klavyeyle açarsın. Odak pencerenin içine gider, Tab ile dışarı çıkmaz. Esc'e basınca pencere kapanır, odak açan butona döner. Yüklemenin tamamı sadece klavyeyle yapılabilir.
10. **Testler:**
    - Derleme, kod kontrolü, site testleri ve backend testleri hatasız geçer.
    - Gerçek sisteme karşı uçtan uca testler yeni sürüm yükleme ve yenileme sonrası devam, yeni kaynak ekleme, geçersiz dosya uyarısı, klavyeyle pencere ve konsolda güvenlik hatası olmamasını dener.
    - TASK-003'ün uçtan uca testleri de geçer.

## Yapılmayacaklar
- Canlı bağlantıyla anlık güncelleme (WebSocket). Başka bir sekmede başlatılan yükleme, bu sayfa yenilenince görünür.
- Kaynak silme, yüklemeyi iptal etme, eski sürüme geri dönme.
- Web sitesi tarama ve talep geçmişi aktarımı.
- Aynı anda birden fazla dosya yükleme, sürükle-bırak.
- Çalışma alanı oluşturma ve silme, model değiştirme ve yeniden embed etme.
- Yapay zekâ ile cevap üretme, giriş ve kullanıcı, mobil yerleşim.
- TASK-003 ekranlarında yükleme dışında değişiklik.
- Docker ve sunucu ayarlarında değişiklik (gerekmiyor).

## Benim verdiğim kararlar
- **Pencere ne zaman kapanır:** Sunucu dosyayı kabul edince kapanır. Gönderme sürerken buton "Yükleniyor…" yazar ve pencere kapatılamaz. Sunucu dosyayı reddederse (ör. aynı ad başka kaynakta var, metin dosyası UTF-8 değil, kaynak sınırı dolu) pencere açık kalır ve hata ilgili alanın altında Türkçe yazar. Böylece seçtiğin dosya ve ad kaybolmaz.
- **Kaynak adı:** Baştaki ve sondaki boşluklar silinir, ad 2-80 karakter olmalıdır. Backend de aynı kuralı uygular. Tek harflik ad kabul edilmez.
- **Sürüm etiketi:** İsteğe bağlıdır, en fazla 40 karakterdir. Boş bırakılırsa kaynağın sürümü "sürüm yok" olur.
- **Dosya sınırı:** Tam 20 MB kabul edilir, bir bayt fazlası edilmez. Uzantıda büyük/küçük harf fark etmez (`KILAVUZ.PDF` olur).
- **Yeni sürümde dosya türü:** Seçilen dosya kaynağın türüne uymalıdır: PDF kaynağa sadece PDF, Metin / Markdown kaynağa TXT / MD / MARKDOWN, CSV kaynağa CSV. Dosya seçme penceresi de sadece o türleri gösterir. Uymazsa "Bu kaynağa sadece PDF dosyası yüklenebilir." yazar ve dosya gönderilmez.
- **"Hazır" bildirimi:**
  - Başka bir sayfadayken (ör. Arama) iş biterse bildirim orada da çıkar.
  - Sürüm etiketi verilmediyse sürüm kısmı yazılmaz: "Yardım notları hazır · 12 parça".
  - Bildirim 5 saniye görünür, ekran okuyucu da okur.
  - Satır 3 saniye renkli kalır.
- **Başarısız yükleme:**
  - Ayrı bir bildirim çıkmaz; neden satırda ve kaynak sayfasında yazar.
  - Bu yazı, sayfayı yenilesen de, o kaynağa yeni bir yükleme başlayana kadar kalır.
  - Yeni sürüm yüklemesi başarısız olursa kaynak "Hazır" kalır, çünkü eski sürüm hâlâ aranabilir. Neden yine satırda görünür.
- **Eski işler:** Sayfayı açtığında zaten bitmiş bir yükleme için bildirim tekrar çıkmaz.
- **Bağlantı koparsa:** Yükleme sürerken sunucuya bir an ulaşılamazsa satırda son görülen aşama kalır ve sorgu devam eder. Sunucu iş sürerken kapandıysa açıldıktan sonra satırda "Son yükleme başarısız: iş yarıda kaldı" yazar.
- **İlerleme kaynak sayfasında da görünür:** Kaynak sayfası açıkken yükleme sürerse aşama yazısı ve çubuk başlığın altında görünür. Bitince sürüm, parça sayısı ve parçalar yeni sürümü gösterir.
- **Uçtan uca testler veriyi değiştirir:** Her koşuda boş yeri olan bir çalışma alanına bir kaynak eklenir ve modem kılavuzuna 3.2 yüklenir. Kaynak silme olmadığı için bu kaynaklar kalır. Bu yüzden elle kontrollerde temiz bir veritabanı kullanılır.

## Çalışacak roller
planner, backend-coder, frontend-coder, tester, qa.
Atlanan: devops. Docker, sunucu ve güvenlik politikası dosyalarında değişiklik yok; sistemi qa yeniden derleyerek açar.

## Kararını bekleyen sorular
Yok.

## Riskler
- **Yenileme testi:** Uçtan uca test, sayfayı yükleme bitmeden yenilemelidir. Gerçek Ollama ile 214 parçanın işlenmesi birkaç saniyeden uzun sürdüğü için sorun beklenmiyor. Makine çok hızlıysa test kararsız olabilir; tester süreyi raporlar.
- **Uçtan uca test süresi:** Yükleme testleri gerçek Ollama ile çalıştığı için her biri bir dakikaya kadar sürebilir.

ONAY: VERİLDİ
