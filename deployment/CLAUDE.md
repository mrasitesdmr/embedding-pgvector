<!--
AMAÇ: Claude (ve ekip üyeleri) deployment/ altında bir dosyayı okuyunca ya da düzenleyince yüklenir.
Durum (TASK-006): tam sistem (postgres, backend, frontend, pgadmin) ve dev compose (postgres, pgadmin) kurulu.
-->

# Deployment (Docker)

## Servisler
İki compose dosyası var; ikisinin de ilk satırı "local use only" yorumudur ve hiçbirinde `${…}` yoktur.
- `docker-compose.yml` (proje adı `embedding-pgvector`): tam sistem, dört servis.
- `docker-compose.dev.yml` (proje adı `embedding-pgvector-dev`): sadece `postgres` + `pgadmin`; backend ve frontend bilgisayarda çalışır. Volume'lar proje adıyla ayrıdır (tam sistem ve dev farklı veri tutar).

| Servis | İmaj | Görevi | Host portu |
|--------|------|--------|------------|
| `postgres` | `pgvector/pgvector:0.8.1-pg18` | PostgreSQL 18 + `vector` eklentisi; veri `pgdata` volume'unda (`/var/lib/postgresql`) | `127.0.0.1:5432` |
| `backend` | `backend/Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-26` build → `eclipse-temurin:26-jre-noble`, kullanıcı `app`) | Spring Boot, `/actuator/health` healthcheck'i (sadece tam sistem) | `8080` |
| `frontend` | `frontend/Dockerfile` (multi-stage: `node:26-alpine` build → `nginxinc/nginx-unprivileged:1.29.8-alpine`, kullanıcı `nginx`, container içi 8080) | SPA + `/api/` → `backend:8080` proxy (`frontend/nginx.conf`); healthcheck `wget` (sadece tam sistem) | `3000` |
| `pgadmin` | `dpage/pgadmin4:9.8` | Veritabanı arayüzü; `pgadmin/servers.json` ile `embedding-pgvector` sunucusu hazır gelir; healthcheck `wget /misc/ping`; veri `pgadmin-data` volume'unda | `127.0.0.1:5050` |

- Giriş: pgAdmin `admin@example.com` / `admin-local`; veritabanı `embedding_pgvector`, kullanıcı `embedding`, şifre `embedding-local`.
- Ollama container'da değil, Mac'te çalışır; backend ona `OLLAMA_BASE_URL=http://host.docker.internal:11434` ile gider (`extra_hosts: host.docker.internal:host-gateway`).
- Başlangıç sırası: `postgres` (healthy) → `backend` (healthy) → `frontend` (healthy); `pgadmin` postgres'ten sonra.
- Sistem boş açılır (başlangıç verisi yok): tek çalışma alanı "Belgelerim" (42) Flyway ile gelir. Örnek belge `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` elle yüklenir (24 parça, `README.md` "Örnek belgeyle dene"). Ollama kapalıyken de servisler healthy olur.
- Eski sürümden kalan volume'daki veri backend'in ilk açılışında (Flyway V9) otomatik silinir; tek seferlik, geri alınamaz.
- Tarayıcı sadece `frontend`'e (3000) bağlanır; `/api/` nginx ile backend'e gider. `/ws` proxy'si henüz yok. Yükleme için `client_max_body_size 21m`.
- Backend imajına paketlenmiş `curl` ile healthcheck yapılır (Temurin JRE imajında yoktur, `apt` ile kurulur).

## Ortamlar
- **Geliştirme (local):** `docker-compose.dev.yml` (postgres + pgadmin); backend `./mvnw spring-boot:run` (8080), frontend `npm run dev` (5173, Vite proxy). Değişken gerekmez.
- **Compose (tam sistem):** site http://localhost:3000, backend http://localhost:8080. Uçtan uca test ve qa bu ortamda yapılır.
- **Prod:** yok.
- İki ortam aynı portları (5432, 5050; local backend ile 8080) kullanır, aynı anda çalışmaz; biri başlamadan diğeri `down` edilir.

## Ayarlar
- Değerler compose dosyalarında düz yazılıdır; `.env`/`.env.example` yoktur ve compose dışarıdan değişken istemez. Değiştirmek için dosya düzenlenir (`EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`).
- Şifreler sadece local kullanım içindir (dosya başındaki yorum). Dockerfile'a ya da imaja gömülmez. Gerçek gizli bilgi bu yapıya girmez.
- Backend `application.properties` varsayılanları (`localhost:5432`, `embedding`, `embedding-local`) dev compose ile uyumludur; local backend değişken almadan bağlanır.

## Komutlar
Hepsi repo kökünden çalıştırılır. Kullanıcı rehberi: `README.md`; Ollama deneyleri: `ollama.md`.
- Önce kontrol: `docker info`. Hata verirse Docker Desktop kapalıdır; kendin düzeltmeye çalışma, lead'e bildir.
- Tam sistem: `docker compose -f deployment/docker-compose.yml up --build -d`; durum `… ps` (4 servis `healthy`); log `… logs --tail=100 backend`; durdur `… down`; veriyi sıfırla `… down -v`.
- Dev: `docker compose -f deployment/docker-compose.dev.yml up -d` (2 servis `healthy`); `… down`; `… down -v`.
- Eski proje adı `deployment`'ten kalan veri: bir kez `docker compose -p deployment down -v`.
- Smoke test: `curl -sf localhost:8080/actuator/health`, `curl -sf localhost:3000`, `curl -s localhost:3000/api/workspaces/42/sources` (belge yüklendiyse kaynak listesi, boş sistemde boş liste), `curl -s localhost:5050/misc/ping` (`PING`).
- pgvector kontrolü: `docker compose -f deployment/docker-compose.yml exec postgres psql -U embedding -d embedding_pgvector -c "select extversion from pg_extension where extname = 'vector'"`
- Uçtan uca test, çalışma alanı 42'nin verisini her koşuda siler (sistem açıkken, önce bir kez `npx playwright install chromium`): `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run test:e2e`
- Ollama kapalı uçtan uca testi (`ollama-down.spec.ts`, 2 test). Ollama'yı kapatmak gerekmez: backend geçici bir override ile ulaşılamayan adrese (port 9, bağlantı hemen reddedilir) yönlendirilir; override dosyası repoya girmez. Sistem açıkken, normal koşudan sonra (veride "Atlaskap…" PDF'i ve "Deneme notu" kaynağı olmalı):
  ```bash
  printf 'services:\n  backend:\n    environment:\n      OLLAMA_BASE_URL: http://host.docker.internal:9\n' > /tmp/ollama-down.override.yml
  docker compose -f deployment/docker-compose.yml -f /tmp/ollama-down.override.yml up -d backend   # healthy olana kadar bekle
  cd frontend && E2E_OLLAMA_DOWN=1 E2E_BASE_URL=http://localhost:3000 npx playwright test e2e/ollama-down.spec.ts
  cd .. && docker compose -f deployment/docker-compose.yml up -d backend                         # geri al; OLLAMA_BASE_URL tekrar :11434
  ```
  Bu koşuda global setup veriyi silmez; `document-flow` testleri `E2E_OLLAMA_DOWN=1` iken atlanır.

## Dosya sahipliği
| Yol | Sahibi |
|-----|--------|
| `deployment/**`, `ollama.md`, kök `README.md` | devops |
| `backend/Dockerfile`, `frontend/Dockerfile`, `*/.dockerignore`, `frontend/nginx.conf` | devops |

- devops uygulama koduna (`src/`, `pom.xml`, `package.json`) dokunmaz. Container'da çalışmak için kod değişikliği gerekiyorsa ilgili coder'a ve lead'e mesaj atar.

## Tuzaklar
- Compose dosyasındaki `build.context` yolları compose dosyasına göre çözülür: `../backend`, `../frontend`.
- Her servisin `healthcheck`'i vardır; bağımlı servis `depends_on: condition: service_healthy` ile bekler. Healthcheck komutu imajda gerçekten bulunan bir araçla yazılır; JRE imajında `curl` olmayabilir, önce kontrol et. postgres için `pg_isready`.
- PostgreSQL 18 imajlarında veri dizini `/var/lib/postgresql/18/docker`'dır; volume `/var/lib/postgresql`'e bağlanır (`/var/lib/postgresql/data`'ya bağlamak 18'de hata verir).
- Backend açıkken tabloları `TRUNCATE ... RESTART IDENTITY` ile sıfırlama (parça id dizisi negatif id üretir); sıfırlama için `down -v`.
- Backend imajı `-Dmaven.test.skip=true` ile paketlenir (testler tester adımında koşar). `backend/.dockerignore` `target/`'ı dışarıda tutar.
- Düz `postgres` imajında `vector` eklentisi yoktur; `CREATE EXTENSION vector` hata verir. pgvector eklentili imaj kullanılır, eklenti Flyway migration'ında oluşturulur.
- nginx'te `add_header` bir `location` içinde tanımlıysa server düzeyindekileri ezer; güvenlik başlıkları bu yüzden her location'da tekrarlanır. `/assets/` ve `/` için CSP (`default-src 'self'`, style için `'unsafe-inline'`) verilir.
- nginx'te `/ws` kullanılıyorsa `Upgrade` ve `Connection` header'ları iletilmezse WebSocket bağlanmaz.
- nginx'te SPA fallback (`try_files $uri /index.html`) yoksa `/lab` gibi sayfalar yenilenince 404 verir.
- `.dockerignore` olmadan `node_modules/` ve `target/` build context'e kopyalanır; build yavaşlar ve gereksiz dosyalar imaja girer.
- İmaj etiketleri sabit sürümdür (`latest` değil). Container'lar root olmayan kullanıcıyla çalışır.
- pgAdmin `servers.json` her açılışta yüklenir (`PGADMIN_REPLACE_SERVERS_ON_STARTUP`); kullanıcı sunucuyu elle değiştirirse silinir. Şifre dosyada yoktur, ilk bağlantıda sorulur. E-posta `.local` gibi özel alan adı olamaz (pgAdmin doğrular), bu yüzden `example.com`.
- İki compose da `./pgadmin/servers.json` dosyasını kullanır; veritabanı servisi ikisinde de `postgres` adındadır.
- Embedding boyutu 1024 dışına çıkamaz: `chunks.embedding` kolonu `vector(1024)`'tür (`bge-m3`). Farklı boyutlu model yeni migration ve yeniden embedding gerektirir.
- 20 MB üstü yüklemeyi nginx keser (`client_max_body_size 21m`); `error_page 413` ile `application/problem+json` gövdesi (`errorCode: FILE_TOO_LARGE`) döner, backend'inkiyle aynı `type` URI'siyle.
