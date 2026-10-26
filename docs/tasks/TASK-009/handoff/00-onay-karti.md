TASK: TASK-009
# Onay kartı: Arama sonucuna tıklayınca parçanın tam metni açılsın

## Ne yapılacak
- Arama sonucuna tıklayınca (ya da klavyeyle seçip Enter'a basınca) arama sayfasının üstünde bir panel açılır. Sayfa değişmez.
- Panelde parçanın başlığı (yoksa "Başlıksız parça"), "anlam" / "kelime" etiketleri, belge adı, parçanın belgedeki sırası ve parça numarası (ör. "parça 31 · #1380"), altında da parçanın kısaltılmamış tam metni görünür. Paragraflar ve satır sonları korunur. Metin uzunsa panelin içinde kaydırılır.
- Kelime eşleşmesiyle gelen sonuçlarda eşleşen kelimeler metnin her yerinde sarıyla vurgulanır, sadece başında değil.
- Panel sağ üstteki × düğmesiyle, alttaki "Kapat" düğmesiyle, Esc tuşuyla ya da panelin dışına tıklayarak kapanır. Arama metni, ayarlar ve sonuçlar olduğu gibi kalır, yeni arama yapılmaz. İmleç tıkladığın sonuca döner.
- Paneldeki "Kaynakta göster" belgenin sayfasını açar. Orada bu parça en üstte, vurgulu ve tam metniyle görünür. Diğer parçalar bugünkü gibi kısa önizleme olarak kalır.
- Parça bu arada silinmişse (ör. belgenin yeni sürümü yüklendiyse) panelde metin yerine "Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir." yazar. Panel normal kapanır.

## Bittiğini nasıl anlayacaksın
1. Site açılıyor, testler geçiyor. Uçtan uca testlerden biri şunu yapar: örnek belge yüklenir, arama yapılır, sonuca tıklanır, panelde tam metin görülür, panel kapatılır, "Kaynakta göster" ile belge sayfasına gidilir.
2. Kendin deneyebilirsin (http://localhost:3000, örnek PDF yüklüyken):
   - "Yıllık izin kaç gün?" diye ara ve ilk sonuca tıkla. Panelde bölümün tamamını okursun, sonunda "…" yoktur. Esc'ye bas. Sonuçlar yerindedir, imleç ilk sonuçtadır. "Arama kayıtları" sayfasında bu arama tek sefer görünür.
   - Gelişmiş ayarlardan "Kelime"yi seç, "masraf beyanı" diye ara ve bir sonuca tıkla. "masraf" ve "beyanı" metnin sonlarında da vurgulu görünür.
   - Paneldeki "Kaynakta göster"e tıkla. Belge sayfası açılır, parça en üstte, vurgulu ve tam metniyle görünür. Tarayıcının geri tuşuyla aynı sonuçlara dönersin.
   - Fare kullanmadan Tab ile ikinci sonuca gel ve Enter'a bas. Tab panelin içinde dolaşır. Esc'ye basınca imleç ikinci sonuca döner.
   - Panel açıkken fare tekerleğini çevir. Arkadaki sayfa kaymaz.
   - Telefon genişliğinde (360 piksel) de panel ekrana sığar, metin panelin içinde kayar.
3. Arama yaptıktan sonra başka bir sekmede belgenin yeni sürümünü yükle, sonra ilk sekmedeki eski sonuca tıkla. Panelde "Bu parça artık yok…" mesajı görünür.
4. Panel erişilebilirlik kontrolünden (ekran okuyucu ve klavye uyumu) geçer.

## Yapılmayacaklar
- Belgenin tamamını (bütün parçaları birleştirerek ya da orijinal PDF olarak) gösterme.
- Panelde önceki ya da sonraki parçaya geçiş.
- Metni düzenleme, kopyala düğmesi, paylaşma bağlantısı.
- Yapay zekâyla soruya cevap üretme.
- Arama kayıtları sayfasındaki satırlara tıklama.
- Canlı bağlantı (WebSocket).
- Sistemin arka ucunda değişiklik yapılmaz. Arama sonucu tam metni zaten getiriyor.
- Belge sayfasında vurgulanan parçanın içinde kelime vurgusu yapılmaz. Lab'daki gibi sadece tam metin gösterilir.

## Benim verdiğim kararlar
- **Metin hemen görünür, varlığı arkadan kontrol edilir.** Tam metin arama sonucuyla birlikte geldiği için panel beklemeden dolar. Panel açılırken parçanın hâlâ var olup olmadığı sisteme ayrıca sorulur. Parça yoksa metin kaldırılır ve "Bu parça artık yok…" mesajı çıkar. Belge silinmişse mesaj "Kaynak bulunamadı." olur.
- **Parça yoksa "Kaynakta göster" gizlenir.** Panelde sadece mesaj ve kapatma düğmeleri kalır, çünkü gösterilecek parça yoktur.
- **Kontrol yapılamazsa metin yine görünür.** Sunucuya o an ulaşılamazsa uyarı çıkmaz. Elimizdeki metin arama sonucundan geldiği için doğru kabul edilir.
- **Sonuç artık bir bağlantı değil, bir düğme.** Orta tıkla ya da sağ tıkla "yeni sekmede aç" artık sonuç listesinde çalışmaz. Yeni sekmede açmak istersen paneldeki "Kaynakta göster"i kullanabilirsin.
- **Panel adres çubuğunda yer almaz.** Sayfayı yenileyince panel kapanır. Arama sonuçları da bugünkü gibi yenilemede kaybolur.
- **İki kapatma düğmesi var:** sağ üstte × ("Paneli kapat"), altta "Kapat". Panel açılınca imleç × düğmesindedir. Düzen lab'daki panelle aynı, genişliği yaklaşık 620 piksel.
- **Arka sayfa kayması bütün pencerelerde durur.** "Yeni kaynak ekle" ve "Yeni sürüm yükle" pencereleri açıkken de arka sayfa artık kaymaz. Bu iki pencerenin görünüşü ve davranışı başka bir şekilde değişmez. Dışına tıklayınca kapanma sadece yeni panelde olur; yükleme pencereleri yanlışlıkla kapanmasın diye öyle kalır.

## Çalışacak roller
planner, frontend-coder, tester, qa.

Atlanan roller:
- **backend-coder:** Arama sonucu parçanın tam metnini ve vurgu konumlarını zaten tam metin üzerinden veriyor. Arka uçta değişiklik gerekmiyor.
- **devops:** Kurulum dosyalarında, README'de ve `ollama.md`'de değişiklik yok. Siteyi yeni kodla yeniden derleyip açmayı tester ve qa yapar.

## Kararını bekleyen sorular
Yok.

## Riskler
- "masraf beyanı" testi, örnek belgede bu kelimelerin bir parçanın sonlarına doğru geçmesine dayanıyor. Geçmiyorsa tester aynı durumu sağlayan başka bir arama kelimesi seçer ve raporuna yazar.
- Ortak pencere parçası değiştiği için "Yeni kaynak ekle" ve "Yeni sürüm yükle" pencereleri de etkilenebilir. Mevcut testleri geçmeli ve qa ikisini gözle kontrol eder.

ONAY: VERİLDİ
