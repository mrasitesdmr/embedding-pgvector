TASK: TASK-005
# Plan

## Özet
**Mevcut durum (TASK-004 sonrası):**
- `deployment/docker-compose.yml` üç servis (`postgres`, `backend`, `frontend`) açıyor. `POSTGRES_PASSWORD` `${POSTGRES_PASSWORD:?…}` ile `deployment/.env`'den zorunlu; repoda `deployment/.env.example` var; bu makinede ayrıca git'e girmeyen (kök `.gitignore` `.env` satırı) yerel bir `deployment/.env` duruyor. Klonlayan kişide `.env` olmadığı için `cp` + şifre yazmadan sistemi açamıyor. pgAdmin yok. Local geliştirme için ayrı compose yok (`up -d postgres` + `POSTGRES_PASSWORD=… ./mvnw spring-boot:run`).
- `application.properties`: `spring.datasource.password=${POSTGRES_PASSWORD:}` (varsayılan boş), yani local backend şifresiz bağlanamıyor.
- Kurulum anlatımı `ollama.md` 5.1'de ve `.env` adımlarıyla yazılı; kökte `README.md` yok.
- Kaynak adı tekilliği iki yerde, iki farklı fonksiyonla karar veriyor: `Workspace.addSource` → `toLowerCase(Locale.ROOT)`; DB → `uk_sources_workspace_id_name (workspace_id, lower(name))`. "yardım MERKEZİ": Java'da `merkezi̇` (i + U+0307) olur, kontrolü geçer, DB'de `lower('İ') = 'i'` ile index'e takılır → 409 doğru ama Hibernate `org.hibernate.orm.jdbc.error` iki ERROR satırı yazar ve kaynak adı logda görünür (TASK-002 QA). "YARDIM MERKEZI": Java `yardim merkezi`, DB `yardim merkezi`, mevcut `yardım merkezi` ile ikisi de eşleşmez → **bugün yinelenen kaynak olarak kabul ediliyor** (TASK Örnek senaryo 5 bunu reddetmeyi istiyor).
- `frontend/e2e/upload.spec.ts` E1, yenileme sonrası ilerlemeyi işin gerçek süresi (0,4-1,4 sn) içinde görmeye çalışıyor (TASK-004 QA, Optional); iki `console.log` satırı var.

**Yeni durum:**
1. **Backend (backend-coder):**
   - Kaynağın karşılaştırma anahtarı (`Source.nameKey`) Java'da bir kez hesaplanır ve `sources.name_key` kolonunda saklanır; tekillik index'i `(workspace_id, name_key)` olur (D1, D2). Migration `V8`.
   - `Workspace.addSource` aynı anahtarı karşılaştırır; Java ve DB aynı değere baktığı için ayrışamaz (Kural 19), Türkçe ad DB'ye ulaşmadan 409 olur, ERROR logu oluşmaz (Kural 18).
   - `spring.datasource.password` varsayılanı `embedding-local` (Kural 6, D8).
2. **Docker (devops):**
   - `deployment/docker-compose.yml`: dört servis (`pgadmin` eklenir), bütün değerler düz yazılı, `${…}` yok (Kural 1-3, D6).
   - Yeni `deployment/docker-compose.dev.yml`: `postgres` + `pgadmin` (Kural 5).
   - Yeni `deployment/pgadmin/servers.json`: hazır sunucu tanımı (Kural 10, D7).
   - `deployment/.env.example` ve yerel `deployment/.env` silinir (Kural 2).
3. **Rehber (devops):** kökte yeni `README.md` tek çalıştırma rehberi (Kural 14, 15, D9). `ollama.md`'den kurulum/başlatma adımları çıkar, README'ye bağlanır. `deployment/CLAUDE.md` güncellenir.
4. **E2E (tester):** E1 yenileme testi iş süresinden bağımsız (D10); `console.log` kalkar (D11); takım 10 kez koşulur.

**Değişen mevcut parçalar (eski ad → yeni ad):**
- DB index `uk_sources_workspace_id_name` → `uk_sources_workspace_id_name_key` (V8 eskisini siler).
- Compose projesi adı (varsayılan klasör adı) `deployment` → `embedding-pgvector` (D5). Container adları `deployment-backend-1` → `embedding-pgvector-backend-1`; volume `deployment_pgdata` → `embedding-pgvector_pgdata`.
- `Source`: + `nameKey` alanı, + `static String toNameKey(String name)`, + `getNameKey()`. `Workspace.addSource` imzası değişmez.
- Dosya adları (kart soru 1, kullanıcı A'yı seçti): `deployment/docker-compose.yml` kalır, yeni dosya `deployment/docker-compose.dev.yml`.

**Bölme:** Task bölünmüyor. Üç parça (klonla-çalıştır + pgAdmin + rehber, Türkçe ad düzeltmesi, E2E güvenilirliği) birbirinden bağımsız ama her biri küçük; bölmek üç ayrı onay ve qa turu demek. TASK bunları tek iş olarak yazmış.

**Uygulama sırası:** backend-coder (migration + entity + properties) → tester (backend testleri; E2E değişikliği ve 10 koşu, yeni backend koduyla açılmış sisteme karşı) → devops (compose'lar, pgAdmin, README, `ollama.md`) → qa (temiz kopya, iki kurulum, pgAdmin, Ollama kapalı, rehber, 10+10 E2E).
- Tester'ın E2E'si için sistem: devops adımı henüz yapılmadığı için tester eski compose'u şifreyi komut satırında vererek açar. Bu sadece tester'ın test ortamıdır; kullanıcıya giden akış devops'tan sonrakidir.
  1. `docker compose -f deployment/docker-compose.yml down -v`. Eski `deployment_pgdata` volume'u diskteki `deployment/.env`'in şifresiyle kurulmuş olabilir. PostgreSQL `POSTGRES_PASSWORD`'ü sadece boş volume'da uygular; volume kalırsa backend `embedding-local` ile bağlanamaz. Temiz seed E2E için zaten gerekli.
  2. `POSTGRES_PASSWORD=embedding-local docker compose -f deployment/docker-compose.yml up --build -d`. Komut satırındaki değişken, Compose'un okuduğu `deployment/.env`'deki değerden önce gelir; postgres ve backend aynı şifreyi alır. Yeni backend imajı V8'i uygular.
  3. `ps` → 3 servis `healthy`, sonra E2E.

## Çalışacak roller
- **planner:** bu plan. Sözleşme değişmez (`docs/api/` dokunulmaz).
- **backend-coder:** `backend/src/main/**` (migration V8, `Source`, `Workspace`, `application.properties`), `backend/CLAUDE.md` (`.env` / `POSTGRES_PASSWORD` satırları).
- **tester:** backend testleri (`backend/src/test/**`), `frontend/e2e/upload.spec.ts`; E2E takımını 10 kez koşar.
- **devops:** `deployment/docker-compose.yml`, `deployment/docker-compose.dev.yml`, `deployment/pgadmin/servers.json`, `deployment/.env.example` ve `deployment/.env` silme, `deployment/CLAUDE.md`, `ollama.md`, kök `README.md` (sahiplik notu aşağıda).
- **qa:** temiz kopya denemesi, iki kurulum, pgAdmin, Ollama kapalı, rehberi sırayla uygulama, 10 + 10 E2E, Türkçe ad senaryosu, komutlar.
- **Atlanan: frontend-coder.** `frontend/src/**` ve `vite.config.ts`'te değişiklik yok: Vite proxy `/api` → `http://localhost:8080` zaten var (Kural 6). Değişen tek frontend dosyası `frontend/e2e/upload.spec.ts`, tester'ın. `frontend/CLAUDE.md` komutları geçerli kalıyor.
- **Sahiplik notu:** Kök `README.md` kök `CLAUDE.md` rol tablosunda devops satırına eklendi (lead, kullanıcı onayıyla).
- **Değişmeyenler:** kök `.gitignore` (`.env` satırı kalır: silinen yerel `.env` yeniden oluşturulursa git'e girmez; `deployment/.env.example` satırı zaten yok), `KULLANIM-KILAVUZU.md` (`.env` geçmiyor), `embedding-pgvector.md`, `docs/api/**`, nginx, Dockerfile'lar.

## Kural eşlemesi
| # | Kural (TASK.md) | Nerede uygulanır | Hangi testle doğrulanır |
|---|-----------------|------------------|-------------------------|
| K1 | Tam sistem tek komutla, `.env` yok, dosya kopyalama/düzenleme yok | `deployment/docker-compose.yml` (D6) | Q1 |
| K2 | Bütün ayarlar compose içinde düz değer; `.env`/`.env.example` yok; dışarıdan değişken zorunlu değil | Compose'lar (D6), `.env.example` silinir | Q1, Q9 |
| K3 | Dört servis, hepsinin sağlık kontrolü, hepsi `healthy` | Compose: `pgadmin` healthcheck (D7) | Q1 |
| K4 | Site, backend, pgAdmin sabit portlarla; portlar rehberde | Compose port eşlemeleri (D5), README "Adresler" | Q1, Q8 |
| K5 | Ayrı compose sadece `postgres` + `pgadmin`, `.env` yok | `deployment/docker-compose.dev.yml` | Q5 |
| K6 | Local backend ve frontend ortam değişkeni/şifre olmadan bağlanır; Vite local backend'e iletir | `application.properties` şifre varsayılanı (D8); Vite proxy (değişmez) | Q5 |
| K7 | Local DB de başlangıç verisiyle dolar; arama, kaynaklar, yükleme çalışır | `seed.enabled` varsayılanı `true` (değişmez), Flyway | Q5 |
| K8 | Aynı anda çalışmak zorunda değil; port çakışması ve önce hangisinin durdurulacağı rehberde | README "İki kurulum aynı portu kullanır" (D5) | Q5, Q8 |
| K9 | pgAdmin iki kurulumda da | İki compose'ta `pgadmin` | Q3, Q5 |
| K10 | Bağlantı hazır gelir; sadece giriş; gerekirse DB şifresi, şifre rehberde | `servers.json` + `PGADMIN_REPLACE_SERVERS_ON_STARTUP` (D7), README | Q3 |
| K11 | Beş tablo ve `vector` kolonları pgAdmin'de görünür, sorgu çalışır | pgAdmin + mevcut şema | Q3 |
| K12 | Ollama Docker'da değil; container backend ve local backend kullanıcının Ollama'sına bağlanır | Compose `OLLAMA_BASE_URL: http://host.docker.internal:11434` + `extra_hosts`; properties varsayılanı `http://localhost:11434` (değişmez) | Q1, Q5 |
| K13 | Ollama kapalıyken servisler `healthy`; kelime araması çalışır; anlam araması/yükleme Türkçe "ulaşılamıyor" | Değişmez (health'te Ollama göstergesi yok) | Q4 |
| K14 | Türkçe, tek yerde rehber: gereksinimler, Ollama + `bge-m3`, tam sistem başlat/durdur/sıfırla, local geliştirme, adresler/portlar/girişler | Kök `README.md` (D9) | Q8 |
| K15 | Şifrelerin sadece local olduğu rehberde ve compose'ta yazar | README "Güvenlik notu"; compose dosyalarının ilk satırındaki yorum | Q8, Q9 |
| K16 | Sadece büyük/küçük harf farkı olan ad kullanımda; Türkçe harfler dahil | `Source.toNameKey` (D2), `Workspace.addSource` | B1, B2 |
| K17 | "Yardım merkezi" varken "yardım MERKEZİ" → `SOURCE_NAME_TAKEN` + Türkçe mesaj | `Workspace.addSource`; frontend mesajı değişmez (`errorMessages.ts`) | B2, B4, Q6 |
| K18 | Bu durumda `ERROR` log yok; logda ad ya da kullanıcı verisi yok | Kontrol DB'ye ulaşmadan entity'de karar verir (D3) | B4, Q6 |
| K19 | Uygulama kontrolü ile DB tekillik kuralı aynı adlar için aynı kararı verir | `sources.name_key` Java'da hesaplanır, index ona kurulur (D1) | B3, B5, B6 |
| K20 | Yenileme E2E'si iş süresinden bağımsız geçer | `upload.spec.ts` E1 (D10) | E1 |
| K21 | Takım 10 kez arka arkaya geçer; retry ile gizleme yok | `playwright.config.ts` `retries: 0` (değişmez) | E-10 (tester), Q7 (qa) |
| K22 | E2E'de `console.log` yok | `upload.spec.ts` (D11) | E1; Q9 grep |
| S-1 | Klonla → Ollama → `ollama pull bge-m3` → tek komut → 4 healthy → arama sonuç verir | Hepsi | Q1, Q2, Q8 |
| S-2 | pgAdmin giriş, sunucu hazır, `chunks.embedding` ve satır sayısı | D7 | Q3 |
| S-3 | Tam sistemi durdur, dev compose, `./mvnw spring-boot:run`, `npm run dev`, değişken yok, arama + yeni sürüm | D5, D8 | Q5 |
| S-4 | Ollama kapalı: healthy, kelime araması, anlam aramasında Türkçe mesaj | Değişmez | Q4 |
| S-5 | "yardım MERKEZİ" ve "YARDIM MERKEZI" → yükleme yok, "ad kullanımda", ERROR yok | D1-D3 | B2, B4, Q6 |
| S-6 | E2E Ollama hızlıyken ve yük altında 10'ar kez | D10 | Q7 |
| AC-1 | `./mvnw verify`, `npm run build`, `npm run lint`, frontend testleri | Tüm kod | tester + qa |
| AC-2 | Temiz kopyada 4 servis healthy, qa raporunda | Compose | Q1 |
| AC-3 | Dev compose 2 servis healthy, backend/frontend değişkensiz, arama + yükleme denendi | Dev compose + D8 | Q5 |
| AC-4 | pgAdmin'de bağlantı elle eklenmeden; tablolar ve sorgu sonucu raporda | D7 | Q3 |
| AC-5 | `.env` / `.env.example` yok; compose değişken istemiyor | D6 | Q9 |
| AC-6 | Rehber var, qa adımları sırayla uygulayarak sistemi açıyor | README | Q8 |
| AC-7 | Türkçe büyük harfli ad için otomatik test: `SOURCE_NAME_TAKEN` + ERROR yok | B4 | B4 |
| AC-8 | E2E takımı (TASK-003 + TASK-004) 10 kez, sonuç qa raporunda | — | Q7 |
| AC-9 | `frontend/e2e/` altında `console.log` yok | D11 | Q9 |

## Gözden kaçabilecek durumlar
| # | Durum | Beklenen davranış | Karşılayan test # ya da karttaki soru # |
|---|-------|-------------------|------------------------------------------|
| G1 | V8'den önce oluşmuş bir veritabanı (geliştiricinin eski volume'u, kullanıcı kaynakları dahil) | V8 `name_key`'i mevcut satırlar için doldurur, kolon `NOT NULL` olur, yeni index kurulur; uygulama açılır, eski kaynaklar listede aynen görünür | B6 |
| G2 | Unicode'da ayrık yazılmış İ (`I` + U+0307) ya da baştaki/sondaki boşluk | Ayrık İ önce birleşik İ'ye çevrilir (NFC), sonra aynı anahtara iner. Boşluklar zaten `IngestionService.createSource`'ta kırpılıyor (`strip()`); anahtar kırpılmış addan hesaplanır | B1, B2 (`'  yardım MERKEZİ '`) |
| G3 | Tam sistem açıkken dev compose ya da local backend başlatılıyor | Docker "port is already allocated" / Spring "Port 8080 was already in use" verir; veri bozulmaz. README hangi komutla önce durdurulacağını yazar | Q5, Q8 |
| G4 | pgAdmin verisi eski bir `servers.json`'la kalmış (volume duruyor, tanım değişmiş) | `PGADMIN_REPLACE_SERVERS_ON_STARTUP` ile her açılışta tanım yeniden yüklenir; sürüm desteklemiyorsa README'de `docker volume rm …pgadmin-data` komutu yazar | Q3 |
| G5 | Sistem Ollama kapalıyken açılıp sonra Ollama açılıyor | Başlangıç verisi eksik kalır (seed WARN); README "Ollama'yı açtıktan sonra `docker compose -f deployment/docker-compose.yml restart backend`" der, seed kalanları tamamlar (mevcut davranış) | Q4 |

## Kararlar
| # | Karar | Gerekçe | Elenen alternatif ve nedeni |
|---|-------|---------|-----------------------------|
| D1 | Karşılaştırma anahtarı `sources.name_key text NOT NULL` kolonunda saklanır; değeri her zaman Java'da `Source.toNameKey(name)` ile hesaplanır (V8 backfill hariç, D2). Unique index `uk_sources_workspace_id_name_key ON sources (workspace_id, name_key)`; eski `uk_sources_workspace_id_name` silinir. `Workspace.addSource` mevcut kaynakların `getNameKey()`'i ile yeni adın `toNameKey`'ini karşılaştırır | Kural 19: iki kontrol aynı değere baktığı için ayrışamaz. DB'nin `lower()`'ı veritabanı locale'ine (libc/ICU) bağlı, Java'nınki değil; aynı fonksiyonu iki dilde yazmak yine ayrışma riski taşır | Fonksiyonel index `lower(translate(name,…))` + Java'da benzer kod: iki implementasyon, collation değişirse ayrışır. Generated column: DB hesaplar, aynı sorun. ICU nondeterministic collation (`und-u-ks-level2`): `ı`/`i` ICU'da birincil düzeyde farklı, "YARDIM MERKEZI" senaryosunu karşılamaz |
| D2 | `Source.toNameKey(name)` = `Normalizer.normalize(name, NFC)` → `İ`, `I`, `ı` karakterleri `i` ile değiştirilir → `toLowerCase(Locale.ROOT)`. Diğer harfler (Ş Ğ Ü Ö Ç) standart küçük harfe iner. V8 backfill SQL karşılığı: `lower(translate(normalize(name, NFC), 'İIı', 'iii'))` | TASK örnek senaryosu "YARDIM MERKEZI"nin "Yardım merkezi" ile aynı sayılmasını istiyor; Türkçe kurala göre `MERKEZI` → `merkezı` olur ve eşleşmez. Dört i biçimini tek harfe indirmek iki örneği de karşılayan tek kanonik anahtar. Sonucu kartta kullanıcı kararı olarak yazıldı ("Kıyı" = "Kiyi") | `toLowerCase(Locale.forLanguageTag("tr"))`: "YARDIM MERKEZI" → `yardım merkezı`, senaryo 5'in ikinci örneği kırılır. Sadece `Locale.ROOT`: bugünkü hata. Ş/S, Ğ/G gibi tüm aksanları kaldırmak: TASK istemiyor, "Şifre" ≠ "Sifre" kalmalı |
| D3 | Log ayarı değişmez (`org.hibernate.orm.jdbc.error` kapatılmaz). Türkçe ad artık entity kontrolünde reddedildiği için DB'ye `INSERT` gitmez, ERROR satırı oluşmaz. `IngestionService.createSource`'taki `DataIntegrityViolationException` → `SOURCE_NAME_TAKEN` yakalaması son güvence olarak kalır (çalışma alanı satırı `findByIdForUpdate` ile kilitli olduğu için normal akışta ulaşılmaz) | Kural 18'in kaynağı ERROR'u bastırmak değil, gereksiz DB hatasını önlemek. Hibernate SQL hata logunu kapatmak gerçek SQL hatalarını da gizler | Log seviyesini `OFF` yapmak: teşhis kaybı. Exception mesajından adı ayıklamak: Hibernate logunu biz yazmıyoruz |
| D4 | Dosya adları (öneri A, kart soru 1): `deployment/docker-compose.yml` (tam sistem) ve `deployment/docker-compose.dev.yml` (local geliştirme) | Mevcut ad 20+ yerde (kök `CLAUDE.md`, `.claude/agents/devops.md`, skill'ler, `ollama.md`, CLAUDE.md'ler) geçiyor; bir kısmı hiçbir rolün değil. Compose iki uzantıyı da okur | `.yaml`'a geçmek: kullanıcı seçerse uygulanır (Özet'teki liste) |
| D5 | Her compose'un üst düzey `name:` alanı var: tam sistem `embedding-pgvector`, dev `embedding-pgvector-dev`. Volume'lar proje adıyla ayrılır: tam sistem ve dev farklı veritabanı ve pgAdmin verisi tutar. Portlar ikisinde aynı: postgres `127.0.0.1:5432:5432`, pgadmin `127.0.0.1:5050:80`; tam sistemde ayrıca backend `8080:8080`, frontend `3000:8080` (değişmez). Aynı anda çalışmazlar (G3) | Aynı proje adı (ikisi de klasör adı `deployment`) olsaydı dev `up` tam sistemin container'larını "orphan" görür, `down` ikisini karıştırırdı. Ayrı veri: E2E'nin (her koşu kaynak ekler, 302'ye sürüm yükler) local çalışmayı bozmaması. Local backend varsayılanı `localhost:5432` olduğu için dev postgres de 5432'de olmalı (K6); backend 8080 zaten çakıştığı için ayrı portlar eşzamanlılık kazandırmazdı | Ortak volume: E2E verisi local'e taşar. Dev'de farklı portlar (5433, 5051): local backend'e `DATABASE_URL` vermek gerekirdi (K6'ya aykırı) |
| D6 | Compose'larda hiç `${…}` yok; değerler düz yazılı: `POSTGRES_DB: embedding_pgvector`, `POSTGRES_USER: embedding`, `POSTGRES_PASSWORD: embedding-local`; backend `DATABASE_URL: jdbc:postgresql://postgres:5432/embedding_pgvector`, `POSTGRES_USER: embedding`, `POSTGRES_PASSWORD: embedding-local`, `OLLAMA_BASE_URL: http://host.docker.internal:11434`, `EMBEDDING_MODEL: bge-m3`, `EMBEDDING_DIMENSIONS: "1024"`, `SEED_ENABLED: "true"`. Dosyaların ilk satırı İngilizce yorum: `# Local use only: credentials in this file are for this machine and must not be used on a server.` `deployment/.env.example` silinir. Ayar değiştirmek isteyen dosyayı düzenler (README'de not) | Kural 1, 2, 15. Kullanıcı varsayılanla `${X:-v}`'yi de "dışarıdan değişken" diye görebilir; TASK "düz değer" diyor | `${X:-default}`: TASK'ın "düz değer" ifadesine aykırı, `.env` beklentisini geri getirir |
| D7 | pgAdmin: imaj sabit 9.x etiketi (öneri `dpage/pgadmin4:9.8`; devops `docker pull` ile var olduğunu doğrular, yoksa en yakın 9.x yamasını seçip raporlar; `latest` yok). Ortam: `PGADMIN_DEFAULT_EMAIL: admin@example.com`, `PGADMIN_DEFAULT_PASSWORD: admin-local`, `PGADMIN_REPLACE_SERVERS_ON_STARTUP: "True"`, `PGADMIN_CONFIG_MASTER_PASSWORD_REQUIRED: "False"`. Volume `pgadmin-data:/var/lib/pgadmin`. Bind mount `./pgadmin/servers.json:/pgadmin4/servers.json:ro` (iki compose aynı dosyayı kullanır; iki projede de DB servisi `postgres` adında). `servers.json`: tek sunucu, `Name: "embedding-pgvector"`, `Group: "Servers"`, `Host: "postgres"`, `Port: 5432`, `MaintenanceDB: "embedding_pgvector"`, `Username: "embedding"`, `SSLMode: "prefer"`; şifre/`PassFile` yok. Healthcheck `wget -q -O /dev/null http://127.0.0.1:80/misc/ping` (interval 10s, timeout 5s, retries 10, start_period 60s; devops imajda `wget` olduğunu doğrular). `depends_on: postgres: condition: service_healthy`. Sunucu modu açık (giriş ekranı var) | Kural 9-11. Giriş bilgileri TASK Kural 2'de istenmiş. `.local` gibi özel alan adları pgAdmin'in e-posta doğrulamasında reddedilebildiği için `example.com`. `PassFile` için dosya 0600 ve uid 5050 sahipliği gerekiyor; bind mount'ta kırılgan. TASK "gerekirse şifreyi girer" diyor | Masaüstü modu (`SERVER_MODE False`, girişsiz): TASK giriş bilgisi istiyor. `PassFile` + pgpass mount: izin sorunu. `.local` e-posta: doğrulama hatası riski |
| D8 | `application.properties`: `spring.datasource.password=${POSTGRES_PASSWORD:embedding-local}`. URL ve kullanıcı varsayılanları aynı kalır (`localhost:5432/embedding_pgvector`, `embedding`). Container backend compose'tan aynı değeri alır | Kural 6: local backend değişken almadan dev postgres'e bağlanır. Değer sadece local şifre (Kural 15); gerçek gizli bilgi değil | Ayrı `application-local.properties` profili: `-Dspring.profiles.active=local` gerekir, K6'ya aykırı |
| D9 | Tek çalıştırma rehberi kök `README.md` (Türkçe). `ollama.md`: bölüm 1-3 (Ollama kurulum, sunucu, model indirme) ve 5.1 (Docker ile başlatma), bölüm 6'daki `.env` / `POSTGRES_PASSWORD` satırları ve "Modeli ya da Ollama adresini değiştirme" altındaki `.env` anlatımı README'ye bağlantıyla değiştirilir; 4, 5.2-5.6 ve 6'nın kalanı ("deneyler") kalır, komutlardaki `POSTGRES_PASSWORD=…` önekleri ve `.env` atıfları kaldırılır | Kural 14 "tek bir yerde". Klonlayan kişi kökte README arar; GitHub ilk onu gösterir. Aynı adımı iki dosyada tutmak zamanla çelişir | `deployment/README.md`: kökte görünmez. Rehberi `ollama.md`'ye yazmak: ad, Docker/pgAdmin/local geliştirme içeriğini anlatmıyor |
| D10 | E1 (`givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady`) ağı tarayıcı tarafında yönetir: test, yüklemeden önce `page.route` ile `GET /api/workspaces/42/sources*` ve `GET /api/workspaces/42/ingestion-jobs/*` isteklerini yakalar. Her istek `route.fetch()` ile gerçek backend'e gider. "Tutma" açıkken gerçek yanıtta iş (`jobId` = yükleme yanıtının `jobId`'si) `DONE`/`FAILED` ise yanıt, işin son görülen aktif hâliyle (başlangıçta 202 gövdesindeki `QUEUED` iş) değiştirilir; kaynak listesinde/detayında 302 satırı yükleme öncesi yakalanan 302 satırı + `status: 'INGESTING'` + `latestJob` = o aktif hâl olur. İş gerçekte hâlâ aktifse yanıt değiştirilmeden geçer. Test yenileme sonrası ilerlemeyi ve `GET .../ingestion-jobs/{jobId}` isteğini gördükten sonra tutmayı kapatır; bundan sonra bütün yanıtlar gerçek (bitiş toast'u, "Hazır", "v3.2") | Kural 20: yenileme anının işin süresine bağlı olmaması. Değiştirilen yanıt backend'in iş sürerken gerçekten döndürdüğü şekle eşit (`status INGESTING`, eski sürüm, aktif `latestJob`; TASK-004 B3). Backend'in `latestJob` doğruluğu TASK-004 B2-B4 integration testlerinde | Yavaş sahte embedding: tam sistemde gerçek Ollama var; test için üretim koduna bekleme kancası eklemek yasak (`writing-good-tests`). Daha büyük dosya: hâlâ süreye bağlı. Retry: Kural 21 yasaklıyor |
| D11 | `console.log` satırları silinir. Süre bilgisi isteniyorsa `test.info().annotations.push({ type: 'ingestion-ms', description: String(ms) })` ile rapora yazılır (list reporter gösterir) | Kural 22; qa süreleri raporlayabilsin | Süreyi tamamen kaldırmak: qa 10 koşuda süre dağılımını raporlayamaz |
| D12 | V8 migration sırası: kolon `NULL`'a izinli eklenir → backfill → `SET NOT NULL` → eski index `DROP` → yeni unique index. Mevcut veride çakışan anahtar olursa (aynı alanda "Kıyı" ve "Kiyi") index kurulamaz ve migration hata verir; seed verisinde böyle çift yok (alan başına 3 farklı ad) | G1. Temiz kurulumda V8 çalışırken `sources` boştur (seed uygulama açılınca yüklenir) | Çakışanları otomatik yeniden adlandırmak: kullanıcı verisini sessizce değiştirir |

## Sözleşme değişiklikleri
Yok. `docs/api/openapi.yaml` ve `errorCode` listesi aynı; `name_key` API'ye çıkmaz (`SourceResponse` değişmez). WebSocket yok.

## Backend planı
Yüklenecek skill'ler: `backend-conventions`, `flyway-migrations`, `postgres-patterns`, `jpa-patterns`, `spring-transactions`.
Yeni dependency yok (`java.text.Normalizer` JDK'da).

| Dosya / sınıf | Değişiklik |
|---------------|------------|
| `db/migration/V8__add_name_key_to_sources.sql` | D12 sırasıyla: `ALTER TABLE sources ADD COLUMN name_key text`; `UPDATE sources SET name_key = lower(translate(normalize(name, NFC), 'İIı', 'iii'))`; `ALTER COLUMN name_key SET NOT NULL`; `DROP INDEX uk_sources_workspace_id_name`; `CREATE UNIQUE INDEX uk_sources_workspace_id_name_key ON sources (workspace_id, name_key)`. V1-V7 değişmez |
| `Source` | + `@Column(name = "name_key", nullable = false) private String nameKey;` private constructor'da `this.nameKey = toNameKey(name)` (hem `ingesting` hem `seeded` buradan geçer). + `public static String toNameKey(String name)` (D2). + `public String getNameKey()` |
| `Workspace.addSource` | `toLowerCase(Locale.ROOT)` karşılaştırması → `Source.toNameKey(sourceName)` ile `source.getNameKey()` eşitliği. Hata, kategori ve `errorCode` aynı (`ConflictException("SOURCE_NAME_TAKEN", …)`, mesaj İngilizce, adı içermez — bugünkü gibi sadece workspace id) |
| `IngestionService.createSource` | Değişmez (D3) |
| `SeedDataLoader` | Değişmez (`Source.seeded` anahtarı kendisi hesaplar) |
| `application.properties` | `spring.datasource.password=${POSTGRES_PASSWORD:embedding-local}` (D8) |
| `backend/CLAUDE.md` | "Teknoloji/Veritabanı": `POSTGRES_PASSWORD` (varsayılan `embedding-local`, local dev compose ile aynı). "Komutlar": veritabanını başlat → `docker compose -f deployment/docker-compose.dev.yml up -d` (postgres + pgAdmin); çalıştır → `./mvnw spring-boot:run` (`POSTGRES_PASSWORD=` öneki kalkar); `SEED_ENABLED=false ./mvnw spring-boot:run`; "Başka veritabanı…" satırından `POSTGRES_PASSWORD=...` çıkar. Şema satırında migration aralığı `V1`-`V8` |

- Log eklenmez, değişmez.
- `Workspace` → `Source.toNameKey` bağımlılığı zaten var olan `workspace → source` yönünde.

## Frontend planı
Yok (frontend-coder atlandı). `errorMessages.ts`'teki `SOURCE_NAME_TAKEN` metni ("Bu ad çalışma alanında başka bir kaynakta kullanılıyor.") ve ad alanı altında gösterimi (TASK-004 `uploadFormErrors.ts`) aynen kullanılır.

E2E değişikliği tester'ındır (aşağıda E1). Ek skill: `e2e-testing`, `writing-good-tests`.

## Deployment planı
devops kendi agent tanımını ve `deployment/CLAUDE.md`'yi izler; ek skill gerekmez. Yeni imaj: `dpage/pgadmin4` (sabit 9.x etiketi, D7). Yeni uygulama dependency'si yok.

### `deployment/docker-compose.yml` (tam sistem)
- Üst satır D6 yorumu; `name: embedding-pgvector`.
- `postgres`: imaj, volume, port, healthcheck aynı; `POSTGRES_PASSWORD: embedding-local`.
- `backend`: D6 değerleri, `extra_hosts`, healthcheck, port aynı.
- `frontend`: değişmez.
- `pgadmin`: D7.
- `volumes: pgdata, pgadmin-data`.

### `deployment/docker-compose.dev.yml` (local geliştirme)
- Üst satır D6 yorumu; `name: embedding-pgvector-dev`.
- `postgres` ve `pgadmin` tam sistemle aynı tanım (imaj, değerler, port, healthcheck, `servers.json` mount).
- `volumes: pgdata, pgadmin-data` (proje adıyla ayrı).

### `deployment/pgadmin/servers.json`
D7'deki tek sunucu tanımı (`Servers` → `"1"`).

### Silinecek
- `deployment/.env.example` (repodaki şablon).
- `deployment/.env` (bu makinedeki yerel dosya, git'te yok; 110 bayt). Yeni compose'larda `${…}` olmadığı için içeriği hiçbir şeyi etkilemez, ama Kural 2 ve AC-5 "projede `.env` kalmaz" diyor. İçindeki eski şifre sadece eski `deployment_pgdata` volume'unun şifresidir; o volume da Q1 öncesi silinir.
- Silmeden önce sıra: tester'ın açtığı eski sistem kapatılır (aşağıda), sonra iki dosya silinir.

### `deployment/CLAUDE.md`
- Servis tablosuna `pgadmin` (imaj, görevi, `127.0.0.1:5050`); proje adları ve ayrı volume'lar.
- "Ortam değişkenleri" bölümü: değerler compose'ta düz yazılı, `.env` kullanılmaz, şifreler local; `.env.example`/`cp` satırları kalkar.
- "Komutlar": tam sistem ve dev için `up`/`ps`/`down`/`down -v`; local geliştirme satırı dev compose ile; eski proje `deployment`'ten kalan veriyi silmek için bir kez `docker compose -p deployment down -v`.
- "Tuzaklar"a: pgAdmin `servers.json` ilk girişte/her açılışta yüklenir (D7), e-posta doğrulaması.

### Kök `README.md` (Türkçe; sahiplik notu "Çalışacak roller"de)
Bölümler, bu sırayla:
1. **Proje** (2-3 cümle; anlatım sayfaları ve demo).
2. **Gereksinimler:** Docker Desktop (Compose v2), Ollama; local geliştirme için Java 26 (`./mvnw` Maven'i kendisi getirir) ve Node 26 + npm 11. Disk: `bge-m3` ~1,2 GB.
3. **Ollama ve model:** kurulum (macOS `brew install ollama` ya da ollama.com), başlatma (`ollama serve` ya da uygulama), `ollama pull bge-m3`, kontrol `curl -s localhost:11434/api/tags` (listede `bge-m3`). Ollama'nın Docker'da çalışmadığı, iki kurulumun da bu Ollama'ya bağlandığı.
4. **Tam sistem:** başlat `docker compose -f deployment/docker-compose.yml up --build -d`; durum `… ps` (4 servis `healthy`; backend ilk açılışta 30-60 sn, seed ~30 sn daha); durdur `… down`; veriyi sıfırla `… down -v`.
5. **Local geliştirme:** önce tam sistemi durdur (`… docker-compose.yml down`); `docker compose -f deployment/docker-compose.dev.yml up -d` (2 servis `healthy`); terminal 1 `cd backend && ./mvnw spring-boot:run` (8080); terminal 2 `cd frontend && npm ci && npm run dev` (5173). Ortam değişkeni gerekmediği. Durdur/sıfırla dev compose ile. Local'in verisinin tam sistemden ayrı olduğu (D5).
6. **Adresler ve giriş bilgileri** (tablo): Site `http://localhost:3000` (tam sistem) / `http://localhost:5173` (local); Backend `http://localhost:8080`; Swagger `http://localhost:8080/swagger-ui.html`; pgAdmin `http://localhost:5050` — `admin@example.com` / `admin-local`; Veritabanı `localhost:5432`, DB `embedding_pgvector`, kullanıcı `embedding`, şifre `embedding-local`.
7. **pgAdmin:** giriş → sol tarafta Servers → `embedding-pgvector`; ilk bağlantıda şifre `embedding-local` ("Save password"); `embedding_pgvector` → Schemas → public → Tables; Query Tool örneği `select count(*) from chunks;` (temiz seed 1.410) ve `select id, embedding_model, vector_dims(embedding) from chunks limit 5;`.
8. **İki kurulum aynı portu kullanır:** 5432, 5050 (ve local backend 8080); hangisinin önce durdurulacağı, hata mesajları (G3).
9. **Ollama kapalıysa:** sistem açılır, kelime araması çalışır, anlam araması/yükleme "Embedding servisine (Ollama) ulaşılamıyor…" der; Ollama açılınca `docker compose -f deployment/docker-compose.yml restart backend` (G5).
10. **Güvenlik notu:** şifreler sadece bu bilgisayarda yerel kullanım içindir; pgAdmin ve veritabanı sadece `127.0.0.1`'de açık; sunucuya kurma (Kural 15).
11. **Daha fazlası:** `ollama.md` (Ollama ve API deneyleri), `KULLANIM-KILAVUZU.md`.
- Ayar değiştirmek (model, seed kapalı) için compose dosyasındaki değeri düzenleme notu (bölüm 4 sonunda, 1-2 satır).

### `ollama.md`
D9'a göre kısalır; başına "Kurulum ve başlatma için `README.md`" satırı. Kalan komutlarda `.env`, `POSTGRES_PASSWORD=…` ve `deployment-backend` gibi eski proje adları güncellenir (ör. `ps` örnek çıktısı).

### devops doğrulaması (deploy raporunda)
- Önce tester'ın eski proje adıyla açtığı sistemi kapat: `docker compose -p deployment down -v` (yoksa 5432/8080/3000 çakışır).
- `ls -la deployment/` → `.env` ve `.env.example` yok.
- `docker compose -f deployment/docker-compose.yml config` ve `… docker-compose.dev.yml config` ortamda hiç değişken yokken hatasız.
- Tam sistem `up --build -d` → 4 `healthy`; `down`; dev `up -d` → 2 `healthy`; pgAdmin'de sunucu görünür (tarayıcı ya da `curl -s localhost:5050/misc/ping` → `PING`).

## Test senaryoları
**Backend:** mevcut altyapı (`PostgresIntegrationTest`, `FakeEmbeddingIntegrationTest`, `TestcontainersConfiguration`, fixture'lar). Mevcut testlerde güncellenecekler:
- `FakeEmbeddingIntegrationTest.insertSource` (satır 97) ve `SchemaMigrationIntegrationTest` (satır 64) ham `INSERT INTO sources` → `name_key` kolonu eklenir; değer `Source.toNameKey(name)` (kurulum verisi; beklenti değil).
- `SchemaMigrationIntegrationTest` satır 56 `uk_sources_workspace_id_name` + `lower(` → B5.
- `SourceRepositoryTest` satır 68 `contains("uk_sources_workspace_id_name")` → `"uk_sources_workspace_id_name_key"`.

| # | Senaryo | Katman | Test adı (given_when_then) | Beklenen sonuç (somut değerle) |
|---|---------|--------|----------------------------|--------------------------------|
| B1 | Anahtar fonksiyonu (K16, D2, G2) | unit `SourceTest` | • `givenNamesDifferingOnlyInCaseOrDottedI_whenToNameKey_thenReturnsSameKey` (tablo)<br>• `givenNamesDifferingInTurkishLetters_whenToNameKey_thenReturnsDifferentKeys` (tablo) | **Aynı anahtar (literal):**<br>• "Yardım merkezi", "yardım MERKEZİ", "YARDIM MERKEZI", "Yardim Merkezi" → `"yardim merkezi"`<br>• "İstanbul notları" (ayrık İ) → `"istanbul notlari"`<br>• "ŞİFRE SSS", "şifre sss" → `"şifre sss"`<br>• "ĞÜÖÇ notları" → `"ğüöç notlari"`<br>**Farklı anahtar:** "Şifre SSS" ≠ "Sifre SSS"; "Göç" ≠ "Goc"; "Ücret" ≠ "Ucret" |
| B2 | Entity kuralı (K16, K17, S-5) | unit `WorkspaceTest` | • `givenYardimMerkezi_whenAddSourceWithTurkishCaseVariant_thenThrowsSourceNameTaken` (tablo)<br>• `givenSifreSss_whenAddSourceSifreSssWithoutCedilla_thenAddsSource` | • Alanda "Yardım merkezi" varken "yardım MERKEZİ", "YARDIM MERKEZI", "YARDIM MERKEZİ" → `ConflictException`, `errorCode` `SOURCE_NAME_TAKEN`; alanın kaynak sayısı 1 kalır<br>• "Şifre SSS" varken "Sifre SSS" → eklenir, dönen kaynağın `getNameKey()` `"sifre sss"`, kaynak sayısı 2 |
| B3 | DB tekilliği anahtarla (K19) | repository `SourceRepositoryTest` (`@DataJpaTest`) | • `givenSourceInWorkspace_whenSaveAnotherWithSameNameKey_thenUniqueIndexRejects`<br>• `givenSourceInWorkspace_whenSaveSameNameKeyInOtherWorkspace_thenSaved` | • Alan 42'de "Yardım merkezi" → aynı alana `Source.ingesting(…, "YARDIM MERKEZI", …)` `saveAndFlush` → `PersistenceException`, kök mesaj `uk_sources_workspace_id_name_key` içerir<br>• Alan 19'a aynı ad → kaydedilir (`flush` + `clear` sonrası `findById` dolu, `nameKey` `"yardim merkezi"`) |
| B4 | Uçtan uca HTTP + log (K17, K18, AC-7) | integration `SourceIntegrationTest` + `@ExtendWith(OutputCaptureExtension.class)` | `givenYardimMerkeziCreatedViaApi_whenCreateSourceWithTurkishUppercaseName_thenReturns409WithoutErrorLog` (`@ParameterizedTest`: `"yardım MERKEZİ"`, `"YARDIM MERKEZI"`, `"  yardım MERKEZİ "`) | **Kurulum:** alan 42 boş; "Yardım merkezi" `createSource` API'si ile eklenir (202).<br>**Eylem:** aynı alana parametredeki adla `yeni.md` yüklenir.<br>**Beklenen:**<br>• 409, `Content-Type` `application/problem+json`, `errorCode` `SOURCE_NAME_TAKEN`<br>• `SELECT count(*) FROM sources WHERE workspace_id = 42` → 1; `ingestion_jobs` sayısı 1 (sadece ilk kaynağın işi)<br>• Eylem sırasında yakalanan çıktıda (`CapturedOutput`, eylemden önceki uzunluktan sonrası) ` ERROR ` geçen satır 0; `MERKEZ` ve `merkez` geçen satır 0 |
| B5 | Şema (K19) | integration `SchemaMigrationIntegrationTest` | `givenMigratedSchema_whenInspectSources_thenNameKeyNotNullAndUniqueIndexOnWorkspaceAndNameKey` | `information_schema.columns`: `sources.name_key` `text`, `is_nullable = 'NO'`; `pg_indexes`: `uk_sources_workspace_id_name_key` tanımı `UNIQUE` ve `(workspace_id, name_key)`; `uk_sources_workspace_id_name` adında index yok |
| B6 | Eski veritabanı backfill (G1, D12) | integration `NameKeyMigrationIntegrationTest` (ayrı Testcontainers DB'si; Flyway API ile `target("7")` → satır ekle → `migrate()`) | `givenSourcesCreatedBeforeV8_whenMigrateToV8_thenNameKeyBackfilledLikeJava` | V7'de eklenen satırlar: "Yardım merkezi", "Çözülmüş destek talepleri", "Dalga T5 modem kullanım kılavuzu" → V8 sonrası `name_key` sırasıyla `"yardim merkezi"`, `"çözülmüş destek talepleri"`, `"dalga t5 modem kullanim kilavuzu"` (literal); her satır için `Source.toNameKey(name)` ile de eşit |
| E1 | Yenileme iş süresinden bağımsız (K20, D10) | e2e `upload.spec.ts` | `givenModemGuide_whenUploadVersionAndReload_thenProgressContinuesAndBecomesReady` (ad aynı) | **Kurulum:** `/w/42/kaynaklar`; ilk liste yanıtından 302 satırı saklanır; D10 route'ları kurulur.<br>**Yükleme:** v3.2 PDF + sürüm `3.2`; 202 gövdesinden `jobId`.<br>**Yenileme öncesi:** diyalog kapanır, 302 satırında "İşleniyor", `progressbar`, buton `disabled`.<br>**`page.reload()` sonrası (tutma açık):** 302 satırında `progressbar` ve "İşleniyor" görünür; ardından `GET /api/workspaces/42/ingestion-jobs/{jobId}` isteği gözlenir (`page.waitForRequest`).<br>**Tutma kapanınca (120 sn içinde):** toast `/Dalga T5 modem kullanım kılavuzu v3\.2 hazır · \d+ parça/`; satırda "Hazır" ve "v3.2"; `progressbar` 0.<br>**Kaynak:** `console.log` yok; süreler D11 annotation'ıyla |
| E-10 | Takım tekrarı (K21) | e2e, tester ortamı | — | `npm run test:e2e` arka arkaya 10 kez; her koşu 10 test (TASK-003 4 + TASK-004 6), 0 başarısız, `retries: 0`. Raporda koşu başı süre ve E1 annotation'ları. Kırılan bir test olursa tester nedenini bulur ve zamanlama varsayımını belirleyici bir beklemeyle değiştirir (sabit bekleme ve retry yok) |

TASK-003 ve TASK-004'ün diğer E2E'leri değişmeden geçmeli.

### qa canlı kontrolleri (gerçek Ollama + Docker)
qa kullanıcının Ollama'sını sadece Q4 için durdurur ve sonra eski hâline getirir. Ekran görüntüleri raporda.
- **Q1 (K1-K4, AC-2, S-1):** Projenin `.env` olmayan temiz kopyası scratchpad'e (`rsync -a --exclude node_modules --exclude target --exclude dist --exclude '.env*'`). Önce eski veriler: `docker compose -p deployment down -v`, `docker compose -p embedding-pgvector down -v`, `docker compose -p embedding-pgvector-dev down -v`. Kopyada, ortamda hiçbir değişken yokken `docker compose -f deployment/docker-compose.yml up --build -d` → `ps`'te 4 servis `healthy`.
- **Q2 (S-1):** `http://localhost:3000/w/42/arama` "Şifremi unuttum, online işlemlere giremiyorum" (varsayılan mod) → ilk 10'da "Online İşlemler parolanı sıfırlama".
- **Q3 (K9-K11, AC-4, S-2):** `http://localhost:5050` → `admin@example.com` / `admin-local` → Servers altında `embedding-pgvector` (elle eklenmedi) → şifre `embedding-local` → beş tablo görünür; `chunks` kolonlarında `embedding vector(1024)`; Query Tool `select count(*) from chunks` → 1410 (temiz seed); `select id, vector_dims(embedding) from chunks limit 3` sonucu. `pgadmin` container'ını yeniden başlatınca sunucu yine tek ve aynı (G4).
- **Q4 (K13, S-4, G5):** Ollama durdurulur → `down` + `up -d` → 4 `healthy`; Kelime araması sonuç verir; Anlam araması "Embedding servisine (Ollama) ulaşılamıyor…"; Ollama açılır, `restart backend`.
- **Q5 (K5-K8, AC-3, S-3):** tam sistem `down`; `docker compose -f deployment/docker-compose.dev.yml up -d` → 2 `healthy`; ortamı temiz bir shell'de (`env -u POSTGRES_PASSWORD …`, hiçbir proje değişkeni yok) `cd backend && ./mvnw spring-boot:run` → health `UP`, log `Seed data completed`; `cd frontend && npm run dev` → `http://localhost:5173/w/42/arama` arama sonuç verir; 302'ye v3.2 yükleme → "Hazır". Dev pgAdmin'de de sunucu hazır. Tam sistem açıkken dev `up` → port çakışma mesajı (G3), README'deki çözüm.
- **Q6 (K17, K18, S-5):** sitede 42 → "Yeni kaynak ekle", ad "yardım MERKEZİ", `backend/samples/yardim-notlari.md` → ad alanı altında "Bu ad çalışma alanında başka bir kaynakta kullanılıyor.", diyalog açık; "YARDIM MERKEZI" ile aynısı. `docker compose -f deployment/docker-compose.yml logs backend` → bu denemeler sırasında `ERROR` satırı 0, `MERKEZ` geçen satır 0.
- **Q7 (K21, AC-8, S-6):** tam sistem temiz seed'le açık; `cd frontend && for i in $(seq 1 10); do E2E_BASE_URL=http://localhost:3000 npm run test:e2e || break; done` → 10/10. Sonra yük altında: Ollama'ya paralel embed istekleri (TASK-004 QA yöntemi) ve CPU yükü sürerken aynı döngü 10/10. Her koşunun süresi ve E1 annotation'ları raporda.
- **Q8 (K14, K15, AC-6):** README sırayla uygulanır (Q1 kopyasında); her adımın komutu ve sonucu; adres/port/giriş tablosunun gerçek sistemle uyumu; şifre notu README'de ve iki compose'un ilk satırında.
- **Q9 (K2, K22, AC-5, AC-9):** `find . -name '.env*' -not -path '*/node_modules/*'` → sonuç yok; iki compose'ta `${` 0; `grep -rn 'console\.' frontend/e2e` → 0.
- **Komutlar:** `./mvnw verify`, `npm run build`, `npm run lint`, `npm test`.

## Açık sorular ve riskler
- **Kök `README.md` sahipliği:** kapandı; lead kullanıcı onayıyla kök `CLAUDE.md` rol tablosunda devops satırına `README.md`'yi ekledi.
- **Kart soru 1:** kapandı; kullanıcı A'yı seçti (`docker-compose.yml` + `docker-compose.dev.yml`). Plan bu adlarla yazılı.
- **pgAdmin özellikleri:** `PGADMIN_REPLACE_SERVERS_ON_STARTUP` ve `/misc/ping` seçilen 9.x sürümünde devops tarafından denenir; yoksa G4/healthcheck için README'ye volume silme notu, healthcheck için kök sayfa (`/login`) kullanılır ve deploy raporuna yazılır.
- **E1 tarayıcı tarafında yanıt değiştirme (D10):** Gerçek iş bitmişse ekranda bir süre "sürüyor" gösterilir; backend'in iş sürerken döndürdüğü şekle eşit olduğu için kabul edildi. Backend tarafı integration testleriyle güvencede.
- **E2E verisi büyür:** 20+ koşu (tester 10, qa 20) her seferinde bir kaynak ekler (boş yeri olan en yüksek id'li alana) ve 302'ye sürüm yükler. 37 alanda ~250 boş yer var; sorun beklenmez. qa elle senaryoları (Q2, Q3, Q6) E2E döngüsünden önce temiz seed'de yapar.
- **Kapsam dışı not:** `.claude/skills/frontend-security` ve `vite-patterns` `.env.example` commit'lenmesini öneriyor; bu projede artık `.env` kullanılmıyor. Skill metnini kullanıcı ya da lead günceller (gerekirse).
- **Kapsam dışı not:** `IngestionService.createSource`'taki `DataIntegrityViolationException` yakalaması tüm bütünlük hatalarını `SOURCE_NAME_TAKEN`'a çeviriyor (ör. ileride eklenecek başka bir constraint). Bu task'ta değiştirilmiyor.

## Düzeltme turu 1
Lead'in bildirimi (onaydan sonra): `deployment/.env` diskte var. Bunu ilk okumada görmedim, çünkü dosya araması git'e girmeyen dosyaları göstermiyor. Kart değişmedi, yeniden onay gerekmiyor.
| Madde | Plandaki değişiklik |
|-------|---------------------|
| "`deployment/.env` yok" yanlış | Özet, mevcut durum: yerel `deployment/.env` var, git'te yok |
| `.env` silinmeli (Kural 2, AC-5) | Özet madde 2, "Çalışacak roller" devops, Deployment planı "Silinecek": `deployment/.env` de silinir; devops doğrulamasına `ls -la deployment/` |
| Tester'ın eski compose'u `POSTGRES_PASSWORD=embedding-local` ile açması | Özet "Uygulama sırası": komut satırındaki değişken `.env`'den önce gelir. Eski `deployment_pgdata` volume'u `.env`'deki şifreyle kurulmuş olabileceği için önce `down -v` eklendi |
| Kart soru 1 ve README sahipliği kapandı | Özet ve "Açık sorular" güncellendi |

STATUS: DONE
