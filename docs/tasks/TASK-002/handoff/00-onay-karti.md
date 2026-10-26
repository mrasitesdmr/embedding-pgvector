TASK: TASK-002
# Onay kartı: Lab'ın backend ve PostgreSQL (pgvector) tarafı: kaynak yükleme, parçalama, embed etme ve arama

## Ne yapılacak
- Sistem Docker ile tek komutla açılır: veritabanı (PostgreSQL + pgvector) ve backend container'da çalışır, Ollama senin Mac'inde çalışmaya devam eder. Ekran yok; her şey terminalden ve tarayıcıdaki API sayfasından (Swagger UI) denenir.
- İlk açılışta lab'daki veri gerçekten kurulur: Dalga Fiber (4 kaynak, lab'daki parçalar) ve 36 başka şirketin benzer konulu (şifre, hata kodu, taşınma, fatura…) metinleri. Toplam 1.410 parça, hepsi bge-m3 ile gerçekten vektöre çevrilir. Dalga Fiber'in payı yaklaşık %3, lab'daki gibi.
- Bir kaynağa PDF, TXT/Markdown ya da CSV yükleyebilirsin: yeni kaynak olarak ya da var olan kaynağın yeni sürümü olarak. Yükleme hemen "kabul edildi" der ve bir iş numarası verir; dosya arka planda okunur, parçalara bölünür, 96'şarlık gruplar halinde vektöre çevrilir ve eski parçaların yerine tek seferde yazılır. İşin hangi aşamada olduğunu ve kaç parçanın bittiğini sorgulayabilirsin.
- Bir çalışma alanında üç şekilde arama yapabilirsin: kelime araması, anlam (vektör) araması ve ikisinin birleştiği hybrid arama. Yanıtta her sonucun hangi yolla bulunduğu, sıraları, puanları, mesafeleri, süreler ve istersen veritabanının sorgu planı görünür. Lab'daki ayarlar (filtre stratejisi, `ef_search`) her aramada değiştirilebilir.
- Her arama kaydedilir; kayıtlar yeniden eskiye listelenir.
- Ayarlardaki model, parçaları yazan modelden farklıysa anlam ve hybrid arama ile yükleme açık bir hatayla durur; kelime araması çalışmaya devam eder (lab'da bu kontrol yoktu).

## Bittiğini nasıl anlayacaksın
1. Docker ile sistemi açarsın, iki servis de "healthy" olur. Birkaç dakika içinde log'da başlangıç verisinin bittiği yazar; Dalga Fiber'in 4 kaynağı "hazır" görünür.
2. "Şifremi unuttum, online işlemlere giremiyorum" kelime aramasında 0 sonuç verir; hybrid aramada "Online İşlemler parolanı sıfırlama" ilk 10'da çıkar ve anlam araması tarafından bulunduğu yazar.
3. "TS-999 hata kodu" hybrid aramada TS-999 parçası, kelime araması da bulduğu için öne geçer.
4. "Taşınıyorum, internetimi yeni eve nasıl aldırırım?" araması "önce ara sonra filtrele" ayarıyla 10'dan az sonuç döndürür, yanıtta "kısa kaldı" işareti ve log'da uyarı görünür. Aynı arama "filtreyi taramada uygula" ayarıyla 10 sonuç döndürür. Sorgu planını istersen planda index'in kullanıldığı ve filtreye takılan satır sayısı görünür.
5. Modem kılavuzunun hazır gelen örnek PDF'sini "3.2" sürümü olarak yüklersin. İşin ilerlediğini görürsün; bitince kaynak 3.2 sürümünde ve yeni parça sayısıyla hazırdır, yeni parçalar aramada çıkar, eskiler çıkmaz.
6. İş sürerken aynı kaynağa bir sürüm daha yüklersin: "bu kaynak hâlâ işleniyor" hatası.
7. Ollama'ya ulaşılamayan bir backend'le sürüm yüklersin: iş başarısız olur, kaynak eski sürümüyle hazır kalır, eski parçalar aranmaya devam eder.
8. `.docx` dosyası "desteklenmeyen tür", 25 MB'lık PDF "dosya çok büyük" hatası verir.
9. Backend'i başka bir modelle (`embeddinggemma`) açarsın: açılışta uyarı yazar; anlam ve hybrid arama "model uyuşmuyor" hatası verir; kelime araması çalışır.
10. Kıyı Net'te "Şifremi unuttum" aranır, sadece Kıyı Net'in parçaları döner.
11. Arama kayıtlarında son aramaların sonuç sayıları ve ilk sonucun başlığı görünür; sonuçsuz arama 0 ile görünür.
12. Backend'in testleri gerçek bir veritabanıyla (Docker'da geçici) ama Ollama olmadan geçer.
13. `ollama.md`'deki komutları sırayla çalıştırınca anlatılan sonuçları alırsın.

## Yapılmayacaklar
- Arayüz / ekran (sonraki task).
- Canlı ilerleme (WebSocket); ilerleme iş durumunu tekrar tekrar sorgulayarak izlenir.
- Web sitesi tarama ve destek talebi aktarımı; bu kaynak türleri sadece başlangıç verisinde var, onlara dosya yüklenemez.
- Kaynak silme, çalışma alanı oluşturma ve silme.
- Giriş / yetki.
- Sonuçlardan yapay zekâ ile cevap üretme.
- Model değiştirip bütün parçaları yeniden vektöre çevirme (sadece uyuşmazlık koruması var).
- Hata veren aramalar (ör. model uyuşmazlığı) arama kayıtlarına yazılmaz; sadece sonuç dönen aramalar (sonuçsuzlar dahil) yazılır.

## Benim verdiğim kararlar
- **Index'i kullanmaya zorlama:** Demo verisi küçük (1.410 parça, Dalga Fiber'in 42'si). Bu boyutta PostgreSQL vektör index'ini hiç kullanmadan bütün satırlara bakmayı seçer; o zaman lab'daki "önce index, sonra filtre → kısa liste" olayı hiç görünmez. Anlam aramasında, sadece o sorgu için veritabanına index'i kullanmasını söyleyen bir ayar açıyorum. Ayarlardan kapatılabilir; kapatınca PostgreSQL'in kendi seçimini görürsün.
- **"Kısa liste" gerçek veriyle:** Lab'daki 37 şirketin metinleri kasıtlı olarak benzer konulardadır; 8'i Dalga Fiber gibi internet/telekom şirketidir ve taşınma, modem hata kodu, şifre konularını kendi adlarıyla anlatır. Bu yüzden "en yakın 40 aday" içinde Dalga Fiber'in birkaç parçası kalır. Bunu test sahte vektörlerle, son kontrol gerçek veriyle ölçer.
- **Parça sayısı:** Lab'daki Dalga Fiber listesinde 42 parça var (TASK 44 diyor; fark, lab'ın yükleme sonrası eklediği bir parçadan geliyor). 42'sini yüklüyorum.
- **Parça boyu:** Yaklaşık 400 token için hedef 1.600 karakter, üst sınır 2.000 karakter. Ardışık parçalar yaklaşık 200 karakterlik bütün cümlelerle örtüşür. Token sayısı "karakter / 4" ile yaklaşık hesaplanır. Parçalar sayfa (PDF) ya da başlık (Markdown) sınırını geçmez.
- **PDF başlığı:** Parçanın başlığı sayfa numarası ve sayfanın ilk satırıdır (`s. 31 · Modemi yeni adreste kurma`). Boş sayfalar atlanır.
- **CSV:** İlk satır sütun adlarıdır. Virgül, noktalı virgül (Türkçe Excel) ya da sekme ile ayrılmış dosyalar otomatik tanınır.
- **Metin dosyalarının kodlaması:** TXT/MD/CSV dosyaları UTF-8 olmalı. Eski Türkçe kodlamayla kaydedilmiş dosya "kodlama desteklenmiyor" hatasıyla reddedilir (Türkçe harfler bozulmasın diye).
- **Kaynak kuralları (proje kuralından):** Bir çalışma alanında en fazla 10 kaynak olur; kaynak adı 2-80 karakterdir ve aynı alanda büyük/küçük harf fark etmeden bir kez kullanılabilir. Başarısız kalmış bir kaynak da sayılır; onu düzeltmek için aynı kaynağa yeni sürüm yüklersin.
- **Yeni sürüm aynı türde olmalı:** PDF kaynağa PDF, CSV kaynağa CSV yüklenir. Web sitesi ve talep geçmişi kaynaklarına dosya yüklenemez.
- **Sürüm etiketi:** Yeni sürüm yüklerken etiket vermezsen kaynağın sürümü boş görünür. Yeni kaynak oluştururken sürüm alınmaz.
- **Arama girdileri:** Modu ve filtre stratejisini büyük ya da küçük harfle yazabilirsin (`keyword` ya da `KEYWORD`); yanıtta hep büyük harfle döner. Arama metni boşluklar kırpıldıktan sonra 1-500 karakter, kanal en fazla 60 karakter.
- **Sıra numaraları:** Puanı eşit sonuçlar aynı sırayı paylaşmaz; eşitlik parça numarasıyla bozulur (lab'da eşit puanlılar aynı sırayı alabiliyordu).
- **Vurgulama:** Kelime aramasının bulduğu parçalarda eşleşen kelimelerin başlık ya da içerikteki başlangıç ve bitiş konumları döner; ekran bunları ileride işaretler.
- **Başlangıç verisi arka planda:** Backend açılınca hemen kullanılabilir olur; başlangıç verisi arka planda yüklenir (Mac'te birkaç dakika). Bu sırada yapılan aramalar eksik veri görebilir. Ollama kapalıysa eksik kalan kısım bir sonraki açılışta tamamlanır; daha önce yüklenen veri tekrar yüklenmez.
- **Yarıda kalan iş:** Backend bir iş sürerken kapanırsa, açılışta o iş "yarıda kaldı" diye başarısız sayılır ve kaynak eski haline döner; kaynak kilitli kalmaz.
- **Kimlikler:** Çalışma alanları ve Dalga Fiber'in kaynakları lab'daki numaraları taşır (42; 301-304). Parça numaraları (id) lab'dakinden farklıdır. Senin oluşturduğun kaynaklar 10001'den başlar.
- **Kısa aşamalar:** Dosya okuma ve parçalama milisaniyeler sürer; iş durumunu sorgularken bu aşamaları göremeyebilirsin, log'da hepsi görünür. Yapay bekleme eklemiyorum.
- **Örnek dosyalar:** Rehberde kullanmak için hazır bir modem kılavuzu PDF'i (3.2), bir Markdown ve bir CSV dosyası projeye eklenir.
- **Rehberi kim günceller:** `ollama.md`'yi sistemi Docker'da gerçekten çalıştıran rol (devops) günceller; her komutun çıktısını gerçek çalıştırmadan alır.
- **Portlar:** Bu task'ta ekran olmadığı için backend 8080'den, veritabanı (sadece kendi bilgisayarından) 5432'den erişilebilir.

## Çalışacak roller
planner, backend-coder, tester, devops, qa.
Atlanan: frontend-coder (bu task'ta ekran yok).

## Kararını bekleyen sorular
1. Yeni iki kütüphane ekleyelim mi: PDF dosyalarından metin okumak için Apache PDFBox ve testlerde arka plandaki işin bitmesini beklemek için Awaitility?
   Neden önemli: PDF okumak için hazır bir kütüphane şart (PDF formatını elle okumak güvenilir olmaz); Awaitility sadece testlerde kullanılır, uygulamaya girmez.
   Seçenekler: A) İkisini de ekle. B) Sadece PDFBox; testler bekleme işini kendi yöntemiyle yapsın. C) Hiçbiri; PDF desteği bu task'tan çıksın.
   Önerim: A. PDFBox Apache'nin uzun süredir bakılan, yaygın kütüphanesi; Awaitility testleri sabit beklemeler olmadan, okunur biçimde yazdırır. B'de testler biraz daha dolambaçlı olur ama çalışır.

2. Veritabanı tablo ve kolon adları lab'dakiyle birebir mi olsun, yoksa projenin isim kuralına göre mi?
   Neden önemli: `psql` ile tabloları incelerken ve rehberdeki komutlarda bu adları göreceksin; lab'daki SQL'i yan yana koyunca ya birebir aynı ya da adları farklı görünecek.
   Seçenekler: A) Lab'daki gibi: `kb_chunks`, `kb_sources`, `ingest_jobs`, `tenant_id`. B) Proje kuralına göre: `chunks`, `sources`, `ingestion_jobs`, `workspace_id` (API'deki adlarla aynı).
   Önerim: B. API'de ve kodda her yerde "workspace" kullanılıyor; veritabanında da aynı ad olunca karışıklık olmaz. Sorgular, index'ler ve davranış lab'dakiyle aynı kalır, sadece adlar farklıdır.

## Riskler
- "Kısa liste" senaryosu gerçek modelin vektörlerine bağlı. Beklenenden farklı çıkarsa (anlam araması yine 10 sonuç döndürürse) son kontrol bunu sayılarla raporlar; düzeltme, diğer şirketlere daha fazla taşınma metni eklemek olur.
- Başlangıç verisinin ilk yüklenmesi Mac'inde birkaç dakika sürer; Ollama o sırada meşgul olur.
- Kullanılacak bazı sürümler (veritabanı imajı, PDF kütüphanesi, Java 26 imajı) kurulumda doğrulanır; tam o sürüm yoksa en yakın yama sürümü kullanılır ve raporda yazılır.

## Kullanıcının cevapları
1. A) İkisini de ekle (Apache PDFBox + Awaitility).
2. B) Proje kuralına göre: `chunks`, `sources`, `ingestion_jobs`, `workspace_id`.

ONAY: VERİLDİ
