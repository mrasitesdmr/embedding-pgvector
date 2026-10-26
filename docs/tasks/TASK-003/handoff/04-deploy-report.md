TASK: TASK-003
# Deploy raporu

## Değişen dosyalar
- `frontend/Dockerfile` (yeni): `node:26-alpine` build (`npm ci`, `npm run build`) → `nginxinc/nginx-unprivileged:1.29.8-alpine` (kullanıcı `nginx`, uid 101, port 8080).
- `frontend/nginx.conf` (yeni): `/api/` → `http://backend:8080` (`client_max_body_size 21m`, `proxy_read_timeout 120s`, Host ve X-Forwarded-For), `/assets/` `expires 1y` + `immutable`, `/` SPA fallback + `no-cache`. Güvenlik başlıkları (`nosniff`, `Referrer-Policy`, CSP) her location'da tekrarlanır, çünkü nginx'te location içindeki `add_header` server düzeyindekileri ezer.
- `frontend/.dockerignore` (yeni).
- `deployment/docker-compose.yml`: `frontend` servisi (3000→8080, `depends_on backend healthy`, `wget` healthcheck; imajda `wget` var, plan değişmedi).
- `deployment/CLAUDE.md`, `ollama.md` (yeni bölüm 5.6, 3 hata satırı, 5.1 `ps` çıktısı).
- Uygulama koduna dokunulmadı. İzin sistemi tarafından reddedilen komut olmadı. Secret değeri yazılmadı.

## Servisler
| Servis | İmaj / etiket | Port | Durum (ps çıktısı) |
|--------|---------------|------|--------------------|
| postgres | `pgvector/pgvector:0.8.1-pg18` | 127.0.0.1:5432 | Up (healthy) |
| backend | `deployment-backend` (temurin 26) | 8080 | Up (healthy) |
| frontend | `deployment-frontend` (nginx-unprivileged 1.29.8-alpine) | 3000 | Up (healthy) |

## Smoke test
- `docker info` → çalışıyor. `up --build -d` → üç servis `healthy`.
- `curl localhost:3000` → 200. `curl localhost:3000/lab` → 200 (SPA fallback).
- `curl localhost:3000/api/workspaces/42/sources` → 200, `{"content":[{"sourceId":301,...,"name":"Yardım merkezi",...` (proxy backend'e gidiyor).
- `POST localhost:3000/api/workspaces/42/search` (HYBRID, "Şifremi unuttum, online işlemlere giremiyorum") → ilk sonuç "Yardım merkezi | Online İşlemler parolanı sıfırlama | SEMANTIC".
- Başlıklar: `Cache-Control: no-cache`, `X-Content-Type-Options: nosniff`, CSP mevcut. Frontend container kullanıcısı uid 101 (root değil).
- WebSocket: bu task'ta yok; `/ws` proxy'si eklenmedi.
- Test sırasında volume bir kez `down -v` ile sıfırlandı (eski verilerle şüphe elemek için); seed temiz yüklendi (`loadedSources=112`). Sistem şu an açık, temiz seed verisiyle.

## E2E (Playwright, `E2E_BASE_URL=http://localhost:3000`, Chromium)
Sonuç: 4 testten 1 geçti (E4); varsayılan paralel koşuda 3 kaldı, `--workers=1` ile 2 kaldı. Temiz veritabanında da aynı. Sebepler deployment değil, test/uygulama tarafında:
1. **E5 (search-events)**: paralel koşuda kalır. Diğer testlerin yaptığı aramalar ortak backend'de "Arama kayıtları" sayısını eşzamanlı artırıyor (beklenen 1, gelen 2). `--workers=1` ile geçer. Çözüm tester'da: `playwright.config.ts` içinde `workers: 1` (ya da `fullyParallel: false`).
2. **E1 (search.spec)** kalır: `listitem` filtresi `Online İşlemler parolanı sıfırlama` için 2 öğeye çözülüyor (strict mode; sıra 1 ve sıra 7: aynı başlık iki kaynakta/parçada). Locator tester'da `.first()` ya da kaynak adıyla daraltılmalı; ya da bu bir uygulama davranışıysa frontend-coder'a sorulmalı. Önceki `curl` ile top-3'te tekrar yok.
3. **E3 (workspace.spec)** kalır: Kıyı Net (workspace 19) aramasında bir sonuç satırı `${kaynak adı} · parça` ile başlamıyor (assertion `false`). Locator/format tester'da kontrol edilmeli; gerekirse frontend-coder'a sorulmalı.

Not: `ollama.md` 5.6'daki UI adımları (öneri tıklama, "anlam" etiketi, Teknik detay, Arama kayıtları) planla uyumlu yazıldı, ama tarayıcıda elle denemedim. Sadece API karşılığı ve E5 sayfa akışı çalıştırıldı.

## Nasıl çalıştırılır
```bash
cp deployment/.env.example deployment/.env   # bir kez, POSTGRES_PASSWORD yaz
docker compose -f deployment/docker-compose.yml up --build -d
docker compose -f deployment/docker-compose.yml ps
open http://localhost:3000
docker compose -f deployment/docker-compose.yml logs --tail=100 frontend backend
docker compose -f deployment/docker-compose.yml down        # durdur (-v: veriyi de sil)
```

## Düzeltme
Sorumlu rol: **tester** (kısmen belirsiz: frontend-coder)
- E5: `frontend/playwright.config.ts` içinde `workers: 1` (testler ortak backend durumuna bağlı).
- E1: `Online İşlemler parolanı sıfırlama` locator'ı 2 öğeye çözülüyor; daralt ya da `.first()` kullan.
- E3: Kıyı Net arama sonucu satırlarının `${kaynak adı} · parça` biçimi doğrulanmalı; test beklentisi mi, UI formatı mı yanlış belirle.
Deployment kendisi sağlıklı; sistem açık bırakıldı.

## Düzeltme turu 1
Tester düzeltmelerinden sonra yeniden doğrulandı (sistem yeniden kurulmadı, açık kalan temiz-seed sistem kullanıldı).
- `docker compose ps`: postgres, backend, frontend `Up 3 minutes (healthy)`.
- `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run test:e2e` → **4 geçti, 0 kaldı** (1 worker, 1.7 sn).
- ollama.md ilk arama adımları geçici bir Playwright betiğiyle (çalıştırıldıktan sonra silindi) `localhost:3000`'de denendi:
  - Öneri düğmesi "Şifremi unuttum, online işlemlere giremiyorum" tıklandı; ilk sonuç `1 | Online İşlemler parolanı sıfırlama | anlam | ...`. "anlam" etiketi görünür.
  - "Teknik detay" bulundu ve açıldı.
  - "Arama kayıtları" sekmesi sayacı arttı (16'ya ulaştı; önceki e2e koşuları dahil).
  - Sınırlama: betikte "Arama kayıtları" sekmesinde ilk satırın "Temsilci paneli" kanalı ayrıca doğrulanmadı; bu E5 testi tarafından doğrulanıyor (geçti). Ekran görüntüsü alınmadı.
- Sistem açık bırakıldı: http://localhost:3000.

STATUS: DONE
