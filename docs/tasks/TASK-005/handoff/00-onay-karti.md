TASK: TASK-005
# Onay kartı: Klonla-çalıştır kurulumu, pgAdmin ve kalan düzeltmeler

## Ne yapılacak
- **Tek komutla tam sistem:** Projeyi indiren kişi hiçbir dosya oluşturmadan ve düzenlemeden tek komutla veritabanını, backend'i, siteyi ve pgAdmin'i birlikte açar. Bütün ayarlar (veritabanı adı, kullanıcı, şifre, model, başlangıç verisi, pgAdmin girişi) bu dosyanın içinde yazılı olur. Ayar şablonu dosyası (`deployment/.env.example`) silinir.
- **Local geliştirme için ayrı dosya:** İkinci bir dosya sadece veritabanını ve pgAdmin'i açar. Backend ve site bilgisayarda doğrudan çalıştırılır ve hiçbir ayar ya da şifre verilmeden bu veritabanına bağlanır. Veritabanı başlangıç verisiyle dolar.
- **pgAdmin:** İki kurulumda da pgAdmin açılır. Giriş yapınca projenin veritabanı sol tarafta hazır görünür, sunucu eklemen gerekmez. Tabloları ve vektör kolonlarını görür, sorgu çalıştırırsın.
- **Çalıştırma rehberi:** Projenin ana klasöründe yeni bir `README.md` olur. Gereksinimler, Ollama ve `bge-m3` kurulumu, iki kurulumun başlatılması, durdurulması ve sıfırlanması, bütün adresler, portlar ve giriş bilgileri Türkçe olarak bu tek dosyada yazar.
- **Türkçe büyük harfli kaynak adı:** "Yardım merkezi" varken "yardım MERKEZİ" ya da "YARDIM MERKEZI" adıyla kaynak eklenemez. Bugünkü gibi Türkçe "ad kullanımda" mesajı çıkar. Sunucu kaydına hata satırı ve kaynak adı düşmez. Bugün "YARDIM MERKEZI" (noktasız I) yanlışlıkla kabul ediliyor; bu da düzelir.
- **Uçtan uca testlerin güvenilirliği:** "Yeni sürüm yükle, sayfayı yenile" testi yüklemenin ne kadar sürdüğüne bağlı olmadan geçer. Testler ekrana ölçüm yazısı basmaz. Bütün uçtan uca testler arka arkaya 10 kez koşulur.

## Bittiğini nasıl anlayacaksın
1. **Temiz kopya:** Projenin `.env` dosyası olmayan temiz bir kopyasında, eski Docker verisi silinmişken `docker compose -f deployment/docker-compose.yml up --build -d` çalıştırırsın. Dört servis (`postgres`, `backend`, `frontend`, `pgadmin`) "healthy" olur. Hiçbir dosya oluşturmamış ya da düzenlememişsindir.
2. **İlk arama:** `http://localhost:3000`'de "Şifremi unuttum, online işlemlere giremiyorum" diye ararsın, sonuçlar gelir.
3. **pgAdmin:** `http://localhost:5050`'ye rehberdeki e-posta ve şifreyle girersin. Sol tarafta "embedding-pgvector" sunucusu hazırdır. İlk açışta veritabanı şifresini (rehberde yazar) girersin. `workspaces`, `sources`, `chunks`, `ingestion_jobs`, `search_events` tablolarını görürsün. `chunks` tablosunda `embedding` kolonunu ve 1.410 satırı görürsün.
4. **Ollama kapalı:** Ollama'yı kapatıp tam sistemi başlatırsın. Servisler yine "healthy" olur. Kelime araması çalışır. Anlam aramasında "Embedding servisine (Ollama) ulaşılamıyor…" mesajı çıkar (bugünkü mesaj).
5. **Local geliştirme:** Tam sistemi durdurur, local geliştirme dosyasını başlatırsın. Veritabanı ve pgAdmin "healthy" olur. `cd backend && ./mvnw spring-boot:run` ve `cd frontend && npm run dev` komutlarını hiçbir ayar vermeden çalıştırırsın. `http://localhost:5173`'te arama yaparsın ve modem kılavuzuna yeni sürüm yüklersin, ikisi de çalışır.
6. **Türkçe ad:** Dalga Fiber'in Bilgi kaynakları sayfasında "yardım MERKEZİ" adıyla bir `.md` yüklemeyi denersin. Yükleme yapılmaz, ad alanının altında "Bu ad çalışma alanında başka bir kaynakta kullanılıyor." yazar. "YARDIM MERKEZI" ile de aynısı olur. Sunucu kaydında hata satırı yoktur.
7. **Rehber:** `README.md`'deki adımları sırayla uygulayan biri sistemi ayağa kaldırabilir.
8. **Testler:**
   - Derleme, kod kontrolü, site testleri ve backend testleri hatasız geçer.
   - Türkçe büyük harfli ad için otomatik test vardır. Test hem "ad kullanımda" sonucunu hem kayıtta hata satırı olmadığını kontrol eder.
   - Uçtan uca testler çalışan sisteme karşı 10 kez normal koşulda, 10 kez de bilgisayar yük altındayken koşulur ve hepsi geçer.

## Yapılmayacaklar
- Ollama'yı ya da modeli Docker'a almak, modeli otomatik indirmek.
- Gerçek sunucu kurulumu, gizli bilgi yönetimi, HTTPS, giriş sistemi, otomatik derleme hattı (CI/CD).
- Dosya seçme düğmesinin tarayıcı diline göre İngilizce görünmesi.
- Mutation testi açıkları.
- Sistem konsolu, senaryolar, konu anlatım sayfaları ve diğer yeni ekranlar.
- Yeni adres, yeni alan, yeni hata kodu. Sitenin görünüşü ve davranışı değişmez.
- Tam sistemle local geliştirmenin aynı anda çalışması (aynı portları kullanırlar, rehberde yazar).

## Benim verdiğim kararlar
- **Noktalı ve noktasız i aynı harf sayılır:** "YARDIM MERKEZI" adının "Yardım merkezi" ile aynı sayılması için I, ı, İ ve i harflerinin dördü de kaynak adlarında aynı harf sayılır. Bunun sonucu: "Kıyı notları" ile "Kiyi notları" da aynı ad sayılır. Diğer Türkçe harfler kendi büyük/küçük hâliyle eşleşir ama noktasız/çengelsiz hâliyle eşleşmez: "Şifre SSS" ile "Sifre SSS" iki farklı addır.
- **Ad kontrolü tek yerde karar verir:** Adın karşılaştırılacak hâli kaynak kaydedilirken bir kez hesaplanır ve veritabanında saklanır. Uygulama ile veritabanı bu aynı değere bakar, böylece aynı ad için hiçbir zaman farklı karar vermezler. Var olan veritabanlarındaki kaynakların bu değeri ilk açılışta doldurulur.
- **İki kurulumun verisi ayrıdır:** Tam sistemin veritabanı ile local geliştirmenin veritabanı ayrı tutulur. Local'de yüklediğin bir dosya tam sistemde görünmez; ikisi de kendi başlangıç verisiyle başlar. Böylece uçtan uca testlerin değiştirdiği veri local çalışmanı bozmaz.
- **Portlar:** Site `3000` (local'de `5173`), backend `8080`, Swagger `8080/swagger-ui.html`, pgAdmin `5050`, veritabanı `5432`. İki kurulum aynı portları kullanır. Birini başlatmadan önce diğeri durdurulur; rehberde komutuyla yazar. pgAdmin ve veritabanı sadece bu bilgisayardan açılır.
- **Giriş bilgileri:**
  - Veritabanı: ad `embedding_pgvector`, kullanıcı `embedding`, şifre `embedding-local`.
  - pgAdmin: e-posta `admin@example.com`, şifre `admin-local`.
  - Bu şifrelerin sadece bu bilgisayarda yerel kullanım için olduğu hem dosyalarda hem rehberde yazar.
- **pgAdmin veritabanı şifresini kendisi bilmez:** Sunucu hazır gelir. İlk bağlanışta pgAdmin veritabanı şifresini bir kez sorar; "Şifreyi kaydet" işaretlenirse bir daha sormaz. Şifreyi önceden dosyaya gömmek dosya izinleri yüzünden kırılgan olduğu için bu yol seçildi.
- **Rehberin yeri:** Rehber ana klasördeki `README.md`'dedir; GitHub'da proje açılınca ilk görünen dosya budur. Bugünkü `ollama.md` kalır ama kurulum ve başlatma adımları oradan çıkarılıp `README.md`'ye bağlanır. `ollama.md` sadece "Ollama ve backend ile deneyler" kısmını (doğrudan Ollama istekleri, arama denemeleri, veritabanına bakma) tutar. Böylece aynı adımlar iki yerde yazmaz. Bu dosyayı devops rolü yazar. Ana `CLAUDE.md`'deki rol tablosuna bu satırı lead senin onayınla ekler.
- **"Yeni sürüm yükle, yenile" testi:** Test, yükleme gerçekte o kadar hızlı bitse bile sayfa yenilenene kadar tarayıcıya işi "sürüyor" gösterir. Test yenilemeden sonra ilerlemeyi gördükten sonra gerçek sonucu serbest bırakır. Bitiş bildirimi ve "Hazır v3.2" gerçek sunucudan gelir. Yükleme yavaşsa testin araya girmesine gerek kalmaz, her şey gerçek gelir.

## Çalışacak roller
planner, backend-coder, tester, devops, qa.
Atlanan: frontend-coder. Sitenin kodunda değişiklik yok; dev server zaten istekleri local backend'e iletiyor. Değişen tek frontend dosyası uçtan uca test, onu tester yazar.

## Kararını bekleyen sorular
1. Tam sistem dosyasının adı bugünkü gibi `deployment/docker-compose.yml` olarak kalsın mı, yoksa senin yazdığın `deployment/docker-compose.yaml` adına mı geçelim?
   Neden önemli: Bu ad rehberlerde, rol tanımlarında ve proje kurallarında 20'den fazla yerde geçiyor; değişirse hepsi güncellenmeli.
   Seçenekler:
   A) `deployment/docker-compose.yml` kalsın, local geliştirme dosyası `deployment/docker-compose.dev.yml` olsun.
   B) İkisi de `.yaml` olsun: `deployment/docker-compose.yaml` ve `deployment/docker-compose.dev.yaml`. Ana `CLAUDE.md`, rol tanımları ve kurallar dosyalarındaki eski adı lead senin onayınla düzeltir.
   Önerim: A, çünkü iki uzantı da aynı şekilde çalışır. Bugünkü adı korumak, hiçbir rolün düzenleyemediği dosyalarda iz bırakmaz.
   **Kullanıcı kararı (2026-10-25): A.** `deployment/docker-compose.yml` kalır, local geliştirme dosyası `deployment/docker-compose.dev.yml`.

## Riskler
- **Eski kurulumun verisi:** Sistemin adı değiştiği için eski Docker verisi yeni kurulumda kullanılmaz, başlangıç verisi yeniden yüklenir. Eski veriyi silme komutu rehberde yazar.
- **pgAdmin sürümü:** pgAdmin sabit bir sürümle kurulur. O sürümün sunucu tanımını her açılışta yenileme ayarını desteklediği devops tarafından denenir; desteklemiyorsa tanım değişince pgAdmin verisinin sıfırlanması gerektiği rehbere yazılır.
- **Testlerin süresi:** Uçtan uca takımın 20 kez koşulması (10 normal, 10 yük altında) gerçek Ollama ile yarım saati bulabilir.

ONAY: VERİLDİ
