# Ollama: Lokal Embedding Modeli Kullanımı ve Deneyler

Kurulum ve başlatma için `README.md`. Bu dosya, Ollama ve backend API'si ile yapılabilecek terminal deneylerini (embedding, yükleme, arama, psql) toplar.

> Projede kullanılan model `bge-m3`'tür (TASK-001'de onaylandı, vektör boyutu 1024). Backend varsayılan olarak bu modeli kullanır. Başka bir modelle çalıştırmak için bkz. bölüm 6, "Modeli ya da Ollama adresini değiştirme".

## Nasıl çalışıyor?

```
terminal ──────────────────────────HTTP──▶ Ollama sunucusu (Mac, localhost:11434) ──▶ bge-m3

terminal ──HTTP──▶ backend (container, localhost:8080) ──HTTP──▶ Ollama sunucusu (Mac, host.docker.internal:11434)
                          │
                          └──JDBC──▶ PostgreSQL + pgvector (container, localhost:5432)
```

- `ollama serve`, bilgisayarda `11434` portunda bir HTTP sunucusu açar.
- `ollama pull` modeli bilgisayara indirir (`~/.ollama/models` altına).
- `/api/embed` isteği gönderilen metni modele verir. Yanıtın `embeddings` alanında metnin vektörü (sayı dizisi) döner.
- Backend, Docker Compose ile PostgreSQL'in (pgvector eklentili) yanında container'da çalışır. Ollama container'a girmez, Mac'te kalır; container ona `host.docker.internal` üzerinden ulaşır.
- Backend, yüklenen dosyaları parçalara böler, her parçayı `/api/embed` ile vektöre çevirir ve PostgreSQL'e yazar. Arama sorgusunu da aynı modelle vektöre çevirip veritabanında karşılaştırır.
- `POST /api/embeddings` endpoint'i ise sadece bir metni embed edip döner; hiçbir şey kaydedilmez.

## 1-3. Kurulum, sunucu ve model indirme
Ollama kurulumu, `ollama serve` ile başlatma ve `ollama pull bge-m3` adımları `README.md` içindedir ("Ollama ve model"). Faydalı komutlar:

```bash
ollama list      # indirilen modeller (bge-m3:latest görünür)
ollama show bge-m3   # "embedding length" satırı vektör boyutudur (bge-m3 → 1024)
ollama ps        # şu an belleğe yüklenmiş modeller
ollama rm <model-adı>   # artık gerekmeyen modeli sil
```

### Neden bge-m3?

| Model | Vektör boyutu | Yaklaşık boyut | Türkçe |
|-------|---------------|----------------|--------|
| **`bge-m3` (seçilen)** | 1024 | 1,2 GB | Çok dilli, Türkçesi iyi |
| `qwen3-embedding:0.6b` | 1024 | 640 MB | Çok dilli, destekler |
| `embeddinggemma` | 768 | 620 MB | Çok dilli, destekler |
| `nomic-embed-text` | 768 | 275 MB | Ağırlıkla İngilizce |

`bge-m3` Türkçede güçlü. Soru ve doküman aynı biçimde gönderilir, ön ek ya da talimat cümlesi gerekmez. Uzun metinleri de (yaklaşık 8.000 token) işler. Bedeli: diğerlerinden biraz daha fazla bellek kullanır ve biraz daha yavaştır.

Not: Etiketsiz `qwen3-embedding` adı 4,7 GB'lık büyük sürümü indirir. Küçük sürüm için `qwen3-embedding:0.6b` yazılır.

## 4. Backend olmadan doğrudan Ollama'ya istek atma

```bash
# Tek bir metni embed et, yanıtın ilk 400 karakterini göster
curl -s http://localhost:11434/api/embed \
  -d '{"model":"bge-m3","input":"Parolamı unuttum, hesabım kilitlendi"}' \
  | head -c 400; echo
```

Örnek yanıt (kısaltılmış):

```json
{"model":"bge-m3","embeddings":[[0.0016,0.0492,-0.0634, ...]],"total_duration":32280083,"load_duration":4315708,"prompt_eval_count":14}
```

- `embeddings`: her girdi metni için bir vektör.
- `total_duration`: nanosaniye cinsinden toplam süre.
- İlk istek, model belleğe yüklendiği için daha yavaştır. Sonraki istekler hızlıdır.

Vektörün kaç sayıdan oluştuğunu say:

```bash
curl -s http://localhost:11434/api/embed \
  -d '{"model":"bge-m3","input":"Yıllık izin kaç gün?"}' \
  | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["embeddings"][0]))'
# → 1024
```

Aynı istekte birden fazla metin (toplu embed):

```bash
curl -s http://localhost:11434/api/embed \
  -d '{"model":"bge-m3","input":["Yıllık izin kaç gün?","Masraf beyanı kaç gün içinde yapılır?"]}' \
  | python3 -c 'import sys,json; e=json.load(sys.stdin)["embeddings"]; print(len(e), "vektör,", len(e[0]), "boyut")'
# → 2 vektör, 1024 boyut
```

## 5. Backend üzerinden kullanma

Backend ve PostgreSQL Docker Compose ile çalışır (başlatma: `README.md`, "Tam sistem"). Ollama Mac'te açık ve `bge-m3` indirilmiş olmalı. Docker Desktop açık olmalı (`docker info` hata vermemeli). Tüm komutlar repo kökünden çalıştırılır. Proje API sözleşmesi: `docs/api/openapi.yaml`.

### 5.1 Sistemi başlatma ve izleme

Başlatma, durdurma ve sıfırlama komutları `README.md` içindedir ("Tam sistem"). Burada kullanılan kısa hâller:

```bash
docker compose -f deployment/docker-compose.yml ps
# → backend    embedding-pgvector-backend    Up 36 seconds (healthy)
# → frontend   embedding-pgvector-frontend   Up 30 seconds (healthy)
# → pgadmin    dpage/pgadmin4:9.8            Up 35 seconds (healthy)
# → postgres   pgvector/pgvector:0.8.1-pg18  Up 41 seconds (healthy)

docker compose -f deployment/docker-compose.yml logs --tail=100 backend
curl -s localhost:8080/actuator/health; echo
# → {"groups":["liveness","readiness"],"status":"UP"}
```

Portlar: site `localhost:3000`, backend `localhost:8080`, PostgreSQL `localhost:5432` ve pgAdmin `localhost:5050` (ikisi sadece bu bilgisayardan erişilir). Swagger UI: `http://localhost:8080/swagger-ui.html`.

### 5.2 Başlangıç durumunu kontrol etme

Sistem boş açılır: başlangıç (demo) verisi yoktur. Tek çalışma alanı "Belgelerim" (id 42) hazır gelir, belge ve parça sayısı 0'dır:

```bash
curl -s localhost:8080/api/workspaces; echo
# → {"content":[{"workspaceId":42,"name":"Belgelerim","sourceCount":0,"chunkCount":0,"embeddingModels":[]}],"page":0,"size":50,"totalElements":1,"totalPages":1}
```

Eski sürümden kalan bir veritabanı varsa backend ilk açılışta içindekileri otomatik siler (bkz. `README.md`, "Önceki sürümden güncelleme").

### 5.3 Örnek belgeyi yükleme ve iş durumunu izleme

Örnek belge: `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf`. Yeni kaynak oluşturup dosyayı yükle (`name` zorunlu, kaynağın adı olur):

```bash
curl -si -F name=Atlaskap-Lojistik-Surec-El-Kitabi -F file=@ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf \
  localhost:8080/api/workspaces/42/sources
# → HTTP/1.1 202
# → Location: /api/workspaces/42/ingestion-jobs/1
# → {"jobId":1,"workspaceId":42,"sourceId":10001,"version":null,"fileName":"Atlaskap-Lojistik-Surec-El-Kitabi.pdf","stage":"QUEUED","progress":0.0,"embeddedChunks":0,"totalChunks":null,"errorCode":null,...,"statusUrl":"/api/workspaces/42/ingestion-jobs/1"}
```

`202` dosyanın kabul edildiği, işin arka planda başladığı anlamına gelir. İş aşamaları: `QUEUED → PARSING → CHUNKING → EMBEDDING → WRITING → DONE` (hata olursa `FAILED` ve `errorCode`). `Location` adresini sorgula:

```bash
until curl -s localhost:8080/api/workspaces/42/ingestion-jobs/1 | grep -qE '"stage":"(DONE|FAILED)"'; do sleep 1; done
curl -s localhost:8080/api/workspaces/42/ingestion-jobs/1; echo
# → {"jobId":1,"workspaceId":42,"sourceId":10001,"version":null,"fileName":"Atlaskap-Lojistik-Surec-El-Kitabi.pdf","stage":"DONE","progress":1.0,"embeddedChunks":24,"totalChunks":24,"errorCode":null,...}
```

Kaynağı ve parçalarını gör:

```bash
curl -s localhost:8080/api/workspaces/42/sources/10001 \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["name"], d["kind"], d["chunkCount"], d["status"])'
# → Atlaskap-Lojistik-Surec-El-Kitabi PDF 24 READY

curl -s "localhost:8080/api/workspaces/42/sources/10001/chunks?size=2" \
  | python3 -c 'import sys,json; [print(c["chunkNo"], c["heading"][:50], c["embeddingModel"]) for c in json.load(sys.stdin)["content"]]'
# → 0 s. 1 · Atlaskap Lojistik A.Ş. · Kurumsal Süreçler  bge-m3@1024
# → 1 s. 2 · Atlaskap Lojistik A.Ş. · Kurumsal Süreçler  bge-m3@1024
```

Aynı kaynağın yeni sürümünü yüklemek için (parçalar silinip yenileri yazılır):

```bash
curl -si -F file=@ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf -F version=2.5 \
  localhost:8080/api/workspaces/42/sources/10001/versions
# → HTTP/1.1 202
```

Başlangıca dönmek için `docker compose -f deployment/docker-compose.yml down -v` ile veritabanını sıfırla.

Reddedilen dosyalar (iş açılmaz, hiçbir şey değişmez):

```bash
printf 'PK\003\004' > /tmp/rapor.docx
curl -s -w '\n%{http_code}\n' -F file=@/tmp/rapor.docx -F version=9 localhost:8080/api/workspaces/42/sources/10001/versions
# → {"detail":"Unsupported file type: docx",...,"status":415,...,"errorCode":"UNSUPPORTED_FILE_TYPE"}
# → 415

(printf '%%PDF-1.7\n'; head -c 26214400 /dev/zero) > /tmp/buyuk.pdf
curl -s -w '\n%{http_code}\n' -F file=@/tmp/buyuk.pdf -F version=9 localhost:8080/api/workspaces/42/sources/10001/versions
# → {"detail":"Uploaded file exceeds the maximum size",...,"status":413,...,"errorCode":"FILE_TOO_LARGE"}
# → 413
```

Sınır 20 MB'tır. Kabul edilen türler: PDF, TXT/MD, CSV.

### 5.4 Üç modda arama ve `efSearch` / filtre denemesi

Endpoint: `POST /api/workspaces/{id}/search`. Gövde: `query` (zorunlu), `mode` (`KEYWORD`, `VECTOR`, `HYBRID`; varsayılan `HYBRID`), `filterStrategy` (`ITERATIVE_SCAN` ya da `POST_FILTER`), `efSearch` (10-1000, varsayılan 40), `limit` (1-50, varsayılan 10), `channel` (isteğe bağlı etiket; sadece arama kaydına yazılır, sonuçları **sınırlamaz**), `explain` (`true` ise vektör kolunun EXPLAIN planını da döner).

Sonuçları tek satır özet olarak görmek için bir yardımcı tanımla (bu terminal oturumu için; başlık çok uzun olduğundan sayfa numarası yeterlidir):

```bash
search() {
  curl -s localhost:8080/api/workspaces/42/search -H 'Content-Type: application/json' -d "$1" | python3 -c '
import sys,json
d=json.load(sys.stdin)
print(d["mode"], d["filterStrategy"], "ef=%s" % d["efSearch"], "dönen=%s" % d["returnedCount"], "keyword=%s" % d["keywordCount"], "vector=%s" % d["vectorCount"], "vectorShort=%s" % d["vectorShort"], "%s ms" % d["timings"]["totalMs"])
for h in d["hits"]:
    print(h["rank"], h["heading"][:6], "|", h["content"][:40].replace("\n"," "), "|", ",".join(h["matchedBy"]))'
}
```

**Sorgu 1: İzin** (soruda belgedeki "kıdem", "iş günü" gibi kelimeler yok, anlam aynı). Aynı sorguyu üç modda çalıştır:

```bash
for m in KEYWORD VECTOR HYBRID; do echo "-- $m"; search "{\"query\":\"Yıllık izin kaç gün?\",\"mode\":\"$m\",\"limit\":3}"; done
# → -- KEYWORD
# → KEYWORD None ef=None dönen=0 keyword=0 vector=None vectorShort=False 6 ms
# → -- VECTOR
# → VECTOR ITERATIVE_SCAN ef=40 dönen=3 keyword=None vector=3 vectorShort=False 36 ms
# → 1 s. 7 · | 5. Yıllık izin ve diğer izinler Yıllık i | SEMANTIC
# → 2 s. 23  | 21. İşten ayrılma süreci İstifa eden çal | SEMANTIC
# → 3 s. 15  | 13. Araç filosu ve sürücü kuralları Şirk | SEMANTIC
# → -- HYBRID
# → HYBRID ITERATIVE_SCAN ef=40 dönen=3 keyword=0 vector=3 vectorShort=False 20 ms
# → (HYBRID de aynı üç sonucu verir; ilk sonuç yine s. 7)
```

KEYWORD modu sorudaki **bütün** kelimelerin (kök hâliyle) aynı parçada geçmesini ister; "kaç" gibi bir kelime belgede bu parçada yoksa hiçbir parça tutmaz, bu yüzden 0 sonuç. VECTOR anlamı yakalar ve izin bölümünü ilk sıraya koyar. Bu, anahtar kelime aramasının sınırıdır.

**Sorgu 2: Masraf.**

```bash
search '{"query":"Masraf beyanı kaç gün içinde yapılır?","mode":"VECTOR","limit":3}'
# → VECTOR ITERATIVE_SCAN ef=40 dönen=3 keyword=None vector=3 vectorShort=False 23 ms
# → 1 s. 8 · | 6. Masraf ve harcırah İş için yapılan ha | SEMANTIC
# → 2 s. 14  | 12. Faturalama ve tahsilat Faturalar e-f | SEMANTIC
# → 3 s. 23  | 21. İşten ayrılma süreci İstifa eden çal | SEMANTIC
```

İlk parça "harcama tarihinden itibaren 30 gün içinde… beyan" cümlesini içerir; yanıt oradadır.

**Sorgu 3: Parola.** Burada kelime kolu da tutar, HYBRID ikisini birleştirir (RRF):

```bash
for m in KEYWORD HYBRID; do echo "-- $m"; search "{\"query\":\"parolamı unuttum hesabım kilitlendi\",\"mode\":\"$m\",\"limit\":3}"; done
# → -- KEYWORD
# → KEYWORD None ef=None dönen=1 keyword=1 vector=None vectorShort=False 20 ms
# → 1 s. 16  | 14. Bilgi teknolojileri destek BT destek | KEYWORD
# → -- HYBRID
# → HYBRID ITERATIVE_SCAN ef=40 dönen=3 keyword=1 vector=3 vectorShort=False 29 ms
# → 1 s. 16  | 14. Bilgi teknolojileri destek BT destek | KEYWORD,SEMANTIC
# → 2 s. 17  | 15. Bilgi güvenliği ve kişisel verilerin | SEMANTIC
# → 3 s. 18  | 16. İş sağlığı ve güvenliği İş güvenliği | SEMANTIC
```

`matchedBy` hangi kolun parçayı bulduğunu gösterir; iki kolda da çıkan parça HYBRID'de yukarı çıkar.

**Belgede olmayan konu.** Vektör kolu her zaman en yakın parçaları döndürür, ama mesafe büyüktür (ilgili sorularda yaklaşık 0,3-0,45; burada 0,68). Bu yüzden "kuantum fiziği" gibi sorular "Sık aranan" listesine girmez:

```bash
search '{"query":"kuantum fiziği","mode":"VECTOR","limit":2}'
# → VECTOR ITERATIVE_SCAN ef=40 dönen=2 ... (mesafe ≈ 0.68, alakasız parçalar)
curl -s localhost:8080/api/workspaces/42/search-events/popular-queries; echo
# → {"queries":[{"query":"parolamı unuttum hesabım kilitlendi","searchCount":2,...},{"query":"Yıllık izin kaç gün?","searchCount":2,...},{"query":"Masraf beyanı kaç gün içinde yapılır?","searchCount":1,...}]}
# (sayılar, bu bölümde kaç kez aradığına göre değişir; "kuantum fiziği" listede yoktur)
```

**`filterStrategy` ve `efSearch` denemesi.** Çalışma alanı filtresi (`workspace_id = 42`) HNSW taramasından **sonra** uygulanırsa (`POST_FILTER`), index'ten gelen `efSearch` adayın bir kısmı filtreye takılır ve istenen sonuç sayısı dolmayabilir (kısa liste). Bu, birden fazla çalışma alanının parçası aynı index'te durduğunda görünür; tek alanlı küçük bir veritabanında her aday 42'ye ait olduğundan fark çıkmaz. `ITERATIVE_SCAN` filtre sonrası yetmezse index taramasına devam eder:

```bash
Q='"query":"Yıllık izin kaç gün?","mode":"VECTOR","limit":10'
search "{$Q,\"filterStrategy\":\"POST_FILTER\",\"efSearch\":40}"
search "{$Q,\"filterStrategy\":\"ITERATIVE_SCAN\",\"efSearch\":40}"
```

Sorgu planını görmek için `"explain":true` ekle. Plan `vectorPlan` alanındadır:

```bash
curl -s localhost:8080/api/workspaces/42/search -H 'Content-Type: application/json' \
  -d "{$Q,\"filterStrategy\":\"POST_FILTER\",\"efSearch\":40,\"explain\":true}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["vectorPlan"])' | cut -c1-110
```

Planda `Index Scan using idx_chunks_embedding` HNSW index'inin kullanıldığını, varsa `Rows Removed by Filter` ise index'ten gelen adaylardan kaçının `workspace_id = 42` filtresine takıldığını gösterir. (Küçük tablolarda PostgreSQL index yerine sıralı tarama da seçebilir.)

Yapılan aramalar kaydedilir (sorgu metni ve sonuç sayısı; log'a yazılmaz):

```bash
curl -s "localhost:8080/api/workspaces/42/search-events?size=1"; echo
# → {"content":[{"searchEventId":10,"createdAt":"...","channel":null,"query":"Yıllık izin kaç gün?","mode":"VECTOR","filterStrategy":"POST_FILTER","limit":10,"resultCount":10,"topChunkId":31,"topChunkHeading":"s. 7 · Atlaskap Lojistik A.Ş. · ...","totalMs":20}],"page":0,"size":1,"totalElements":10,...}
# (en son yapılan arama; `explain` isteği de bir arama kaydı bırakır, bu yüzden sayı ve id sende farklı olabilir)
```

### 5.5 `psql` ile tabloları ve index'leri görme

```bash
# Etkileşimli psql (çıkış: \q)
docker compose -f deployment/docker-compose.yml exec postgres psql -U embedding -d embedding_pgvector
```

Tek komut olarak da çalıştırabilirsin (`-c`):

```bash
docker compose -f deployment/docker-compose.yml exec postgres psql -U embedding -d embedding_pgvector -c '\dt'
# →  public | chunks                | table | embedding
# →  public | flyway_schema_history | table | embedding
# →  public | ingestion_jobs        | table | embedding
# →  public | search_events         | table | embedding
# →  public | sources               | table | embedding
# →  public | workspaces            | table | embedding

docker compose -f deployment/docker-compose.yml exec postgres psql -U embedding -d embedding_pgvector -c '\d chunks'
# → ... embedding | vector(1024) | not null
# → ... tsv       | tsvector     | generated always as (to_tsvector('turkish', ...)) stored
# → Indexes:
# →     "chunks_pkey" PRIMARY KEY, btree (id)
# →     "idx_chunks_embedding" hnsw (embedding vector_cosine_ops) WITH (m='16', ef_construction='64')
# →     "idx_chunks_source_id_chunk_no" btree (source_id, chunk_no)
# →     "idx_chunks_tsv" gin (tsv)
# →     "idx_chunks_workspace_id" btree (workspace_id)
```

- `idx_chunks_embedding`: vektör aramasının HNSW index'i (kosinüs uzaklığı).
- `idx_chunks_tsv`: kelime aramasının GIN index'i; `tsv` kolonu başlık+içerikten Türkçe köklerle otomatik üretilir.
- `idx_chunks_workspace_id`: çalışma alanı filtresi.

Örnek sorgular (5.3'teki yüklemeden sonra):

```bash
P() { docker compose -f deployment/docker-compose.yml exec -T postgres psql -U embedding -d embedding_pgvector "$@"; }

P -c "select extname, extversion from pg_extension where extname = 'vector'"
# →  extname | extversion
# →  vector  | 0.8.1

P -c "select count(*) as workspaces from workspaces" -c "select count(*) as chunks from chunks"
# →  workspaces = 1
# →  chunks     = 24    (yüklemeden önce 0)

P -c "select id, name, version, chunk_count, status from sources where workspace_id = 42 order by id"
# →  10001 | Atlaskap-Lojistik-Surec-El-Kitabi | 2.5 | 24 | READY    (5.3'te sürüm 2.5 yüklendiyse; yüklemediysen sürüm boş)

P -c "select id, stage, embedded_chunks from ingestion_jobs"
# →  1 | DONE | 24
# →  2 | DONE | 24    (sadece 5.3'te yeni sürüm yüklediysen)
```

Veriyi sıfırlamak istersen tabloları elle boşaltma; sistemi `down -v` ile durdurup yeniden başlat. Backend açıkken `TRUNCATE ... RESTART IDENTITY` çalıştırmak parça numaralarını bozar.

### 5.6 Siteyi açma ve ilk arama

**a) Docker ile (önerilen):** `README.md`'deki `up --build -d` komutundan sonra dört servis (`postgres`, `backend`, `frontend`, `pgadmin`) `healthy` olmalı.

```bash
curl -s -o /dev/null -w "%{http_code}\n" localhost:3000
# → 200
# Site, /api isteklerini backend'e iletir (aynı adres, CORS yok)
curl -s localhost:3000/api/workspaces/42/sources | head -c 120; echo
# → {"content":[{"sourceId":10001,"workspaceId":42,"name":"Atlaskap-Lojistik-Surec-El-Kitabi","kind":"PDF",...
```

Tarayıcıda `http://localhost:3000` aç. Belge yoksa "Henüz belge yüklenmedi" görünür:

1. **Belge yükle**'ye tıkla, `ornek-dokuman/Atlaskap-Lojistik-Surec-El-Kitabi.pdf` dosyasını seç, **Belgeyi yükle**'ye bas. "Belge hazır" görününce **Soru sor**'a tıkla.
2. **"Yıllık izin kaç gün?"** diye sor. Sonuçta "Yıllık izin ve diğer izinler" parçası (s. 7) başta çıkar ve **anlam** etiketi taşır (kelime eşleşmesi değil, anlam benzerliğiyle bulunmuştur).
3. **Teknik detay**'ı aç: mod, filtre stratejisi, süreler ve vektör sorgu planı görünür.
4. **Arama kayıtları** sekmesine geç: az önceki arama en üstte, kanalı "Temsilci paneli" olarak listelenir; sekmedeki sayı bir artar.
5. Birkaç soru daha sor; arama sayfasında "Sık aranan" listesi belirir.

Aynı aramanın API karşılığı (sitenin yaptığı istek):

```bash
curl -s -X POST localhost:3000/api/workspaces/42/search -H 'Content-Type: application/json' \
  -d '{"query":"Yıllık izin kaç gün?","mode":"HYBRID","limit":3}' \
  | python3 -c "import sys,json; [print(x['rank'],x['heading'][:6],'|',x['matchedBy']) for x in json.load(sys.stdin)['hits']]"
# → 1 s. 7 · | ['SEMANTIC']
# → 2 s. 23  | ['SEMANTIC']
# → 3 s. 15  | ['SEMANTIC']
```

**b) Geliştirme modu (hot reload):** adımlar `README.md`'de ("Local geliştirme"). Site `http://localhost:5173`'te açılır; aramayı yukarıdaki adımlarla yap.

**Uçtan uca tarayıcı testi** (sistem açıkken):

```bash
cd frontend && npx playwright install chromium
E2E_BASE_URL=http://localhost:3000 npm run test:e2e
```

> **Uyarı:** Bu test her koşunun başında çalışma alanı 42'deki belgeleri, parçaları, yükleme işlerini ve arama kayıtlarını siler. Kendi yüklediğin belgelerin olduğu bir sistemde çalıştırma.

## 6. Sık karşılaşılan hatalar

| Belirti | Sebep | Çözüm |
|---------|-------|-------|
| `curl: (7) Failed to connect to localhost port 11434` | Ollama sunucusu kapalı | `brew services start ollama` ya da `ollama serve` |
| `could not connect to a running Ollama instance` | Ollama sunucusu kapalı | Aynı çözüm |
| `{"error":"model \"bge-m3\" not found, try pulling it first"}` | Model indirilmemiş ya da adı yanlış yazılmış | `ollama pull bge-m3`, sonra `ollama list` ile adı kontrol et |
| `Error: listen tcp 127.0.0.1:11434: bind: address already in use` | Ollama sunucusu zaten çalışıyor (servis olarak ya da başka bir terminalde `ollama serve` ile) | `lsof -i :11434` ile portu kullananı bul; ikinci bir sunucu başlatma |
| İlk istek çok yavaş | Model belleğe yükleniyor | Normal; sonraki istekler hızlanır |
| `docker info` hata verir ya da Docker daemon'a bağlanılamaz | Docker Desktop kapalı | Docker Desktop'ı aç, `docker info` çalışınca tekrar dene |
| Compose 5432 portunu bağlayamaz (port zaten kullanımda) | Lokal bir PostgreSQL ya da başka bir container 5432'yi kullanıyor | `lsof -i :5432` ile bul ve durdur |
| Compose 8080 portunu bağlayamaz (port zaten kullanımda) | Lokal `./mvnw spring-boot:run` ya da başka bir uygulama 8080'de | `lsof -i :8080` ile bul ve durdur; lokal backend ile container backend aynı anda çalışmaz |
| Compose 3000 portunu bağlayamaz (port zaten kullanımda) | Başka bir uygulama (ör. başka bir dev server) 3000'de | `lsof -i :3000` ile bul ve durdur |
| Site açılıyor ama "Sunucuya ulaşılamıyor" | Backend kapalı ya da `unhealthy`; nginx `/api` isteğini iletemiyor (502) | `docker compose -f deployment/docker-compose.yml ps` ve `logs --tail=100 backend`; backend `healthy` olunca sayfayı yenile |
| Arama yapınca "Embedding servisine ulaşılamıyor" | Ollama kapalı (`EMBEDDING_UNAVAILABLE`) | Ollama'yı başlat, `curl http://localhost:11434` ile kontrol et; sonraki arama başarılı olur |
| Backend 503 döner, `"errorCode":"EMBEDDING_UNAVAILABLE"` | Backend Ollama'ya ulaşamıyor: sunucu kapalı, cevap vermiyor ya da `OLLAMA_BASE_URL` yanlış. Container'dan Ollama'ya `host.docker.internal:11434` ile gidilir | Ollama'yı başlat ve `curl http://localhost:11434` ile kontrol et. Backend'i yeniden başlatmaya gerek yok; Ollama açılınca sonraki istek başarılı olur |
| Backend 503 döner, `"errorCode":"EMBEDDING_MODEL_NOT_FOUND"` | `EMBEDDING_MODEL`'deki model Ollama'da yok | `ollama pull bge-m3` (ya da `EMBEDDING_MODEL`'deki ad), sonra `ollama list` |
| Backend 409 döner, `"errorCode":"EMBEDDING_MODEL_MISMATCH"` | Çalışma alanındaki parçalar başka bir modelle (ya da boyutla) embed edilmiş; yapılandırılan `EMBEDDING_MODEL@EMBEDDING_DIMENSIONS` ile uyuşmuyor. Vektörler farklı modeller arasında karşılaştırılamaz | Eski modele dön ya da `down -v` ile veritabanını sıfırlayıp yeni modelle yeniden yükle. KEYWORD modu bu hatadan etkilenmez |
| Backend 409 döner, `"errorCode":"INGESTION_IN_PROGRESS"` | Aynı kaynak için bir yükleme işi hâlâ çalışıyor | `ingestion-jobs/{id}` ile işin `DONE` ya da `FAILED` olmasını bekle, sonra yeniden yükle |
| Backend 415 döner, `UNSUPPORTED_FILE_TYPE` / `UNSUPPORTED_FILE_ENCODING` | Dosya PDF/TXT/MD/CSV değil, uzantısı içeriğiyle uyuşmuyor ya da metin UTF-8 değil | Desteklenen bir dosya yükle; metin dosyasını UTF-8 olarak kaydet |
| Backend 413 döner, `"errorCode":"FILE_TOO_LARGE"` | Dosya 20 MB'tan büyük | Dosyayı küçült ya da böl |
| Backend 400 döner, `"errorCode":"VALIDATION_FAILED"` | İstekteki bir alan geçersiz (boş sorgu, `efSearch` 10-1000 dışında, `limit` 1-50 dışında, boş dosya vb.) | `violations` alanındaki `field` ve mesaja göre düzelt |
| Belge yüklendi ama `FAILED` kaldı (`EMBEDDING_UNAVAILABLE`) | Yükleme sırasında Ollama kapalıydı | Ollama'yı başlat; sitede "Yeniden dene" ile aynı dosyayı yeniden yükle |
| Backend container `unhealthy` | Veritabanına bağlanamıyor ya da açılış hatası | `docker compose -f deployment/docker-compose.yml logs --tail=100 backend` |
| `./mvnw verify` Testcontainers (Docker) hatası verir | Testler gerçek bir PostgreSQL container'ı kullanır; Docker kapalı | Docker Desktop'ı aç |

### Modeli ya da Ollama adresini değiştirme

Model adı, boyut ve adresler koda gömülü değildir, ortam değişkeniyle değişir. Docker'da değerler `deployment/docker-compose.yml` içindeki `backend` servisinde düz yazılıdır (dosyayı düzenleyip `up -d` ile yeniden başlat). Local çalıştırmada komut satırından verilir:

| Ortam değişkeni | Varsayılan | Ne yapar |
|-----------------|------------|----------|
| `EMBEDDING_MODEL` | `bge-m3` | Ollama'ya gönderilen model adı |
| `EMBEDDING_DIMENSIONS` | `1024` | Beklenen vektör boyutu. Veritabanı kolonu `vector(1024)`'tür; farklı boyutlu model için şema da değişmelidir |
| `OLLAMA_BASE_URL` | `http://localhost:11434` (Docker'da `http://host.docker.internal:11434`) | Ollama sunucusunun adresi |
| `POSTGRES_PASSWORD` | `embedding-local` | PostgreSQL şifresi (kullanıcı `embedding`); local backend için ayar gerekmez |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/embedding_pgvector` | Backend'in bağlandığı veritabanı |

```bash
# Başka bir adresteki Ollama'yı local backend ile kullan
OLLAMA_BASE_URL=http://192.168.1.20:11434 ./mvnw spring-boot:run
```

Model ya da boyut değiştirince eski modelle embed edilmiş parçalar `EMBEDDING_MODEL_MISMATCH` verir; veritabanını `down -v` ile sıfırlayıp yeniden yükle.

### `/api/embeddings` ile tek metin embed etme

Veritabanına dokunmaz; bir metnin vektörünü döner (sözleşme: `docs/api/openapi.yaml`).

```bash
curl -s localhost:8080/api/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"text":"Yıllık izin kaç gün?"}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["embeddingModel"], d["dimensions"], len(d["embedding"]), d["durationMs"], "ms")'
# → bge-m3 1024 1024 18 ms
```

Boş metin, sadece boşluk ya da kırpıldıktan sonra 2.000 karakterden uzun metin reddedilir (Ollama'ya istek atılmaz):

```bash
curl -s localhost:8080/api/embeddings -H 'Content-Type: application/json' -d '{"text":"   "}'; echo
# → {"detail":"Request validation failed","instance":"/api/embeddings","status":400,"title":"Validation Failed","type":"https://embedding-pgvector.example/errors/validation-failed","errorCode":"VALIDATION_FAILED","violations":[{"message":"must not be blank","field":"text"}]}
```
