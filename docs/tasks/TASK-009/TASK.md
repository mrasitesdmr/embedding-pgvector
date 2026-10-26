# TASK-009: Arama sonucuna tıklayınca parçanın tam metni açılsın

## Ne isteniyor
Bugün arama sonucuna tıklanınca kaynak detay sayfası açılıyor; parça orada sadece kısaltılmış önizleme olarak listede görünüyor ve tam metin hiçbir yerde okunamıyor. Bu task'tan sonra kullanıcı bir arama sonucuna tıkladığında o parçanın tam metni arama sayfasında açılır. Kullanıcı metni baştan sona okur, kapatınca aynı arama sonuçlarına geri döner.

`lab.html` de aynı mantıkla güncellendi (kullanıcının isteğiyle, lead tarafından; hiçbir rol `lab.html`'ye yazmaz). Lab'da sonuca tıklanınca tam metin paneli açılıyor; panelde etiketler, vurgu, "Kapat" ve "Kaynakta göster" var; kaynak sayfasında vurgulanan parça tam metinle görünüyor. Frontend bu davranışı referans alır.

## Kurallar

### Açılma
1. Arama sonuç listesindeki bir sonuca tıklanınca (ya da klavyeyle seçilip Enter'a basılınca) o parçanın tam metni arama sayfasının üstünde bir panelde (dialog) açılır. Sayfa değişmez.
2. Panelde şunlar görünür: parça başlığı (başlık yoksa "Başlıksız parça"), kaynak adı, parça numarası, parça id'si ve parçanın tam metni. Metin kısaltılmaz, sonunda "…" olmaz.
3. Metin, belgedeki satır ve paragraf yapısını korur (satır sonları kaybolmaz). Metin uzunsa panelin içinde kaydırılır; panel ekrandan taşmaz.
4. Kelime eşleşmesiyle gelen sonuçlarda eşleşen kelimeler panelde de, sonuç listesindeki gibi vurgulu görünür. Vurgu metnin tamamında doğru yerlerdedir (sadece ilk 190 karakterde değil).
5. Sonucun "anlam" / "kelime" etiketleri panelde de görünür.

### Kapanma ve geri dönüş
6. Panel kapatma düğmesiyle, Esc tuşuyla ve panelin dışına tıklanarak kapanır.
7. Panel kapanınca arama metni, ayarlar ve sonuç listesi aynen kalır; arama yeniden yapılmaz, yeni arama kaydı oluşmaz. Odak, tıklanan sonuca geri döner.
8. Panel açıkken arkadaki sayfa kaydırılmaz ve klavye odağı panelin dışına çıkmaz.

### Kaynağa gitme
9. Panelde "Kaynakta göster" bağlantısı vardır. Tıklanınca bugünkü davranış çalışır: kaynak detay sayfası açılır ve bu parça vurgulu olarak en üstte görünür.
10. Kaynak detay sayfasında vurgulanan parçanın da tam metni görünür (kısaltılmaz). Diğer parçalar bugünkü gibi önizleme olarak kalır.

### Hata durumları
11. Parça açılırken silinmiş ya da kaynağın yeni sürümüyle değişmişse, panelde Türkçe "Bu parça artık yok" türünde bir mesaj görünür ve sayfa bozulmaz. Mesaj bugünkü `errorCode` eşlemesiyle gösterilir.
12. Parçanın içeriği HTML/script içerse bile metin olarak gösterilir, çalıştırılmaz.

## Örnek senaryolar
- Kullanıcı örnek PDF'i yüklemiş, "Yıllık izin kaç gün?" diye anlam araması yapar. İlk sonuç "Yıllık izin ve diğer izinler" parçasıdır; listede metnin ilk iki satırı görünür. Sonuca tıklar, panel açılır, bölümün tüm metnini okur. Esc'ye basar; aynı sonuç listesi yerindedir, odak ilk sonuçtadır.
- Kullanıcı "masraf beyanı" diye kelime araması yapar. Bir sonuca tıklar; panelde "masraf" ve "beyanı" kelimeleri metnin sonlarına doğru geçtiği yerlerde de vurguludur.
- Kullanıcı paneldeki "Kaynakta göster"e tıklar. Kaynak detay sayfası açılır, parça en üstte vurgulu ve tam metniyle görünür.
- Kullanıcı arama yaptıktan sonra başka bir sekmede kaynağın yeni sürümünü yükler, sonra eski sonuca tıklar. Panelde Türkçe "parça artık yok" mesajı görünür; panel kapatılabilir.
- Kullanıcı sadece klavyeyle Tab ile ikinci sonuca gelir, Enter'a basar, panel açılır; Tab odağı panelin içinde dolaşır, Esc ile kapanır ve odak ikinci sonuca döner.

## Kabul kriterleri
- Komutlar hatasız geçiyor:
  - `cd backend && ./mvnw verify` (backend değişirse)
  - `cd frontend && npm run build`
  - `cd frontend && npm run lint`
  - frontend unit testleri ve Playwright e2e testleri
- Panelin açılması, tam metnin kısaltılmadan görünmesi, vurgunun metnin tamamında doğru olması, kapanınca sonuçların ve odağın korunması ve "parça artık yok" durumu otomatik testlerle doğrulanıyor.
- En az bir uçtan uca test: örnek belge yüklenir, arama yapılır, sonuca tıklanır, panelde tam metin görülür, panel kapatılır, "Kaynakta göster" ile kaynak detayına gidilir.
- Panel axe erişilebilirlik kontrolünden geçiyor.
- Panel açılıp kapanınca yeni arama kaydı oluşmadığı test ya da qa raporuyla doğrulanıyor.

## Kapsam dışı
- Belgenin tamamını (tüm parçaları birleştirerek ya da orijinal PDF'i) görüntüleme.
- Panelde önceki/sonraki parçaya geçiş.
- Metni düzenleme, kopyalama düğmesi, paylaşma bağlantısı.
- Soruya yapay zekâyla yanıt üretme (RAG/LLM).
- Arama kayıtları sayfasındaki satırlara tıklama davranışı.
- WebSocket.

## Etkilenen katmanlar
- [ ] Backend — gerekirse planner belirler (arama sonucundaki içerik tam metin değilse)
- [x] Frontend (sonuç paneli, kaynak detayında vurgulanan parçanın tam metni)
- [ ] API sözleşmesi — gerekirse planner belirler
- [ ] Veritabanı (migration, pgvector)
- [ ] WebSocket
- [ ] Docker
