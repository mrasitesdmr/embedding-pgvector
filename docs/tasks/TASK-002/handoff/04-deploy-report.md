TASK: TASK-002
# Deploy raporu

## Değişen dosyalar
- `backend/Dockerfile` (yeni): multi-stage. Build `maven:3.9-eclipse-temurin-26` (`-Dmaven.test.skip=true`), runtime `eclipse-temurin:26-jre-noble`; `curl` apt ile kurulur (healthcheck için, JRE imajında yok); kullanıcı `app` (uid 999, root değil).
- `backend/.dockerignore` (yeni): `target/`, `.idea/`, `*.iml`, `.DS_Store`, `.git`, `src/test/` (`samples/` dışarıda bırakılmadı).
- `deployment/docker-compose.yml` (yeni): `postgres`, `backend`, `pgdata` volume.
- `deployment/.env.example` (yeni): `POSTGRES_PASSWORD` (zorunlu), `EMBEDDING_MODEL`, `EMBEDDING_DIMENSIONS`, `SEED_ENABLED`, hepsi açıklamalı.
- `deployment/.env` (yerel, oluşturuldu, rastgele şifre): kök `.gitignore` `.env`'i zaten ignore ediyor (satır 11), ek bir şey gerekmedi.
- `deployment/CLAUDE.md`: "hedef" ifadeleri kaldırıldı, gerçek servis, port ve komutlar yazıldı.
- `ollama.md`: giriş ve şema güncellendi; bölüm 5 Kural 31 sırasıyla yeniden yazıldı (5.1 Docker, 5.2 seed kontrolü, 5.3 yükleme + iş izleme, 5.4 üç mod + efSearch/filtre/explain, 5.5 psql, `/api/embeddings` ve modeli değiştirme bölüm 6'ya taşındı), bölüm 6 hata tablosu genişletildi. Bölüm 1-4 korundu.

## Servisler
| Servis | İmaj / etiket | Port | Durum (ps çıktısı) |
|--------|---------------|------|--------------------|
| postgres | `pgvector/pgvector:0.8.1-pg18` (etiket `docker pull` ile doğrulandı, plandakiyle aynı) | `127.0.0.1:5432` | `Up (healthy)` |
| backend | `deployment-backend` (build: `maven:3.9-eclipse-temurin-26` → `eclipse-temurin:26-jre-noble`) | `8080` | `Up (healthy)` |

`maven:3.9.x` tam yama etiketi yok (`3.9.11-...-26` mevcut değil), `3.9-eclipse-temurin-26` kullanıldı; JRE `26-jre-noble`. Tam yama sabitlemesi Temurin 26 etiketlerinde bulunamadı.

## Smoke test
| Komut | Sonuç |
|-------|-------|
| `docker compose -f deployment/docker-compose.yml config` (`.env` yokken) | exit 1: `required variable POSTGRES_PASSWORD is missing a value: POSTGRES_PASSWORD is required (deployment/.env)`. Compose başlamıyor. |
| `docker compose -f deployment/docker-compose.yml up --build -d` (`.env` ile) | İmaj derlendi, iki container başladı |
| `docker compose ... ps` | `postgres ... Up (healthy)`, `backend ... Up (healthy)` |
| `docker exec deployment-backend-1 id` | `uid=999(app) gid=999(app)`: root değil |
| `curl -s localhost:8080/actuator/health` | `{"groups":["liveness","readiness"],"status":"UP"}` |
| `logs backend \| grep "Seed data"` | `Seed data started workspaces=37 sources=112` → `Seed data completed loadedSources=112 skippedSources=0` (~30 sn) |
| `psql ... select count(*) from workspaces / chunks` | 37 / 1410 |
| `curl -s localhost:8080/api/workspaces/42/sources` | 4 kaynak, hepsi `READY` (301 WEB_CRAWL 21, 302 PDF 3.1 5, 303 CSV 1, 304 TICKET_EXPORT 15) |
| Hybrid arama: `POST /api/workspaces/42/search` `{"query":"nakil","mode":"HYBRID","limit":3}` | 200; `keyword=3 vector=3`; ilk sonuç "Nakil başvurusu" (KEYWORD,SEMANTIC) |
| `psql ... select extname, extversion from pg_extension where extname='vector'` | `vector \| 0.8.1` |
| `POST /api/embeddings` `{"text":"Şifremi unuttum"}` | `bge-m3 1024 1024 42 ms` (container'dan host Ollama'ya erişim çalışıyor) |
| Backend restart | `Seed data completed loadedSources=0 skippedSources=112` (idempotent) |
| PDF yükleme (302'ye v3.2) | 202 + `Location`; iş `DONE`, 12/12 parça; 302 → 3.2, 12 parça |
| `.docx` / 25 MB PDF yükleme | 415 `UNSUPPORTED_FILE_TYPE` / 413 `FILE_TOO_LARGE` |
| `filterStrategy` denemesi (VECTOR, Dalga Fiber `workspace_id = 42`; `channel` kullanılmadı) | `POST_FILTER ef=40` → 4 sonuç, `vectorShort=True`; `ITERATIVE_SCAN` ve `POST_FILTER ef=200` → 10 sonuç; `explain` planında `Index Scan using idx_chunks_embedding`, `Rows Removed by Filter: 35` |

`ollama.md`'deki bütün komutlar yukarıdaki gerçek çalıştırmalardan alındı. Çalıştırılmayan satırlar rehberde işaretli: lokal `./mvnw spring-boot:run` alternatifi (çıktı iddiası yok), hata tablosundaki `EMBEDDING_MODEL_MISMATCH`, `INGESTION_IN_PROGRESS`, `UNSUPPORTED_FILE_ENCODING` ve Docker/port hataları (sözleşmedeki kodlara göre, çıktı alıntısı yok).

Gözlem (hata değil): `KEYWORD` modu `plainto_tsquery` (AND) kullandığı için uzun doğal cümlelerde 0 sonuç döner ("Taşınıyorum, internetimi yeni eve nasıl aldırırım?"); rehber bunu "anahtar kelime aramasının sınırı" diye anlatıyor. Planla (K17) uyumlu.

## Nasıl çalıştırılır
```bash
cp deployment/.env.example deployment/.env      # POSTGRES_PASSWORD'e şifre yaz
docker compose -f deployment/docker-compose.yml up --build -d
docker compose -f deployment/docker-compose.yml ps
docker compose -f deployment/docker-compose.yml logs --tail=100 backend
docker compose -f deployment/docker-compose.yml down        # durdur
docker compose -f deployment/docker-compose.yml down -v     # veriyi de sil
```

## Sistem durumu
Sistem **açık**: postgres + backend healthy, `localhost:8080`. Veritabanı temiz seed durumunda (`down -v` ile sıfırlanıp seed yeniden yüklendi, ardından backend bir kez restart edildi; yükleme yapılmadı, 302 hâlâ v3.1 / 5 parça; sayılar 37 / 1410). Frontend servisi bu task'ta yok. Ollama kullanıcının Mac'inde, durdurulmadı.

## Notlar
- Uygulama koduna (`src/`, `pom.xml`) dokunulmadı; kod değişikliği gerekmedi.
- Kök `CLAUDE.md` rol-yol tablosunda `ollama.md` yok; plan D24 ile yetki verildi, değiştirilmedi.

## Düzeltme turu 1
qa NEEDS_FIX maddeleri uygulandı.
- `ollama.md` 5.4: `channel` yalnızca arama kaydına yazılan etiket olarak anlatıldı (sonuçları sınırlamaz). `Q=` örneğinden `channel` çıkarıldı. "Kısa liste" ve `Rows Removed by Filter` açıklaması `workspace_id = 42` filtresine göre yazıldı: `efSearch=40` adaydan 42'ye ait 4'ü kaldı, 35'i filtreye takıldı. Dosyanın başka yerinde `channel` ile ilgili yanlış anlatım kalmadı.
- Bu raporun smoke test satırındaki yanlış anlatım düzeltildi.
- Temiz seed (`down -v` + `up --build -d`, `loadedSources=112`) üzerinde, rehber sırasıyla 302'ye v3.2 yüklenip 5.4 komutları yeniden çalıştırıldı: POST_FILTER ef=40 → dönen=4, `vectorShort=True`; ITERATIVE_SCAN ef=40 → 10; POST_FILTER ef=200 → 10; explain: `Index Scan using idx_chunks_embedding`, `Rows Removed by Filter: 35`; `search-events` kaydında `"channel":null`. Üç mod örnekleri önceki çıktılarla aynı. Rehberdeki `searchEventId`, `Buffers` ve süre değerleri güncellendi.
- Ardından tekrar `down -v` + `up --build -d`: sistem açık, healthy, temiz seed (302 v3.1 / 5 parça).

STATUS: DONE
