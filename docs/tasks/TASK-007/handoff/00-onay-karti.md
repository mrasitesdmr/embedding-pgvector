TASK: TASK-007
# Onay kartı: Backend sınıflarına Türkçe amaç yorumu

## Ne yapılacak
- Backend'deki her sınıf dosyasının en üstüne, sınıfın ne işe yaradığını anlatan 1-2 satırlık Türkçe bir açıklama eklenir. Toplam 157 dosya: uygulama kodunda 98, testlerde 59.
- Açıklama sınıfın amacını söyler. İçindeki parçaları tek tek saymaz, sınıfın adını tekrar etmez.
- Test dosyalarının açıklaması, testin hangi sınıfın hangi davranışını doğruladığını söyler.
- Kodun kendisinde hiçbir şey değişmez: sadece açıklama satırları eklenir.
- Backend'in geliştirici notlarındaki eski "yorum yasağı" ifadesi yeni kurala göre düzeltilir.

## Bittiğini nasıl anlayacaksın
1. IntelliJ'de `backend/src/main/.../source/service/SourceService.java`'yı açarsın. En üstte şu yazar: "Çalışma alanındaki kaynakları sayfa sayfa listeler ve tek bir kaynağı en son içe alma işiyle birlikte getirir."
2. Bir controller'da `SourceService` adının üzerine fareyle gelirsin. Açılan kutuda aynı açıklamayı görürsün.
3. Herhangi bir test dosyasını açarsın, ör. `SourceServiceTest.java`. En üstte hangi sınıfın hangi davranışını doğruladığı yazar.
4. Raporlarda şunlar yazar:
   - Açıklaması eksik dosya kalmadı.
   - Hiçbir açıklama 2 satırı geçmiyor ve hepsi Türkçe.
   - Değişiklik öncesi alınan kopyayla karşılaştırınca sadece açıklama satırları eklenmiş.
5. Backend testleri hatasız geçer. Test sayısı önceki task'taki gibi 417'dir.
6. Son kontrolde 157 açıklamanın her biri sınıfın gerçekte yaptığı işle karşılaştırılır. Uymayan varsa düzeltilir.

## Yapılmayacaklar
- Sınıfların içindeki işlemlere ve sınıf içindeki küçük yardımcı yapılara açıklama eklenmez.
- Site, Docker, veritabanı dosyaları ve API sözleşmesi değişmez.
- İsim düzeltme ya da kod düzenleme yapılmaz.
- Açıklamaları otomatik denetleyen yeni bir test yazılmaz. Test sayısı aynı kalmalı; denetim komutlarla yapılır.

## Benim verdiğim kararlar
- **Sadece paket bilgisi taşıyan 35 dosya açıklama almaz.** Bunlar uygulama kodunda her klasörde bulunan küçük dosyalardır. İçlerinde sınıf yoktur, bu yüzden task'ın kuralı bunları kapsamaz. Lead'in verdiği "133 dosya" sayısı bu 35 dosyayı da içeriyordu. Açıklama alan uygulama dosyası sayısı 98'dir.
- **`SourceService` açıklaması task'taki örnekten farklı olur.** Örnekteki cümle "kaynak ekleme, listeleme ve silme… kaynak limitini uygular" diyor. Bu sınıf kaynak eklemiyor, silmiyor ve limit uygulamıyor: ekleme ve limit başka yerde, silme ise projede yok. Task'ın "yorum doğru olmalı" kuralı örnekten önce geldiği için gerçek işi yazılır (1. maddedeki cümle).
- **Test açıklamaları test edilen sınıfın adını kullanabilir.** Örnek: "SourceService'in kaynak listeleme ve tek kaynak getirme davranışını … doğrular." Uygulama kodundaki açıklamalarda kod adı geçmez, sadece Türkçe kelimeler kullanılır.
- **Yazım biçimi:**
  - Her açıklama aynı düzende yazılır ve noktayla biten bir cümledir.
  - Satır 120 karakteri geçmez.
  - Terimler sitedekiyle aynıdır: çalışma alanı, kaynak, parça, içe alma işi, arama kaydı. "embedding" ve "pgvector" gibi yerleşik terimler olduğu gibi kalır.
- **Karşılaştırma için kopya alınır.** Proje bir sürüm kontrol deposu değil. Değişiklik başlamadan önce lead backend kodunun bir kopyasını geçici klasöre (`scratch/`) alır. Son kontrol bu kopyaya göre yapılır, kopya iş bitince silinir.

## Çalışacak roller
planner, backend-coder (uygulama kodu), tester (test dosyaları ve test sayısı kontrolü), qa (açıklamaların doğruluğu ve "sadece açıklama eklendi" kontrolü). Sıra: backend-coder → tester → qa.
Atlanan: frontend-coder (site etkilenmiyor), devops (Docker ve rehberler etkilenmiyor).

## Kararını bekleyen sorular
Yok.

## Riskler
- **Yanlış ama makul görünen açıklama:** 157 açıklamanın doğruluğu elle kontrol edilir; ince bir hata gözden kaçabilir. Bunu azaltmak için yazan kişi, emin olamadığı dosyaları raporuna yazar. Son kontrol önce bunlara bakar.
- **Eski kurallar duran yardımcı dosyalar:** Ekibin kullandığı bazı yardımcı kural dosyalarında hâlâ "sınıf üstüne yorum yazılmaz" cümlesi var. Bu task'ta kontrolde yeni kural esas alınır. O dosyaların düzeltilmesini lead sana ayrıca soracak.

ONAY: VERİLDİ
