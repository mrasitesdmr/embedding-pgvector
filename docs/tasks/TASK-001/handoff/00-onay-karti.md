TASK: TASK-001
# Onay kartı: Lokal embedding modeli (Ollama) ve ilk embedding isteği

## Ne yapılacak
- Backend'in ilk hali kurulur. Henüz ekran yok.
- Backend'e bir metin gönderince backend bu metni senin bilgisayarındaki Ollama modeline verir ve geri şunları döndürür: kullanılan modelin adı, vektörün kaç sayıdan oluştuğu, vektörün kendisi ve işlemin kaç milisaniye sürdüğü.
- Metnin başındaki ve sonundaki boşluklar silinir. Boş metin ve 2.000 karakterden uzun metin reddedilir; bu durumda modele hiç istek gitmez.
- Ollama kapalıysa "embedding servisine ulaşılamıyor", model indirilmemişse "model bulunamadı" hatası döner. İkisi birbirinden ayrı hatalardır ve backend bu durumlarda kapanmaz.
- Kökteki `ollama.md` rehberi seçilen modele göre güncellenir ve "Backend üzerinden istek atma" bölümü gerçek komutlarla doldurulur. Rehberdeki Docker bölümü çıkarılır.

## Bittiğini nasıl anlayacaksın
1. `ollama.md`'deki komutları sırayla çalıştırınca Ollama'nın çalıştığını görürsün, model listede çıkar ve terminalden doğrudan Ollama'ya attığın istek bir vektör döndürür.
2. Backend'i başlatıp rehberdeki komutla "Şifremi unuttum, online işlemlere giremiyorum" metnini gönderirsin. Yanıtta model adı (`bge-m3`), boyut (1024), 1024 sayılık vektör ve süre görünür.
3. Aynı metni iki kez gönderirsin, iki vektör birebir aynı çıkar.
4. `"  modem ışığı kırmızı yanıyor  "` gönderirsin; boşluklar silinmiş haliyle işlenir.
5. Boş metin, sadece boşluk ya da 2.001 karakterlik metin gönderirsin; "girdi geçersiz" hatası alırsın.
6. Ollama kapalıyken istek atarsın, "embedding servisine ulaşılamıyor" hatası gelir. Ollama'yı açıp backend'i yeniden başlatmadan tekrar denersin, bu kez çalışır.
7. Ayarlarda indirilmemiş bir model adı verirsin, "model bulunamadı" hatası gelir.
8. Backend'in testleri Ollama kapalıyken de geçer.

## Yapılmayacaklar
- Arayüz / ekran (sonraki task'larda).
- Veritabanı, pgvector, vektörlerin kaydedilmesi.
- Doküman yükleme, parçalama, toplu embed etme.
- Benzerlik hesaplama ve arama.
- Canlı ilerleme (WebSocket).
- Backend'in Docker'da çalışması ve tüm sistemin Docker ile kurulması.
- Ollama Docker'da çalıştırılmaz (senin kararın). Ollama sadece bilgisayarında çalışır.
- Yeniden deneme: Ollama hata verirse backend isteği kendiliğinden tekrar denemez, hatayı hemen bildirir.

## Benim verdiğim kararlar
- **Hata türleri:** Boş metin ile çok uzun metin aynı "girdi geçersiz" hatasını döndürür; hangi kuralın bozulduğu hatanın ayrıntısında ayrıca yazılır. İleride ekran bu ayrıntıya bakıp doğru Türkçe mesajı gösterecek.
- **2.000 karakter sınırı:** Sınır, baştaki ve sondaki boşluklar silindikten sonra kalan metne uygulanır. Metnin içindeki boşluklar ve satır sonları olduğu gibi kalır. Türkçe harflerin her biri 1 karakter sayılır; emoji gibi bazı özel işaretler 2 karakter sayılır (ileride ekranın saydığıyla aynı sonuç çıksın diye).
- **Süre:** Yanıttaki süre, backend'in modele isteği gönderdiği andan cevabı aldığı ana kadar geçen süredir. Model ilk istekte belleğe yüklendiği için ilk süre birkaç saniye olabilir, sonrakiler hızlıdır.
- **Bekleme sınırı:** Ollama 60 saniye içinde cevap vermezse "embedding servisine ulaşılamıyor" hatası döner. Ollama hiç açık değilse hata en geç 2 saniyede gelir.
- **"Model bulunamadı" hatası:** Bu hata, senin isteğinde değil sunucunun ayarında bir eksik olduğunu gösterir. Bu yüzden "servis şu an kullanılamıyor" grubunda yer alır ama kendine ait ayrı bir hata kodu vardır. Modeli indirince düzelir.
- **Vektör yanıtta:** Vektörün tamamı (1024 sayı) bu uç noktanın yanıtında döner. Bunu sadece bu uç nokta yapar; ileride arama gibi diğer yanıtlarda vektör olmayacak.
- **Ayarlar:** Model adı ve Ollama'nın adresi ayarlardan değiştirilebilir. Varsayılanlar `bge-m3` ve `http://localhost:11434`. Rehbere, backend'i başka bir modelle başlatmanın komutu da yazılır.
- **Rehberi kim günceller:** `ollama.md`'yi backend geliştiricisi günceller. Docker ile çalıştırma bölümünü rehberden çıkarır, sonraki bölümün numarasını düzeltir ve hata tablosundaki Docker'dan bahseden ifadeleri temizler.
- **Testler:** Testler gerçek Ollama yerine, testin içinde açılan küçük sahte bir sunucuyla çalışır. Bu yüzden Ollama kapalıyken de geçerler.
- **Test kalitesi araçları:** Testlerin kodun ne kadarını denediğini ölçen bir araç ve testlerin hataları gerçekten yakalayıp yakalamadığını ölçen bir araç kurulur. İkisi de proje kuralı gereği kuruluyor ve sadece geliştirme sırasında çalışıyor.
- **Kontrolde Ollama'n durdurulmaz:** Son kontrolü yapan rol, "Ollama kapalı" durumunu senin çalışan Ollama'nı durdurarak değil, backend'i boş bir adrese yönlendirerek dener.

## Çalışacak roller
planner, backend-coder, tester, qa.
Atlanan: frontend-coder (bu task'ta ekran yok), devops (Docker kullanılmıyor, senin kararın).

## Kararını bekleyen sorular
1. Hangi embedding modelini kullanalım?
   Neden önemli: Vektörün boyutu modele göre değişir ve ileride veritabanındaki vektör alanı bu boyuta göre kurulacak. Sonradan modeli değiştirmek, kaydedilmiş bütün vektörlerin yeniden hesaplanması demektir.

   | Model | Vektör boyutu | İndirme boyutu | Türkçe | Not |
   |-------|---------------|----------------|--------|-----|
   | `bge-m3` | 1024 | ~1,2 GB | Çok dilli, Türkçesi iyi | **Bilgisayarında zaten kurulu.** Soru ve doküman aynı biçimde gönderilir, ön ek gerekmez. Uzun metinleri de (yaklaşık 8.000 token) işler. |
   | `qwen3-embedding:0.6b` | 1024 | ~640 MB | Çok dilli, destekler | Daha yeni ve daha küçük. En iyi sonuç için sorulara özel bir talimat cümlesi eklemek gerekir. Etiketsiz `qwen3-embedding` adı 4,7 GB'lık büyük sürümü indirir, dikkat. |
   | `embeddinggemma` | 768 | ~620 MB | Çok dilli, destekler | Küçük ve hızlı. En iyi sonuç için soru ve dokümanın başına farklı ön ekler eklemek gerekir; en fazla yaklaşık 2.000 token işler. |
   | `nomic-embed-text` | 768 | ~275 MB | Ağırlıkla İngilizce | En küçük, ama Türkçede zayıf; kural 2'ye uymuyor. |

   Seçenekler: A) `bge-m3` B) `qwen3-embedding:0.6b` C) `embeddinggemma`
   Önerim: A. Model bilgisayarında zaten kurulu ve çalışıyor, ek indirme yok. Türkçesi güçlü. Ön ek gerektirmediği için lab'da "metin vektöre çevrildi" adımını göstermek en sade haliyle olur. Uzun metinleri işleyebilmesi ileride doküman parçaları için de rahatlık sağlar. Bedeli: diğerlerinden biraz daha fazla bellek kullanır ve biraz daha yavaştır.

2. Backend'in uç noktalarını tarayıcıda görüp denemek için hazır bir dokümantasyon sayfası ekleyelim mi?
   Neden önemli: Bunun için yeni bir kütüphane gerekiyor (`springdoc-openapi`, "Swagger UI" sayfası). Bu sayfa, terminal kullanmadan tarayıcıdan metin gönderip yanıtı görmeni sağlar. Ayrıca son kontrolde backend'in sözleşmeye birebir uyduğunu otomatik karşılaştırmaya yarar.
   Seçenekler: A) Evet, ekleyelim. B) Hayır, istekler sadece terminalden atılsın.
   Önerim: A. Hem senin denemen kolaylaşır hem de "backend sözleşmeyle birebir uyumlu" kabul kriteri elle değil araçla kontrol edilir. Kütüphane sadece belge sayfası üretir, uygulamanın davranışını değiştirmez.

## Riskler
- Proje Java'nın çok yeni bir sürümünü (26) kullanıyor. Test kalitesi araçlarının bu sürümü desteklemediği ortaya çıkarsa ekip sana haber verir. Java sürümünü düşürmek senin kararın olur.
- İlk istek, model belleğe yüklendiği için birkaç saniye sürebilir. Bu bir hata değil.
- 1. soruda `bge-m3` dışında bir model seçersen modeli önce senin indirmen gerekir (rehberde komutu olacak).

ONAY: VERİLDİ
