TASK: TASK-005
# Deploy raporu

## Değişen dosyalar
- `deployment/docker-compose.yml` (yeniden yazıldı: `name: embedding-pgvector`, düz değerler, `pgadmin` eklendi, `${…}` yok)
- `deployment/docker-compose.dev.yml` (yeni: `name: embedding-pgvector-dev`, postgres + pgadmin)
- `deployment/pgadmin/servers.json` (yeni)
- `deployment/CLAUDE.md` (gerçek duruma göre güncellendi)
- `README.md` (yeni, kök, Türkçe çalıştırma rehberi)
- `ollama.md` (kurulum/başlatma README'ye bağlandı; `.env`, `POSTGRES_PASSWORD=…`, eski proje adları kaldırıldı)
- Silindi: `deployment/.env`, `deployment/.env.example`
- Dockerfile, nginx.conf, uygulama kodu değişmedi.

## Servisler
Tam sistem (proje `embedding-pgvector`, şu an AÇIK):
| Servis | İmaj / etiket | Port | Durum |
|--------|---------------|------|-------|
| postgres | `pgvector/pgvector:0.8.1-pg18` | 127.0.0.1:5432 | healthy |
| backend | `embedding-pgvector-backend` (build) | 8080 | healthy |
| frontend | `embedding-pgvector-frontend` (build) | 3000 | healthy |
| pgadmin | `dpage/pgadmin4:9.8` (pull ile doğrulandı; `wget` var) | 127.0.0.1:5050 | healthy |

Dev (proje `embedding-pgvector-dev`, kapalı): postgres + pgadmin, ikisi de healthy oldu.

## Doğrulamalar
- Eski sistem: `docker compose -p deployment down -v` → eski `deployment_pgdata`, `deployment_postgres-data` volume'ları ve ağ silindi.
- `ls -la deployment/` → `CLAUDE.md`, `docker-compose.dev.yml`, `docker-compose.yml`, `pgadmin/`; `.env` ve `.env.example` yok.
- `env -i … docker compose -f deployment/docker-compose.yml config -q` ve `… docker-compose.dev.yml config -q` → ortamda hiç değişken yokken hatasız (CONFIG_OK).
- Tam sistem `up --build -d` (sıfır volume'dan) → 4 servis `healthy` (~30 sn). Seed: `Seed data completed loadedSources=112 skippedSources=0`, `select count(*) from chunks` = 1410.
- Tam sistem `down` → dev `up -d` → `ps`: `embedding-pgvector-dev-postgres-1` ve `…-pgadmin-1` healthy.
- Dev açıkken local: `./mvnw spring-boot:run` (değişken yok) → `/actuator/health` UP, seed 112 kaynak tamamlandı (31 sn); `npm ci && npm run dev` → `localhost:5173` 200, `localhost:5173/api/workspaces/42/sources` kaynakları döndü, `POST :5173/api/workspaces/42/search` (HYBRID) 2 sonuç döndü (Vite proxy). Sonra local süreçler ve dev compose durduruldu (8080/5173 boşta).
- pgAdmin: `curl localhost:5050/misc/ping` → `PING`. pgadmin4.db içinde `server` tablosu: `('embedding-pgvector', 'postgres', 'embedding')`, yani bağlantı elle eklenmeden hazır. Tarayıcıyla giriş ve Query Tool denemesi yapmadım (qa'ya bırakıldı).

## Smoke test (tam sistem)
- `curl -s -o /dev/null -w %{http_code} localhost:3000` → 200
- `curl -s localhost:8080/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`
- `curl -s localhost:3000/api/workspaces/42/sources` → 4 kaynak (ilki "Yardım merkezi", 21 parça); seed bitmeden önce ilk denemede boş liste dönmüştü, seed sonrası doğru.
- `curl -s localhost:5050/misc/ping` → `PING`
- WebSocket yok.

## Nasıl çalışıyor / bırakılan durum
Sistem **tam sistem compose'u ile açık**: `docker compose -f deployment/docker-compose.yml …`, proje adı `embedding-pgvector`, site http://localhost:3000. Not: veri tutuldu (volume'lar silinmedi); dev denemesinden sonra `down` + `up -d` yapıldı, seed verisi duruyor. qa "temiz başlangıç" denemesi için önce `docker compose -f deployment/docker-compose.yml down -v` çalıştırmalı.
Başlat: `docker compose -f deployment/docker-compose.yml up --build -d`; durum `… ps`; log `… logs --tail=100 backend`; durdur `… down`; sıfırla `… down -v`. Dev: `docker compose -f deployment/docker-compose.dev.yml up -d`. Ayrıntı: `README.md`.
Ollama açık bırakıldı, dokunulmadı; "Ollama kapalıyken" kontrolü yapılmadı (qa).

STATUS: DONE
