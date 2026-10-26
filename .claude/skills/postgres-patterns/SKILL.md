---
name: postgres-patterns
description: PostgreSQL ve pgvector schema tasarımı, veri tipi seçimi (vector/halfvec dahil), index türleri (B-tree, composite, partial, covering, GIN, HNSW, IVFFlat), vektör benzerliği sorguları (mesafe operatörleri, k-NN, filtreli arama, iterative index scan, recall ölçümü), UPSERT, cursor pagination, kuyruk işleme (SKIP LOCKED) ve anti-pattern tespit sorguları. Tablo/kolon/index tasarlarken, Flyway migration'ında SQL yazarken, native query yazarken veya bir PostgreSQL sorgusunun neden yavaş olduğunu incelerken kullan.
license: MIT
metadata:
  origin: ECC
  source: https://github.com/affaan-m/ECC/tree/main/skills/postgres-patterns
  credit: Supabase Agent Skills (MIT)
---

# PostgreSQL Pattern'leri

Projenin veritabanı pgvector eklentili PostgreSQL'dir (lokal/test image: `pgvector/pgvector:<sürüm>-pg18`; düz `postgres` imajında `vector` eklentisi yoktur). Migration dosya kuralları için `flyway-migrations`, yavaş sorgu analizi için `sql-optimization-patterns`, JPA tarafı için `jpa-patterns`.

## İsimlendirme

- Tablo: çoğul, snake_case (`sources`, `chunks`). Kolon: snake_case (`created_at`, `source_id`).
- PK: `id`. FK: `<tekil_tablo>_id`.
- Index: `idx_<tablo>_<kolonlar>`, unique: `uk_<tablo>_<kolonlar>`, FK: `fk_<tablo>_<hedef>`, check: `ck_<tablo>_<kolon>`.
- Constraint ve index'ler Flyway migration'ında tanımlanır, JPA annotation'ında değil (`flyway-migrations`).

## Veri tipi seçimi

| Kullanım | Doğru tip | Kaçın |
|----------|-----------|-------|
| ID | `bigint GENERATED ALWAYS AS IDENTITY` | `int`, `serial`/`bigserial`, rastgele UUID PK |
| Dış dünyaya açılan ID | `uuid` (ayrı kolon) | sıralı ID'yi dışarı açmak |
| String | `text` (+ `CHECK` ile uzunluk) | `varchar(255)` alışkanlığı |
| Zaman | `timestamptz` | `timestamp` |
| Para | `numeric(12,2)` | `float`, `money` |
| Bayrak | `boolean` | `varchar`, `int` |
| Sabit küme | `text` + `CHECK (status IN (...))` | PostgreSQL `enum` (migration'ı zor) |
| Embedding | `vector(<boyut>)` (ör. `vector(1024)`), boyut her zaman yazılır | boyutsuz `vector`, `float8[]`, `jsonb` |
| Büyük embedding / bellek baskısı | `halfvec(<boyut>)` (yarım hassasiyet, yarı yer) | gereksiz yere `vector` ile 2000+ boyut |

## Index kopya kağıdı

| Sorgu pattern'i | Index türü | Örnek |
|-----------------|------------|-------|
| `WHERE col = value` | B-tree (varsayılan) | `CREATE INDEX idx ON t (col)` |
| `WHERE col > value` | B-tree | `CREATE INDEX idx ON t (col)` |
| `WHERE a = x AND b > y` | Composite | `CREATE INDEX idx ON t (a, b)` |
| `WHERE lower(col) = ...` | Expression | `CREATE INDEX idx ON t (lower(col))` |
| `WHERE jsonb @> '{}'` | GIN | `CREATE INDEX idx ON t USING gin (col)` |
| Full-text `tsv @@ query` | GIN | `CREATE INDEX idx ON t USING gin (col)` |
| Zaman serisi aralıkları | BRIN | `CREATE INDEX idx ON t USING brin (col)` |
| `ORDER BY embedding <=> :q LIMIT k` | HNSW (varsayılan seçim) | `CREATE INDEX idx ON t USING hnsw (embedding vector_cosine_ops)` |
| Çok büyük, sık yeniden kurulan vektör tablosu | IVFFlat | `CREATE INDEX idx ON t USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)` |

Her FK kolonuna index ekle (PostgreSQL otomatik eklemez).

## Sık pattern'ler

**Composite index sırası** — eşitlik kolonları önce, aralık kolonları sonra:
```sql
CREATE INDEX idx_sources_status_created_at ON sources (status, created_at);
```

**Büyük/küçük harf duyarsız tekillik** (aynı çalışma alanında aynı kaynak adı yok):
```sql
CREATE UNIQUE INDEX uk_sources_workspace_id_name ON sources (workspace_id, lower(name));
```

**Covering index:**
```sql
CREATE INDEX idx_sources_workspace_id ON sources (workspace_id) INCLUDE (name, status);
```

**Partial index:**
```sql
-- Bir kaynağın aynı anda tek aktif içe alma işi olur (bu projedeki gerçek index, V5)
CREATE UNIQUE INDEX uk_ingestion_jobs_source_id_active ON ingestion_jobs (source_id)
    WHERE stage NOT IN ('DONE', 'FAILED');
```

**UPSERT:**
```sql
-- Yeniden içe almada parça içeriği değiştiyse embedding de yenilenir
INSERT INTO chunks (source_id, workspace_id, position, content, embedding, embedding_model)
VALUES (:sourceId, :workspaceId, :position, :content, :embedding, :embeddingModel)
ON CONFLICT (source_id, position)
DO UPDATE SET content = EXCLUDED.content,
              embedding = EXCLUDED.embedding,
              embedding_model = EXCLUDED.embedding_model
WHERE chunks.content IS DISTINCT FROM EXCLUDED.content
   OR chunks.embedding_model IS DISTINCT FROM EXCLUDED.embedding_model;
```

**Cursor (keyset) pagination** (büyük tablolarda `OFFSET` yerine):
```sql
SELECT id, position, content FROM chunks WHERE source_id = :sourceId AND id > :lastId ORDER BY id LIMIT 20;

-- Zaman sıralı listede eşitlik durumunu id ile kır
SELECT id, name, created_at FROM sources
WHERE workspace_id = :workspaceId
  AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 20;
-- Gerekli index: CREATE INDEX idx_sources_workspace_id_created_at_id ON sources (workspace_id, created_at DESC, id DESC);
```

**Kuyruk işleme:**
```sql
-- Worker'ın sıradaki işi alması; aynı işi iki worker almaz
UPDATE ingestion_jobs SET stage = 'PARSING', started_at = now()
WHERE id = (
  SELECT id FROM ingestion_jobs WHERE stage = 'QUEUED'
  ORDER BY created_at LIMIT 1
  FOR UPDATE SKIP LOCKED
) RETURNING *;
-- Not: bu projede iş, oluşturan transaction commit edilince doğrudan executor'a verilir; kuyruk tablosu taranmaz.
-- Bu desen backend birden fazla instance'a çıkarsa ya da işler bir worker'a taşınırsa gerekir.
```

## pgvector

### Mesafe operatörleri ve operatör sınıfları

| Operatör | Mesafe | HNSW/IVFFlat operatör sınıfı | Not |
|----------|--------|------------------------------|-----|
| `<=>` | Cosine distance (`1 - cosine similarity`) | `vector_cosine_ops` | Projenin varsayılanı |
| `<#>` | Negatif inner product | `vector_ip_ops` | Değer negatiftir; benzerlik için `* -1` |
| `<->` | L2 (Euclidean) | `vector_l2_ops` | |
| `<+>` | L1 (Manhattan) | `vector_l1_ops` (sadece HNSW) | |

- Index sadece sorgudaki operatör index'in operatör sınıfıyla aynı ailedense kullanılır. `vector_cosine_ops` index'i varken `<->` ile sıralamak sessizce sequential scan yapar.
- Normalize edilmiş vektörlerde cosine, inner product ve L2 aynı sıralamayı verir; yine de proje genelinde tek bir operatör (`<=>`) kullanılır.
- Skor dönüşümü SQL'de tek yerde yapılır: `1 - (embedding <=> :q) AS score`. `ORDER BY` ifadesi ham mesafe üzerinde kalır (`ORDER BY embedding <=> :q`); skor ifadesiyle sıralamak index'i devre dışı bırakır.

### k-NN sorgusu

```sql
SELECT id, source_id, content, 1 - (embedding <=> :queryEmbedding) AS score
FROM chunks
WHERE workspace_id = :workspaceId
  AND embedding_model = :embeddingModel
ORDER BY embedding <=> :queryEmbedding
LIMIT :k;
```

- Sorgu vektörü ve satırlar aynı modelle üretilmiş olmalı (`embedding_model` filtresi); farklı modelin vektörleriyle mesafe anlamsızdır.
- Index yokken bu sorgu exact search'tür (her satıra bakar, recall %100). Küçük tabloda (on binlerce satır) bu yeterli olabilir; index kararı ölçümle verilir (`sql-optimization-patterns`).

### HNSW mı IVFFlat mı

| | HNSW | IVFFlat |
|---|---|---|
| Hız/recall dengesi | Daha iyi | Daha düşük |
| Kurulum süresi ve bellek | Yüksek | Düşük |
| Boş tabloda kurulabilir mi | Evet, veri geldikçe güncellenir | Hayır; kümeler veriden öğrenilir, veri yüklendikten **sonra** kurulur |
| Kurulum parametreleri | `m` (vars. 16), `ef_construction` (vars. 64) | `lists` (≈ satır/1000, 1M üstünde ≈ √satır) |
| Sorgu parametresi | `hnsw.ef_search` (vars. 40) | `ivfflat.probes` (vars. 1; başlangıç ≈ √lists) |

- Varsayılan seçim HNSW'dir. Sorgu parametresi transaction içinde `SET LOCAL` ile verilir; bağlantı havuzunda oturum geneline `SET` sızar:

```sql
BEGIN;
SET LOCAL hnsw.ef_search = 100;
SELECT ... ORDER BY embedding <=> :q LIMIT 10;
COMMIT;
```

- Index'lenebilir boyut sınırı: `vector` için 2000, `halfvec` için 4000. Daha büyük modelde ya boyut küçültülür ya da `halfvec` ifade index'i kullanılır: `CREATE INDEX ON chunks USING hnsw ((embedding::halfvec(3072)) halfvec_cosine_ops);` (sorgu da aynı cast'i yapmalı).
- Büyük tabloda index kurulumu: `maintenance_work_mem` index'in sığacağı kadar yüksek olmalı (yetmezse kurulum çok yavaşlar, log'da uyarı çıkar); `max_parallel_maintenance_workers` paralel kurulumu açar; dolu tabloda `CREATE INDEX CONCURRENTLY` ayrı migration'dadır (`flyway-migrations`).

### Filtreli arama ve iterative index scan

ANN index'i önce en yakın `ef_search` adayı bulur, `WHERE` filtresi **sonra** uygulanır (post-filter). Filtre seçiciyse (`workspace_id = 42` satırların %3'ü ise, 40 adaydan ≈1'i kalır) `LIMIT 10` deyip 1-2 sonuç alırsın. Çözümler, filtre seçiciliğine göre:

| Durum | Çözüm |
|-------|-------|
| Filtre orta seçici | pgvector 0.8+ iterative scan: `SET LOCAL hnsw.iterative_scan = relaxed_order;` yetmeyince taramaya devam eder; üst sınır `hnsw.max_scan_tuples` (vars. 20000). Sıra kesin olmalıysa `strict_order` |
| Filtre çok seçici (küçük kiracı) | Önce filtrele, sonra kesin ara: B-tree index'li `workspace_id` ile satırları daralt, exact search yap (planner'ı zorlamak için CTE `MATERIALIZED` ya da partial index) |
| Az sayıda sabit filtre değeri | Değer başına partial HNSW index: `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops) WHERE workspace_id = 42;` ya da partition |

- `relaxed_order` sonuçları kabaca sıralı döndürebilir; kesin sıra gerekiyorsa sorguyu `MATERIALIZED` CTE'ye alıp dışta yeniden `ORDER BY` yap.

### Recall'u ölçmek

ANN biraz kaçırmayı kabul eder; ne kadar kaçırdığı ölçülür, tahmin edilmez. Aynı sorgu bir kez index'le, bir kez exact search ile çalıştırılır; recall@k = ortak sonuç sayısı / k:

```sql
BEGIN;
SET LOCAL enable_indexscan = off;   -- exact search (doğru cevap)
SELECT id FROM chunks WHERE workspace_id = 42 ORDER BY embedding <=> :q LIMIT 10;
COMMIT;
```

- Recall ve gecikme birlikte izlenir; `ef_search`/`probes` artırmak recall'u yükseltir, gecikmeyi artırır. Index'in belleğe sığıp sığmadığı `pg_relation_size('idx_chunks_embedding')` ile kontrol edilir.

### Hybrid arama (keyword + vector)

İki kol ayrı sorgulanır: keyword kolu `content_tsv @@ websearch_to_tsquery('simple', :query)` + `ts_rank`, vector kolu `<=>`. Sonuçlar puanla değil **sırayla** birleştirilir (Reciprocal Rank Fusion, `k = 60`): `score = Σ 1 / (k + rank)`. İki kolun puanları farklı ölçeklerde olduğu için toplanmaz.

## Anti-pattern tespiti

```sql
-- Index'i olmayan foreign key'ler
SELECT conrelid::regclass, a.attname
FROM pg_constraint c
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
WHERE c.contype = 'f'
  AND NOT EXISTS (
    SELECT 1 FROM pg_index i
    WHERE i.indrelid = c.conrelid AND a.attnum = ANY(i.indkey)
  );

-- Yavaş sorgular (pg_stat_statements extension'ı gerekir)
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC;

-- Tablo şişmesi (bloat)
SELECT relname, n_dead_tup, last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;
```

## Bağlantı ve timeout

- Uygulama tarafı: HikariCP `maximum-pool-size` küçük tut (varsayılan 10); PostgreSQL `max_connections` ile uyumlu.
- Sunucu tarafı: `idle_in_transaction_session_timeout` ve `statement_timeout` ayarlı olsun (ör. 30s).
- Uygulama kullanıcısı schema sahibi olmasın; sadece gereken yetkiler.
