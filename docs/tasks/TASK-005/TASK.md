# TASK-005: Klonla-çalıştır kurulumu, pgAdmin ve kalan düzeltmeler

## Ne isteniyor
Projeyi git'ten klonlayan biri, ek dosya oluşturmadan ve ayar yapmadan sistemi tek komutla ayağa kaldırabilir. Bunun için iki Docker Compose dosyası olur:
- **Tam sistem:** PostgreSQL (pgvector), backend, frontend ve pgAdmin birlikte çalışır.
- **Local geliştirme:** Sadece PostgreSQL ve pgAdmin çalışır. Backend ve frontend bilgisayarda doğrudan çalıştırılıp incelenir.

Embedding modeli Docker'a girmez. Kullanıcı Ollama'yı ve modeli kendi bilgisayarına kurup çalıştırır.

Ayrıca TASK-002 ve TASK-004 QA raporlarında "Optional" olarak kalan iki hata ve bir kural ihlali düzeltilir. Kullanıcının gördüğü uygulama davranışı değişmez. TASK-001…TASK-004'te yapılan her şey aynen kalır.

## Kurallar

### Tam sistem (klonla ve çalıştır)
1. Tam sistem `deployment/` altındaki compose dosyasıyla tek komutla başlar. Projeyi klonlayan kişi `.env` dosyası oluşturmaz, hiçbir dosyayı kopyalamaz ya da düzenlemez.
2. Ayarlar (veritabanı adı, kullanıcı, şifre, embedding modeli, vektör boyutu, başlangıç verisi açık/kapalı, pgAdmin giriş bilgileri) compose dosyasının içinde düz değer olarak yazılıdır. Projede `.env` ya da `.env.example` dosyası kalmaz; compose hiçbir ortam değişkeninin dışarıdan verilmesini zorunlu tutmaz.
3. Tam sistemde dört servis çalışır: `postgres`, `backend`, `frontend`, `pgadmin`. Hepsinin sağlık kontrolü vardır ve başladıktan sonra hepsi `healthy` olur.
4. Site, backend ve pgAdmin bilgisayardan sabit portlarla açılır. Portlar çalıştırma rehberinde yazılıdır.

### Local geliştirme
5. Ayrı bir compose dosyası sadece `postgres` ve `pgadmin` servislerini başlatır. Bu dosya da `.env` gerektirmez ve ayarları kendi içinde yazılıdır.
6. Bu veritabanı açıkken backend `./mvnw spring-boot:run` ile, frontend `npm run dev` ile bilgisayarda başlatılır. Ek ortam değişkeni vermeden ya da şifre yazmadan veritabanına bağlanırlar. Frontend dev server isteklerini local backend'e iletir.
7. Local geliştirme veritabanı da başlangıç verisiyle dolar. Backend ilk açılışta migration'ları ve başlangıç verisini yükler; arama, kaynaklar ve yükleme tam sistemdeki gibi çalışır.
8. Tam sistem ile local geliştirme aynı anda çalıştırılmak zorunda değildir. Aynı portu kullanıyorlarsa bu durum ve önce hangisinin durdurulacağı rehberde yazılır.

### pgAdmin
9. pgAdmin hem tam sistemde hem local geliştirmede vardır.
10. pgAdmin açılınca projenin veritabanı bağlantısı hazır gelir. Kullanıcı sunucu eklemez; sadece pgAdmin'e giriş yapar. Gerekirse veritabanı şifresini girer ve bu şifre de rehberde yazılıdır.
11. pgAdmin'den `workspaces`, `sources`, `chunks`, `ingestion_jobs` ve `search_events` tabloları görülür ve sorgu çalıştırılır. pgvector'ün `vector` kolonları da görülür.

### Embedding modeli (Ollama)
12. Ollama Docker'da çalışmaz. Kullanıcı Ollama'yı kendi bilgisayarına kurar, modeli (`bge-m3`) indirir ve çalıştırır. Hem tam sistemdeki backend container'ı hem local'de çalışan backend bu Ollama'ya bağlanır.
13. Ollama kapalıyken de sistem başlar ve servisler `healthy` olur. Kelime araması çalışır; anlam araması ve yükleme Türkçe "embedding servisine ulaşılamıyor" mesajı verir (bugünkü davranış).

### Çalıştırma rehberi
14. Projeyi klonlayan kişi için adımlar Türkçe ve tek bir yerde yazılıdır:
    - gereksinimler (Docker, Ollama; local geliştirme için Java ve Node sürümleri),
    - Ollama kurulumu ve `bge-m3` modelinin indirilmesi,
    - tam sistemi başlatma ve durdurma, veriyi sıfırlama,
    - local geliştirme: veritabanını başlatma, backend ve frontend'i çalıştırma,
    - adresler, portlar ve giriş bilgileri (site, backend, Swagger, pgAdmin, veritabanı).
15. Compose dosyalarındaki şifreler sadece local kullanım içindir. Bu, rehberde ve compose dosyasında açıkça yazılır.

### Türkçe büyük harfli kaynak adları
16. Aynı çalışma alanında sadece büyük/küçük harf farkı olan iki kaynak adı yine "ad kullanımda" sayılır. Türkçe harfler (İ/i, I/ı, Ş/ş, Ğ/ğ, Ü/ü, Ö/ö, Ç/ç) de buna dahildir.
17. Kullanıcı mevcut bir adın Türkçe büyük harfli halini girerse (ör. "Yardım merkezi" varken "yardım MERKEZİ"), sonuç bugünkü gibi `SOURCE_NAME_TAKEN` olur ve Türkçe "ad kullanımda" mesajı çıkar.
18. Bu durumda backend loguna `ERROR` seviyesinde satır düşmez. Loglarda kaynak adı ya da başka kullanıcı verisi görünmez.
19. Ad kontrolünün sonucu, kontrolün nerede yapıldığına bağlı değildir. Uygulamadaki kontrol ile veritabanındaki tekillik kuralı aynı adlar için aynı kararı verir.

### Uçtan uca testlerin güvenilirliği
20. "Yeni sürüm yükle, sayfayı yenile, ilerleme kaldığı yerden görünsün" uçtan uca testi, içe alma işinin ne kadar sürdüğünden bağımsız olarak geçer.
21. Uçtan uca test takımı, sistem açıkken arka arkaya 10 kez çalıştırıldığında her seferinde geçer. Kaçırılan bir zamanlama "retry" ile gizlenmez.
22. Uçtan uca testler `console.log` ile çıktı yazmaz (`frontend-conventions`).

## Örnek senaryolar
- Yeni bir geliştirici projeyi klonlar, Ollama'yı kurar, `ollama pull bge-m3` çalıştırır ve tam sistemi tek komutla başlatır. Dört servis `healthy` olur. Siteyi açıp "Şifremi unuttum, online işlemlere giremiyorum" diye arar, sonuçlar gelir. Hiçbir dosya oluşturmamış ya da düzenlememiştir.
- Aynı kişi pgAdmin'i açar, rehberdeki bilgilerle giriş yapar. Projenin veritabanı sol tarafta hazırdır. `chunks` tablosunu açar, `embedding` kolonunu ve satır sayısını görür.
- Geliştirici tam sistemi durdurur, local geliştirme compose'unu başlatır (postgres + pgAdmin). Backend'i `./mvnw spring-boot:run`, frontend'i `npm run dev` ile açar. Hiçbir ortam değişkeni vermez. Dev server'daki sitede arama ve yeni sürüm yükleme çalışır.
- Ollama kapalıyken tam sistem başlatılır. Servisler `healthy` olur, kelime araması çalışır, anlam aramasında Türkçe "embedding servisine ulaşılamıyor" mesajı çıkar.
- Dalga Fiber'de "Yardım merkezi" kaynağı var. Kullanıcı "yardım MERKEZİ" adıyla `.md` yükler. Yükleme yapılmaz, Türkçe "ad kullanımda" mesajı çıkar, backend logunda `ERROR` satırı yoktur. "YARDIM MERKEZI" (noktasız I) ile deneme de aynı sonucu verir.
- Uçtan uca takım, Ollama hızlıyken ve bilgisayar yük altındayken 10'ar kez koşulur; hepsi geçer.

## Kabul kriterleri
- Komutlar hatasız geçiyor:
  - `cd backend && ./mvnw verify`
  - `cd frontend && npm run build`
  - `cd frontend && npm run lint`
  - frontend testleri
- Projenin temiz bir kopyasında (`.env` yok, eski Docker volume'ları silinmiş) tam sistem tek komutla başlıyor ve `postgres`, `backend`, `frontend`, `pgadmin` servislerinin hepsi `healthy`. Bu deneme qa raporunda yazıyor.
- Local geliştirme compose'u ile `postgres` ve `pgadmin` `healthy`. Backend ve frontend ortam değişkeni verilmeden bilgisayarda çalışıyor, sitede arama ve yükleme deneniyor. Sonuç qa raporunda yazıyor.
- pgAdmin'de veritabanı bağlantısı elle eklenmeden görünüyor. Tablolar ve bir sorgu sonucu qa raporunda yazıyor.
- Projede `.env` / `.env.example` dosyası yok; compose dosyaları ortam değişkeni istemiyor.
- Çalıştırma rehberi var ve qa rehberdeki adımları sırayla uygulayarak sistemi ayağa kaldırabiliyor.
- Türkçe büyük harfli ad senaryosu için en az bir otomatik test var; test hem `SOURCE_NAME_TAKEN` sonucunu hem logda `ERROR` olmadığını doğruluyor.
- Uçtan uca takım (TASK-003 ve TASK-004 testleri dahil) çalışan sisteme karşı arka arkaya 10 kez koşuldu; sonuçlar qa raporunda yazıyor.
- `frontend/e2e/` altında `console.log` yok.

## Kapsam dışı
- Ollama'yı ya da embedding modelini Docker'a almak, modeli otomatik indirmek.
- Production kurulumu, gizli bilgi yönetimi (secret manager), HTTPS, kimlik doğrulama.
- CI/CD.
- Dosya seçici düğmesinin tarayıcı diline göre İngilizce görünmesi (TASK-004 QA "Optional").
- Mutation testi açıkları (PIT'te hayatta kalan mutant'lar, Stryker kurulumu).
- `lab.html`'deki Sistem konsolu, senaryolar, konu anlatım sayfaları ve diğer yeni özellikler.
- Yeni endpoint, yeni alan ya da sözleşme değişikliği. Hata kodları aynı kalır.

## Etkilenen katmanlar
- [x] Backend (local'de ortam değişkeni olmadan çalışma, ad karşılaştırması, log)
- [x] Frontend (dev server ayarı gerekirse, uçtan uca testler)
- [ ] API sözleşmesi
- [ ] Veritabanı (migration, pgvector) — gerekirse planner belirler (ör. ad karşılaştırma kuralı)
- [ ] WebSocket
- [x] Docker (iki compose dosyası, pgAdmin, `.env` kaldırma)
