TASK: TASK-008
# Deploy raporu

## Sistem senaryoları (ana adım)

Ön durum: Docker çalışıyor, Ollama açık (`bge-m3`), tam sistem tester'ın bıraktığı veriyle açıktı. Soru 3 = A: yedek alınmadı. Komutlar repo kökünden çalıştırıldı; `C` = `docker compose -f deployment/docker-compose.yml`.

### Değişen dosyalar
- `ollama.md`: 5.4, 5.5, 5.6 ve `/api/embeddings` örnek çıktıları gerçek çıktıya göre düzeltildi (R4, aşağıda).
- `deployment/CLAUDE.md`: "Komutlar"a Ollama kapalı E2E yöntemi eklendi (R6).
- `README.md`, `frontend/nginx.conf`: bu adımda değişmedi (tur 1 değişiklikleri geçerli).

### Özet
| # | Sonuç |
|---|-------|
| P1 | GEÇTİ |
| P2 | GEÇTİ (3 kez: ilk deneme betik hatası, bkz. not) |
| P3 | GEÇTİ (iki kez: P2 sonrası ve son adımda) |
| C5a | GEÇTİ (ekran kısmında gözlem var, bkz. G1) |
| C5b | GEÇTİ |
| R1 | GEÇTİ |
| R2 | GEÇTİ |
| R3 | GEÇTİ |
| R4 | GEÇTİ, `ollama.md`'de 6 örnek çıktı düzeltildi |
| R5 | GEÇTİ (Query Tool ekranı elle koşulmadı, sorgular `psql` ile) |
| R6 | GEÇTİ |
| Son adım | GEÇTİ, sistem temiz kurulumla açık |

Not (betik hatası): ilk P2 denemem zsh'in `$C` değişkenini bölmemesi yüzünden hiçbir komut çalıştırmadan bitti; `down -v` uygulanmadı, veri kaybı yok. P2 bash betiğiyle yeniden koşuldu. Aynı hata R6'nın ilk denemesinde tekrarladı (override uygulanmadı, test kırıldı), betik bash'e çevrilip yeniden koşuldu. Uygulama hatası değil, benim komut hatamdı.

### P1: `down` / `up` sonrası veri kalır
Tester'ın bıraktığı sistemde (`sum(vector_dims)` sütunu `counts.sh`'tan; sütunlar: sources | chunks | ingestion_jobs | search_events | toplam boyut):
```
önce:   10 | 535 | 13 | 14 | 547840
$C down ; $C up -d   → healthy=4 after 18s
sonra:  10 | 535 | 13 | 14 | 547840
```
- Kaynaklar listesi (`GET /api/workspaces/42/sources`: id, ad, durum, parça sayısı) önce ve sonra `diff` ile aynı: `LIST_SAME` (10 kaynak, ör. `(10001, Atlaskap…, READY, 24)`, `(10003, Uzun belge, READY, 500)`).
- `POST /api/workspaces/42/search` HYBRID → `HTTP 200`.

### P2: `down -v` sonrası temiz
```
$C down -v                       → volume'lar silindi (pgdata, pgadmin-data)
$C up --build -d                 → up 12-14 sn'de döndü, healthy=4 after 25-26s (imaj katmanları önbellekte)
workspaces:   42|Belgelerim
sayılar:      0 | 0 | 0 | 0 | 0   (sources, chunks, ingestion_jobs, search_events, boyut)
flyway:       select count(*), max(version::int) from flyway_schema_history where success  → 10|10
vector:       0.8.1
```
- `http://localhost:3000/` tarayıcıda `/w/42/belge-yukle`'ye gider (Playwright `waitForURL`); `/w/42/arama` "Henüz belge yüklenmedi" gösterir.
- Not: ilk kurulumda (önbelleksiz imaj) süre bu değildir; Maven ve npm indirmeleri dakikalar sürebilir. Ölçülmedi.

### P3: Smoke
```
curl -sf localhost:8080/actuator/health        → {"groups":["liveness","readiness"],"status":"UP"}
curl -sf -o /dev/null -w "%{http_code}" localhost:3000   → 200
curl -s localhost:3000/api/workspaces/42/sources → {"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
curl -s localhost:5050/misc/ping               → PING
```

### C5a: ilk yüklemede backend yeniden başlatma
D11 belgesi (`python3` ile: 500 bölüm, 784 510 bayt, scratchpad `uzun.md`) `curl -F name="Uzun belge"` ile yüklendi (`202`, iş 1, kaynak 10001).
```
iş 1 EMBEDDING 0/500 görülünce:  $C restart backend
yeniden başlatma sırasında:      GET /api/workspaces/42/sources → HTTP 502 (3 örnek)
healthy sonrası iş 1:  stage FAILED, errorCode INGESTION_INTERRUPTED, embeddedChunks 96/500
kaynak 10001:          status FAILED, chunkCount 0, latestJob.errorCode INGESTION_INTERRUPTED
DB:                    select count(*) from chunks where source_id=10001 → 0
yeni sürüm (küçük .md, version=2) → 202; 4 sn sonra kaynak READY, chunkCount 1, version 2, iş DONE
```
- Ekran (Playwright, `/w/42/kaynaklar` açık, restart sırasında 0,5 sn aralıkla örnekleme, ikinci kaynak "Uzun belge 2"): önce satırda "Embed ediliyor · 0/500 parça", restart bitince "Son yükleme başarısız: iş yarıda kaldı (sunucu iş sürerken durdu); dosyayı tekrar yükle", durum "Başarısız". Beklenen metin birebir.
- G1 (gözlem, hata değil): açık sayfada restart sırasında (~10 sn) "Sunucuya ulaşılamıyor…" **görünmedi**. Backend durdurulup sayfa yenilenince 12 sn içinde değil, yaklaşık 30 sn bekleyince "Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et. Tekrar dene" görünüyor (`shouldRetryQuery` yeniden denemeleri bu süreyi uzatıyor). 502 → `NETWORK_ERROR` eşlemesi (B-03) çalışıyor. Plan "yeniden başlatma sırasında" mesajını bekliyordu; kısa kesintide sorgu yeniden denemede toparlandığı için mesaj çıkmıyor. Önem: düşük, istenen davranış olabilir. Sorumlu: frontend-coder, karar lead'in.

### C5b: sürüm yüklemede backend yeniden başlatma
```
"Kalıcılık notu" (kalicilik.md) → kaynak 10002, READY, chunkCount 1, parça id {2}
D11 belgesi yeni sürüm (version=2) → iş 4; CHUNKING → EMBEDDING 0/500 → $C restart backend
iş 4:     FAILED, INGESTION_INTERRUPTED, embeddedChunks 0/500
kaynak:   status READY, chunkCount 1, version null, fileName kalicilik.md (değişmedi)
DB:       chunks group by source_id → 10001|{1}, 10002|{2}  (id'ler aynı, yarım parça yok)
arama:    POST …/search {"query":"zeytinyağı notu","mode":"KEYWORD"} → returnedCount 2 (eski parça bulunuyor)
```

### R1: README
- "Gereksinimler": `docker info` hatasız; Ollama `bge-m3` (`/api/embed` 1024 boyut döndü); Java/Node sadece local geliştirme için.
- "Tam sistem": `up --build -d`, `ps` 4 servis healthy (18-26 sn), `down` veri kalır (P1), `down -v` sıfırlar (P2).
- "Ollama kapalıysa": R6'daki OD1-OD2 (aşağıda) README'deki cümleyi doğruladı: 503 `EMBEDDING_UNAVAILABLE`, kelime araması çalışır, Ollama geri gelince backend yeniden başlatmadan HYBRID `200`.
- "Güvenlik notu" ve "Embedding boyutu" (H5, B-02 notu) metni mevcut.

### R2: "Örnek belgeyle dene" (temiz sistemde, Playwright)
```
1 redirect -> http://localhost:3000/w/42/belge-yukle
2 "Henüz belge yüklenmedi": görünür
3 ad alanı boş → "Belgeyi yükle"
4 "Belge hazır" / "Atlaskap-Lojistik-Surec-El-Kitabi hazır · 24 parça"
Yıllık izin kaç gün?                    → 1. s. 7 (5. Yıllık izin ve diğer izinler), anlam
Masraf beyanı kaç gün içinde yapılır?   → 1. s. 8 (6. Masraf ve harcırah), anlam
parolamı unuttum hesabım kilitlendi     → 1. s. 16 (14. Bilgi teknolojileri destek), anlam + kelime
kuantum fiziği                          → 10 sonuç, alakasız (2. İşe alım…); veritabanında best_distance 0.68
Sık aranan: ["parolamı unuttum…","Masraf beyanı…","Yıllık izin kaç gün?"]   ("kuantum fiziği" yok)
```

### R3: Local geliştirme
```
$C down ; docker compose -f deployment/docker-compose.dev.yml up -d → dev healthy=2 (postgres, pgadmin)
cd backend && ./mvnw -q spring-boot:run  (arka planda) → localhost:8080/actuator/health UP
cd frontend && npm ci && npm run dev     (arka planda) → VITE v8.3.5 ready in 499 ms, localhost:5173 HTTP 200
E2E_COMPOSE_FILE=../deployment/docker-compose.dev.yml E2E_BASE_URL=http://localhost:5173 npm run test:e2e
  → 17 passed (1.5m), 2 skipped (ollama-down)
```
- Dev E2E, ikinci kez başlatılan dev veritabanında çalıştı (önceki oturumdan 1 kaynak vardı; global setup sildi). `document-flow` 16 test + 413 testi, hepsi geçti. Beklenen "16 test" yerine 17 çünkü tur 1'de 413 testi eklendi.
- İş sonunda backend ve Vite süreçleri durduruldu (8080 ve 5173 boş, `lsof` boş), `docker compose -f deployment/docker-compose.dev.yml down`.
- `npm ci` uyarısı: `fsevents` için "install-scripts" uyarısı (macOS; zararsız, hata değil).

### R4: `ollama.md` bölüm 4, 5.1-5.6, 6
Temiz sistemde belgedeki komutlar sırayla çalıştırıldı. Uyanlar: bölüm 4 (1024, "2 vektör, 1024 boyut"), 5.1 (`ps`, health), 5.2 (`/api/workspaces` birebir), 5.3 (202, `Location`, DONE 24/24, `Atlaskap… PDF 24 READY`, `bge-m3@1024`, 415, 413), 5.5 (`\dt`, `\d chunks` ve beş index birebir; `vector 0.8.1`, 24 parça), 5.6 (200, site üzerinden sonuçlar), 6 (`/api/embeddings` 400 gövdesi).

Belge hataları (devops düzeltti, `ollama.md`):
1. 5.4: sonuç satırlarında başlık kısaltması ve "..." yerine gerçek bölüm adları ve `s. 7 ·` biçimi (3 sorgu, ~10 satır).
2. 5.4 "Sık aranan": `searchCount` 3,3,2 yazıyordu; bu bölümdeki aramalarla 2,2,1; sıra parola, izin, masraf; "kuantum fiziği yoktur" notu eklendi.
3. 5.4 arama kayıtları örneği: `searchEventId` 9 / `HYBRID` parola yazıyordu; bölümün son araması (id 10, `POST_FILTER`, `totalElements` 10) ile değiştirildi; `explain` isteğinin de kayıt bıraktığı notu eklendi.
4. 5.5: 5.3'te sürüm 2.5 yüklendiği için `sources` satırı sürüm `2.5` ve `ingestion_jobs` ikinci satır (`2 | DONE | 24`) yazıyordu eksik; eklendi.
5. 5.6 API örneği: `s. 7 ·` biçimi.
6. 6: `/api/embeddings` 400 gövdesinde `violations` alan sırası (`message`, `field`).
Kod hatası yok.

### R5: pgAdmin
- Playwright: `http://localhost:5050` → `admin@example.com` / `admin-local` girişi, **Servers → embedding-pgvector** hazır, şifre `embedding-local` istendi, bağlandı, **Databases → embedding_pgvector** görünür.
- `psql` ile README sorguları: `select count(*) from chunks` → 24; `select id, embedding_model, vector_dims(embedding) from chunks limit 5` → `bge-m3@1024 | 1024` (5 satır). Altı tablo `\dt` listesinde (beşi iş tabloları + `flyway_schema_history`). Query Tool ekranında sorgu elle koşulmadı.

### R6: Ollama kapalı E2E
```
override (scratchpad ollama-down.override.yml): services.backend.environment.OLLAMA_BASE_URL: http://host.docker.internal:9
$C -f override up -d backend → backend healthy, printenv OLLAMA_BASE_URL = http://host.docker.internal:9
Ön veri: "Atlaskap…" PDF'i ve "Deneme notu" (curl ile yüklendi)
E2E_OLLAMA_DOWN=1 E2E_BASE_URL=http://localhost:3000 npx playwright test e2e/ollama-down.spec.ts
  ✓ givenOllamaDown_whenHybridSearch_thenOllamaMessageAndKeywordSearchStillWorks (899ms)
  ✓ givenOllamaDown_whenUploadNewVersionOfMarkdown_thenFailureReasonAndOldChunksKept (2.0s)
  2 passed
$C up -d backend → healthy, OLLAMA_BASE_URL = http://host.docker.internal:11434, HYBRID arama HTTP 200
```
`deployment/CLAUDE.md` "Komutlar"a bu yöntem eklendi.

### Son adım (D15): temiz kurulum
```
docker compose -f deployment/docker-compose.dev.yml down
$C down -v ; $C up --build -d → healthy=4 after 26s
backend | embedding-pgvector-backend | healthy ; frontend | … | healthy ; pgadmin dpage/pgadmin4:9.8 | healthy ; postgres pgvector/pgvector:0.8.1-pg18 | healthy
workspaces 42|Belgelerim ; sayılar 0|0|0|0|0 ; flyway 10|10
P3: health UP ; 3000 HTTP 200 ; sources content [] ; pgAdmin PING
```
Sistem temiz kurulumla `localhost:3000`'de açık, Ollama'ya dokunulmadı.

### Hata listesi
Yeni `NEEDS_FIX` yok. Gözlem G1 (düşük önem, frontend-coder; karar lead'in) yukarıda C5a'da. Not: scratchpad dosyaları (`uzun.md`, override, betikler) repoya girmedi.

## Düzeltme turu 1

### Değişen dosyalar
- `frontend/nginx.conf`: `/api/` içine `error_page 413 @file_too_large;`, yeni `location @file_too_large` (`default_type application/problem+json`, `return 413` ile Problem Details gövdesi).
- `README.md`: "Embedding boyutu" bölümü (1024 dışına çıkamaz, B-02 notu).
- `deployment/CLAUDE.md`: aynı not ve 413 davranışı.

### Ne değişti
413'te nginx artık `application/problem+json` döner: `type` = `https://embedding-pgvector.example/errors/file-too-large` (openapi.yaml `FileTooLarge` örneğiyle aynı), `title` "Content Too Large", `status` 413, `detail`, `instance` = istek yolu, `errorCode` `FILE_TOO_LARGE`.

### Komutlar ve çıktılar
- `docker compose -f deployment/docker-compose.yml up -d --build frontend` → frontend yeniden kuruldu, `Up (healthy)`. Backend ve veritabanı yeniden kurulmadı.
- 23 MB gövdeyle `curl -i -X POST -F file=@big.bin;filename=big.txt localhost:3000/api/workspaces/42/sources/10001/versions`:
  - `HTTP/1.1 413 Request Entity Too Large`, `Content-Type: application/problem+json`
  - gövde: `{"type":"https://embedding-pgvector.example/errors/file-too-large","title":"Content Too Large","status":413,"detail":"Uploaded file exceeds the maximum size","instance":"/api/workspaces/42/sources/10001/versions","errorCode":"FILE_TOO_LARGE"}`
- Playwright E2E koşulmadı (tester Tur 2'de koşacak).

### Hata durumu
- B-04 → DÜZELTİLDİ (tur 1)
- B-02 notu → README ve deployment/CLAUDE.md'ye eklendi.

## Düzeltme turu 2

### Değişen dosyalar
- `frontend/nginx.conf`: `@file_too_large` gövdesinden `"instance":"$uri"` alanı çıkarıldı (`instance` openapi.yaml'da zorunlu değil). `deployment/CLAUDE.md`'de 413 maddesi `instance`'tan söz etmediği için değişmedi.

### Ne değişti
Yolda `%22`, `%5C`, `%0A` olduğunda `$uri` JSON'a kaçışsız giriyordu ve gövde geçersiz JSON oluyordu. Alan çıkarılınca gövde sabit bir metin oldu.

### Komutlar ve çıktılar
- `docker compose -f deployment/docker-compose.yml up -d --build frontend`: frontend `Up (healthy)`.
- 23 MB gövdeyle `POST localhost:3000/api/workspaces/42/sources/{x%22y, x%5Cy, x%0Ay, normal}`: dördünde de `HTTP/1.1 413`, `Content-Type: application/problem+json`, gövde `json.load` ile ayrıştı, `errorCode` = `FILE_TOO_LARGE`.
- `cd frontend && npx playwright test e2e/document-flow.spec.ts -g EncodedQuote`: 1 passed.
- Temiz kurulum: `down -v && up --build -d`, 4 servis (backend, frontend, pgadmin, postgres) `healthy`. P3 smoke: health `UP`, `localhost:3000` → 200, sources `content: []`.

### Hata durumu
- B-05 → DÜZELTİLDİ (tur 2)

STATUS: DONE
