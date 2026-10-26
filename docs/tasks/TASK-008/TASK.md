# TASK-008: Uçtan uca doğrulama: backend, frontend ve PostgreSQL akışlarının baştan sona testi

## Ne isteniyor
Projenin bugünkü hâli (TASK-001…007) baştan sona test edilir: backend, frontend ve PostgreSQL (pgvector) birlikte, gerçek çalışan sistem üzerinde. Amaç hatalı bir durum olup olmadığını bulmak; bulunan hatalar düzeltilir ve tekrar etmemesi için testle korunur. Yeni özellik eklenmez.

## Kurallar
1. Kapsam, kullanıcının yaptığı her akıştır: sistemi açma, çalışma alanını görme, belge yükleme (PDF dahil), içe alma ilerlemesi (sayfa yenilense de devam etmesi), parçaların veritabanına embedding'leriyle yazılması, belgenin yeni sürümünü yükleme, kelime / anlam / hybrid arama, arama kayıtları, istatistikler ve "Sık aranan" listesi, belge ve parça detay ekranları.
2. Her akış üç katmanda birden kontrol edilir: ekranda ne görünüyor, REST cevabı `docs/api/openapi.yaml` sözleşmesine uyuyor mu, veritabanında doğru kayıt oluştu mu (tablolar, `vector_dims`, embedding modeli, index kullanımı).
3. Hata durumları da test edilir: geçersiz istek (boş metin, sınır dışı değerler, olmayan id), desteklenmeyen ya da boş dosya, limit aşımı, Ollama kapalıyken davranış. Her hata RFC 9457 Problem Details ve sabit `errorCode` döner; ekranda doğru Türkçe mesaj görünür.
4. Eşzamanlılık ve tutarlılık: aynı anda birden fazla yükleme, yükleme sürerken arama, içe alma yarıda kalırsa (backend yeniden başlatma) iş ve parçaların durumu.
5. Kalıcılık: sistem `down` / `up` sonrası veri kalır; `down -v` sonrası temiz başlar; Flyway migration'ları boş veritabanında sorunsuz çalışır.
6. README ve `ollama.md`'deki adımlar yazıldığı gibi çalışır (tam sistem ve local geliştirme). Örnek belgeyle README'deki soru tablosu beklenen bölümleri döndürür.
7. Mevcut tüm testler (backend `./mvnw verify`, frontend build + lint + unit, Playwright e2e) çalıştırılır ve geçer.
8. Bulunan her hata için: belirti, nasıl tekrarlanır, sorumlu katman ve önem derecesi raporlanır; hata düzeltilir ve onu yakalayan bir test eklenir. Kod sözleşmeden sapıyorsa kod sözleşmeye uydurulur; sözleşmenin kendisi yanlışsa planner düzeltir.

## Örnek senaryolar
- Kullanıcı sistemi `docker compose ... up --build -d` ile açar, 4 servis `healthy` olur, http://localhost:3000'de "Henüz belge yüklenmedi" görür.
- Kullanıcı örnek PDF'i yükler, ilerlemeyi izlerken sayfayı yeniler, ilerleme kaldığı yerden görünür; bitince veritabanında ~24 parça, hepsinde 1024 boyutlu embedding vardır.
- Kullanıcı "Yıllık izin kaç gün?" diye anlam araması yapar, "Yıllık izin ve diğer izinler" bölümü ilk sonuçlarda gelir; arama kaydı `search_events`'e yazılır ve "Sık aranan"da görünür.
- Ollama kapalıyken kullanıcı dosya yükler; ekranda "Embedding servisine (Ollama) ulaşılamıyor…" mesajı görünür, kelime araması çalışmaya devam eder.
- Kullanıcı boş bir dosya ya da desteklenmeyen bir uzantı yükler; anlamlı bir Türkçe hata görür, veritabanında yarım kayıt kalmaz.

## Kabul kriterleri
- Kural 1'deki her akış için hangi katmanda nasıl test edildiği ve sonucu raporda yazılıdır.
- Bulunan hatalar düzeltilmiş ve her biri için en az bir test eklenmiştir; düzeltilmeyen varsa gerekçesiyle listelenmiştir.
- `cd backend && ./mvnw verify`, `cd frontend && npm run build`, `npm run lint`, frontend unit testleri ve Playwright e2e testleri geçer; komutlar ve test sayıları raporda yazılıdır.
- Tam sistem temiz bir veritabanıyla (`down -v` sonrası) açılır ve smoke kontrolü geçer.

## Kapsam dışı
- Yeni özellik, yeni ekran ya da yeni endpoint.
- WebSocket (henüz yok; ilerleme polling ile izlenir).
- Performans optimizasyonu ve yük testi (sadece hata olarak görülen ciddi yavaşlık raporlanır).
- Kökteki anlatım HTML sayfaları.

## Etkilenen katmanlar
- [x] Backend
- [x] Frontend
- [ ] API sözleşmesi
- [x] Veritabanı (migration, pgvector)
- [ ] WebSocket
- [x] Docker
