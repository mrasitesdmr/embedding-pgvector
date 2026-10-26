# sql-optimization-patterns — detaylı pattern'ler ve çalışılmış örnekler

Tüm örnekler PostgreSQL (pgvector) ve Spring Data JPA içindir. Tip ve isimlendirme kuralları `postgres-patterns`'teki gibidir (`bigint` identity, `text`, `timestamptz`, `numeric`, `vector(<boyut>)`). Her index değişikliği bir Flyway migration'ıdır (`flyway-migrations`).

## Optimizasyon Pattern'leri

### Pattern 1: N+1 Sorgularını Ortadan Kaldırma

**Sorun:** Bir liste çekilir, sonra her eleman için ayrı sorgu atılır (1 + N sorgu).

```java
List<Workspace> workspaces = workspaceRepository.findAll();
for (Workspace workspace : workspaces) {
    workspace.getSources().size();
}
```

Lazy `sources` koleksiyonu her çalışma alanı için ayrı bir `SELECT` tetikler.

**Çözüm 1 — JOIN FETCH** (tek sorgu):

```java
@Query("select distinct w from Workspace w left join fetch w.sources")
List<Workspace> findAllWithSources();
```

**Çözüm 2 — `@EntityGraph`** (derived query'ye fetch planı ekler):

```java
@EntityGraph(attributePaths = "sources")
List<Workspace> findByNameStartingWith(String prefix);
```

**Çözüm 3 — batch fetch** (koleksiyonlar `IN (...)` ile toplu yüklenir; sayfalı sorgularda tercih edilir, çünkü koleksiyon `JOIN FETCH` + `Pageable` bellekte sayfalamaya düşer):

```properties
spring.jpa.properties.hibernate.default_batch_fetch_size=50
```

**Çözüm 4 — DTO projection** (sadece gereken kolonlar, entity yok):

```java
@Query("""
        select new com.embedding.pgvector.source.domain.dto.SourceSummaryResponse(s.id, s.name, count(c.id))
        from Source s left join Chunk c on c.source = s
        where s.workspaceId = :workspaceId
        group by s.id, s.name
        """)
List<SourceSummaryResponse> findSummaries(@Param("workspaceId") long workspaceId);
```

Karşılık gelen SQL:

```sql
SELECT s.id, s.name, count(c.id) AS chunk_count
FROM sources s
LEFT JOIN chunks c ON c.source_id = s.id
WHERE s.workspace_id = 42
GROUP BY s.id, s.name;
```

Arama sonucu da N+1'e açıktır: her hit için kaynağın adını ayrı sorguyla çekmek yerine benzerlik sorgusu `sources` ile join'lenir ve `sourceName` aynı satırda döner (`spring-boot-engineer/references/data.md`).

### Pattern 2: Pagination'ı Optimize Etme

**Kötü: Büyük tablolarda OFFSET**

```sql
-- OFFSET atlanan 100 000 satırı yine de okur
SELECT id, position, content FROM chunks
WHERE source_id = 301
ORDER BY id
LIMIT 20 OFFSET 100000;
```

**İyi: Keyset (cursor) pagination**

```sql
-- Son görülen satırın id değerinden devam et
SELECT id, position, content FROM chunks
WHERE source_id = 301 AND id > 98765
ORDER BY id
LIMIT 20;

-- Zaman sıralı listede eşitliği id ile kır
SELECT id, name, created_at FROM sources
WHERE workspace_id = 42
  AND (created_at, id) < ('2026-10-23 06:00:12+03', 301)
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Gerekli index
CREATE INDEX idx_sources_workspace_id_created_at_id ON sources (workspace_id, created_at DESC, id DESC);
```

- `id` ikinci sıralama kolonu olarak eşit `created_at` değerlerinde sıralamayı belirleyici yapar.
- Spring Data'da `Window<T>` + `ScrollPosition.keyset()` veya `@Query` ile aynı koşul.
- Toplam sayı gerekmiyorsa `Page` yerine `Slice` (count sorgusu yok), bkz. `backend-conventions` bölüm 6.
- Vektör aramasında sayfalama yapılmaz: `LIMIT k` ile ilk k sonuç döner. "Daha fazla göster" gerekiyorsa `k` büyütülerek sorgu yeniden çalıştırılır; OFFSET ANN index'iyle tutarsız sonuç verir.

### Pattern 3: Aggregate'leri Verimli Yazma

**COUNT sorguları:**

```sql
-- Büyük tabloda tam sayım tüm satırları (veya tüm index'i) tarar
SELECT count(*) FROM chunks;

-- Yaklaşık sayı yeterliyse istatistiklerden oku (lab'daki "412.860 parça" gibi gösterimler için)
SELECT reltuples::bigint AS estimate
FROM pg_class
WHERE oid = 'chunks'::regclass;

-- Filtreli sayım + filtre kolonunda index → Index Only Scan mümkün
SELECT count(*) FROM chunks
WHERE workspace_id = 42;
```

Index Only Scan için tablonun visibility map'i güncel olmalı (autovacuum çalışıyor olmalı).

**WHERE ve HAVING:**

```sql
-- Satır filtresi WHERE'de: gruplamadan önce satır sayısını azaltır
SELECT workspace_id, count(*) AS zero_hit_searches
FROM search_logs
WHERE hit_count = 0
GROUP BY workspace_id
HAVING count(*) > 10;
```

- `WHERE` satırları gruplamadan önce eler; `HAVING` sadece aggregate üzerindeki koşullar içindir. Aggregate olmayan bir koşulu `HAVING`'e yazmak gereksiz satırları gruplar.
- Bu sorgu için `(workspace_id) WHERE hit_count = 0` partial index veya `(hit_count, workspace_id)` composite index değerlendirilir; karar `EXPLAIN (ANALYZE, BUFFERS)` ile verilir.

### Pattern 4: Subquery Optimizasyonu

**Correlated subquery → JOIN + GROUP BY:**

```sql
-- Her satır için alt sorgu çalışabilir
SELECT s.name,
       (SELECT count(*) FROM chunks c WHERE c.source_id = s.id) AS chunk_count
FROM sources s;

-- Tek geçişte JOIN + aggregate
SELECT s.name, count(c.id) AS chunk_count
FROM sources s
LEFT JOIN chunks c ON c.source_id = s.id
GROUP BY s.id, s.name;
```

PostgreSQL planlayıcısı bazı correlated subquery'leri kendisi dönüştürebilir; değişikliği her zaman `EXPLAIN ANALYZE` ile karşılaştır, varsayımla yapma.

**`IN` yerine `EXISTS`** (alt sorgu sadece varlık kontrolü yapıyorsa):

```sql
SELECT s.name
FROM sources s
WHERE EXISTS (SELECT 1 FROM chunks c WHERE c.source_id = s.id AND c.embedding_model <> 'bge-m3');
```

**Okunabilirlik için CTE (hybrid arama):**

```sql
WITH vector_hits AS (
    SELECT id, row_number() OVER (ORDER BY embedding <=> :queryEmbedding) AS rank
    FROM chunks
    WHERE workspace_id = :workspaceId
    ORDER BY embedding <=> :queryEmbedding
    LIMIT 10
),
keyword_hits AS (
    SELECT id, row_number() OVER (ORDER BY ts_rank(content_tsv, query) DESC) AS rank
    FROM chunks, websearch_to_tsquery('simple', :queryText) AS query
    WHERE workspace_id = :workspaceId AND content_tsv @@ query
    ORDER BY ts_rank(content_tsv, query) DESC
    LIMIT 10
)
SELECT coalesce(v.id, k.id) AS chunk_id,
       coalesce(1.0 / (60 + v.rank), 0) + coalesce(1.0 / (60 + k.rank), 0) AS rrf_score
FROM vector_hits v
FULL OUTER JOIN keyword_hits k ON k.id = v.id
ORDER BY rrf_score DESC
LIMIT 10;
```

- PostgreSQL 12+ CTE'leri varsayılan olarak inline eder; yan etkisi olmayan CTE performans bariyeri değildir. Bariyer gerekiyorsa (ör. önce filtreleyip sonra kesin vektör araması yapmak için) açıkça `AS MATERIALIZED` yazılır.
- Her kol kendi `LIMIT`'iyle sınırlıdır; vektör kolunun `ORDER BY ... LIMIT` biçimi HNSW index'ini kullanabilmesi için korunur.
- RRF'yi SQL'de mi Java'da mı yapacağın plan kararıdır; Java'da yapmak saf bir sınıfla unit test edilebilirliği artırır.

### Pattern 5: Batch İşlemler

**Batch INSERT (uygulama tarafı, içe alma):**

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

- `GenerationType.IDENTITY` kullanan entity'lerde Hibernate insert'leri batch'leyemez (her insert'te üretilen ID'yi okumak zorundadır). Toplu insert yapılan tablolar (`chunks`) için `@SequenceGenerator` (+ `allocationSize`) kullanılır.
- Çok büyük toplu yüklemede (on binlerce parça) JPA yerine `JdbcClient` / `JdbcTemplate.batchUpdate` daha verimlidir.

**Batch INSERT (SQL):**

```sql
-- Tek tek insert yerine çok satırlı VALUES
INSERT INTO chunks (source_id, workspace_id, position, content, embedding, embedding_model) VALUES
    (301, 42, 0, 'Şifre sıfırlama adımları...', :embedding0, 'bge-m3'),
    (301, 42, 1, 'Online işlemlere giriş...', :embedding1, 'bge-m3'),
    (301, 42, 2, 'Fatura itirazı...', :embedding2, 'bge-m3');
```

`COPY ... FROM '/dosya'` sunucu dosya sistemini okur ve süper kullanıcı / `pg_read_server_files` yetkisi ister; uygulamadan toplu yükleme için PostgreSQL JDBC `CopyManager` (`COPY ... FROM STDIN`) kullanılır. Çok büyük ilk yüklemede HNSW index'ini yüklemeden **sonra** kurmak, satır satır index güncellemekten hızlıdır.

**Batch UPDATE:**

```sql
-- Döngüde tek tek update yerine tek sorgu
UPDATE ingestion_jobs SET stage = 'FAILED', error_code = 'INGESTION_INTERRUPTED', finished_at = now() WHERE id = ANY(:ids);

-- Satır başına farklı değer: VALUES listesiyle join
UPDATE sources s
SET chunk_count = v.chunk_count
FROM (VALUES (301, 742), (302, 12)) AS v(id, chunk_count)
WHERE s.id = v.id;
```

JPA'da toplu update `@Modifying @Query` ile yapılır; persistence context'i bayatlatacağı için `clearAutomatically = true` (bkz. `spring-jpa-testing/references/transactional-tests.md`).

## İleri Teknikler

### Materialized View'lar

Maliyetli rapor sorgularını önceden hesapla (ör. çalışma alanı başına arama istatistikleri).

```sql
CREATE MATERIALIZED VIEW workspace_search_stats AS
SELECT workspace_id,
       mode,
       count(*) AS search_count,
       count(*) FILTER (WHERE hit_count = 0) AS zero_hit_count,
       avg(duration_ms) AS avg_duration_ms
FROM search_logs
GROUP BY workspace_id, mode;

-- REFRESH ... CONCURRENTLY bir UNIQUE index gerektirir
CREATE UNIQUE INDEX uk_workspace_search_stats_workspace_id_mode ON workspace_search_stats (workspace_id, mode);

REFRESH MATERIALIZED VIEW CONCURRENTLY workspace_search_stats;
```

- View tanımı repeatable migration'dadır (`R__create_workspace_search_stats_view.sql`).
- Yenileme zamanlanmış bir işle (`@Scheduled`) yapılır; veri yenileme aralığı kadar bayattır.

### Partitioning

Çok büyük, zamana göre büyüyen tablolar için (ör. arama logları). Küçük tablolarda fayda sağlamaz, karmaşıklık ekler. Vektör tablosunu (`chunks`) kiracıya göre partition'lamak, filtre çok seçici olduğunda partition başına küçük HNSW index'i sağlar; karar ölçümle verilir (`postgres-patterns` "Filtreli arama").

```sql
CREATE TABLE search_logs (
    id           bigint      GENERATED ALWAYS AS IDENTITY,
    workspace_id bigint      NOT NULL,
    mode         text        NOT NULL,
    hit_count    integer     NOT NULL,
    duration_ms  integer     NOT NULL,
    searched_at  timestamptz NOT NULL,
    PRIMARY KEY (id, searched_at)
) PARTITION BY RANGE (searched_at);

CREATE TABLE search_logs_2026_q3 PARTITION OF search_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');

CREATE TABLE search_logs_2026_q4 PARTITION OF search_logs
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Sorgu sadece ilgili partition'ı tarar (partition pruning)
SELECT count(*) FROM search_logs
WHERE searched_at >= '2026-10-01' AND searched_at < '2026-11-01';
```

- Partitioned tabloda PK/unique constraint partition anahtarını içermelidir.
- Aralık koşullarında `BETWEEN` yerine yarı açık aralık (`>=` / `<`) kullan; `timestamptz` ile gün sonu kaçırılmaz.
- Arama logu sorgu metnini saklamaz (kullanıcı verisi); sadece mod, sonuç sayısı ve süre.

### Planlayıcı Ayarları

PostgreSQL'de MySQL'deki gibi index hint'i (`USE INDEX`) yoktur. Planlayıcı yanlış plan seçiyorsa önce istatistikleri ve sorguyu düzelt:

```sql
-- İstatistikleri güncelle
ANALYZE chunks;

-- Çarpık dağılımlı kolonda (birkaç büyük kiracı, çok sayıda küçük kiracı) daha ayrıntılı istatistik
ALTER TABLE chunks ALTER COLUMN workspace_id SET STATISTICS 500;
ANALYZE chunks;
```

`SET enable_nestloop = off`, `SET enable_indexscan = off` gibi ayarlar sadece **teşhis** içindir (aynı oturumda `EXPLAIN` ile alternatif planı görmek ya da recall ölçerken exact search'ü zorlamak için, `SET LOCAL` ile transaction içinde); uygulama bağlantılarında veya global config'te kalıcı olarak kullanılmaz.
