---
name: sql-optimization-patterns
description: SQL sorgu optimizasyonu, EXPLAIN / EXPLAIN ANALYZE ile query plan okuma (vektör index'i kullanılıyor mu dahil), index stratejileri, JOIN/subquery iyileştirme, N+1, batch işlemler ve pagination performansı. Yavaş bir sorguyu debug ederken, bir sorgunun (benzerlik araması dahil) index kullanıp kullanmadığını kontrol ederken, @Query/native query yazarken veya veritabanı yükünü azaltırken kullan.
license: MIT
metadata:
  source: https://github.com/wshobson/agents/tree/main/plugins/developer-essentials/skills/sql-optimization-patterns
---

# SQL Optimization Pattern'leri

Index isimleri `postgres-patterns`'teki kalıba uyar (`idx_<tablo>_<kolonlar>`). Sistematik optimizasyon, doğru index'leme ve query plan analizi ile yavaş sorguları hızlandır. Projenin veritabanı pgvector eklentili PostgreSQL'dir; tüm örnekler PostgreSQL içindir. Schema/veri tipi kararları ve pgvector index parametreleri (HNSW/IVFFlat, `ef_search`, iterative scan, recall ölçümü) için `postgres-patterns`, JPA/Hibernate tarafı (N+1, fetch join, projection) için `jpa-patterns`.

## İş akışı

```
- [ ] 1. Yavaş sorguyu bul (Hibernate SQL log'u, pg_stat_statements, trace'teki JDBC span'i)
- [ ] 2. Gerçek parametrelerle EXPLAIN (ANALYZE, BUFFERS) çalıştır (vektör sorgusunda gerçek bir sorgu embedding'iyle)
- [ ] 3. Darboğazı belirle (Seq Scan, yanlış join, sort, tahmin/gerçek satır farkı, filtre sonrası az satır)
- [ ] 4. Tek bir değişiklik yap (index, sorgu yeniden yazımı, projection, ef_search)
- [ ] 5. EXPLAIN'i tekrar çalıştır ve karşılaştır; vektör sorgusunda recall'u da ölç; iyileşme yoksa geri al
- [ ] 6. Index değişikliğini Flyway migration'ı olarak ekle
```

## 1. Query plan (EXPLAIN)

```sql
EXPLAIN SELECT * FROM sources WHERE workspace_id = 42;

EXPLAIN ANALYZE
SELECT * FROM sources WHERE workspace_id = 42;

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT s.name, c.position
FROM sources s
JOIN chunks c ON c.source_id = s.id
WHERE s.created_at > now() - interval '1 day';
```

Bakılacak metrikler:

- **Seq Scan**: Tam tablo taraması (büyük tablolarda genelde yavaş)
- **Index Scan**: Index kullanılıyor (iyi)
- **Index Only Scan**: Tabloya gitmeden sadece index (en iyi)
- **Nested Loop**: Küçük veri setlerinde uygun
- **Hash Join**: Büyük veri setlerinde iyi
- **Merge Join**: Sıralı veride iyi
- **Cost / Rows**: Tahmini maliyet ve satır sayısı
- **Actual Time / actual rows**: Gerçek süre ve satır; tahminle çok farklıysa istatistikler eski olabilir (`ANALYZE`)

### Vektör sorgusunun planı

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM chunks
WHERE workspace_id = 42
ORDER BY embedding <=> '[0.012,-0.034,...]'::vector
LIMIT 10;
```

- Beklenen: `Limit → Index Scan using idx_chunks_embedding` ve `Order By: (embedding <=> ...)`. `Seq Scan` + `Sort` görüyorsan index kullanılmıyordur: operatör ile index'in operatör sınıfı aynı aileden mi (`<=>` ↔ `vector_cosine_ops`), `ORDER BY` ham mesafe üzerinde mi (skor ifadesi değil), `LIMIT` var mı kontrol et.
- `Rows Removed by Filter` yüksek ve dönen satır `LIMIT`'ten azsa post-filter sorunudur: iterative scan ya da önce filtreleme (`postgres-patterns` "Filtreli arama").
- Küçük tabloda planlayıcının exact search (Seq Scan) seçmesi doğru olabilir; karar ölçümle verilir.

## 2. Index stratejileri

- **B-Tree**: Varsayılan; eşitlik ve aralık sorguları
- **Hash**: Sadece eşitlik (`=`)
- **GIN**: Full-text search (`tsvector`), array, JSONB
- **GiST**: Geometrik veri, full-text
- **BRIN**: Fiziksel sıralı çok büyük tablolar
- **HNSW / IVFFlat** (pgvector): Yaklaşık en yakın komşu (ANN) araması

```sql
CREATE INDEX idx_chunks_source_id ON chunks (source_id);
CREATE INDEX idx_sources_workspace_id_status ON sources (workspace_id, status);
CREATE UNIQUE INDEX uk_ingestion_jobs_source_id_active ON ingestion_jobs (source_id) WHERE stage NOT IN ('DONE', 'FAILED');
CREATE INDEX idx_sources_lower_name ON sources (lower(name));
CREATE INDEX idx_sources_workspace_id_covering ON sources (workspace_id) INCLUDE (name, status);
CREATE INDEX idx_chunks_content_tsv ON chunks USING gin (content_tsv);
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
```

## 3. Sorgu pattern'leri

**`SELECT *` kullanma** — sadece gereken kolonlar (JPA'da projection / DTO query). `chunks` tablosunda `SELECT *` her satır için 1024 float'lık embedding'i de taşır:
```sql
SELECT id, position, content FROM chunks WHERE source_id = 301;
```

**WHERE'de kolona fonksiyon uygulama index'i devre dışı bırakır** — ya expression index oluştur ya da veriyi normalize saklayıp düz karşılaştır:
```sql
CREATE INDEX idx_sources_lower_name ON sources (lower(name));
SELECT id FROM sources WHERE lower(name) = 'fatura sss';
```

**Vektör sorgusunda `ORDER BY` ham mesafe üzerindedir** — skor ifadesiyle sıralamak index'i devre dışı bırakır:
```sql
-- Kötü: index kullanılmaz
SELECT id, 1 - (embedding <=> :q) AS score FROM chunks ORDER BY score DESC LIMIT 10;
-- İyi
SELECT id, 1 - (embedding <=> :q) AS score FROM chunks ORDER BY embedding <=> :q LIMIT 10;
```

**JOIN'leri açık yaz**, filtreyi erken uygula:
```sql
SELECT s.name, c.content
FROM sources s
JOIN chunks c ON c.source_id = s.id
WHERE s.workspace_id = 42 AND s.status = 'READY';
```

## Sık tuzaklar

- **Aşırı index**: Her index INSERT/UPDATE/DELETE'i yavaşlatır; HNSW index'i içe alma sırasında yazma maliyetini belirgin artırır
- **Kullanılmayan index**: Yer kaplar, yazmayı yavaşlatır
- **Eksik index**: Tam tablo taraması
- **Örtük tip dönüşümü**: Index kullanımını engeller (`bigint` kolonu `text` ile karşılaştırma; vektör parametresi `vector`'e cast edilmeden metin olarak gönderilirse)
- **OR koşulları**: Index'i verimsiz kullanır; gerekirse `UNION ALL`
- **Baştan wildcard'lı LIKE**: `LIKE '%abc'` B-tree index kullanamaz (gerekirse `pg_trgm` + GIN ya da tam metin araması)
- **Büyük OFFSET**: `OFFSET 100000` tüm satırları okur; cursor (keyset) pagination kullan. Vektör aramasında OFFSET ile sayfalama yapılmaz
- **N+1**: Döngüde tek tek sorgu; JOIN FETCH / `@EntityGraph` / batch fetch (bkz. `jpa-patterns`)
- **Belleğe sığmayan vektör index'i**: HNSW index'i `shared_buffers`/RAM'e sığmazsa her sorgu diske gider; `pg_relation_size` ile izle, gerekirse `halfvec` (`postgres-patterns`)

## İzleme sorguları (PostgreSQL)

```sql
-- En yavaş sorgular (PG 13+ kolon adları)
SELECT query, calls, total_exec_time, mean_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Çok seq scan yiyen tablolar (eksik index adayı)
SELECT schemaname, relname, seq_scan, seq_tup_read, idx_scan,
       seq_tup_read / seq_scan AS avg_seq_tup_read
FROM pg_stat_user_tables
WHERE seq_scan > 0
ORDER BY seq_tup_read DESC
LIMIT 10;

-- Hiç kullanılmayan index'ler
SELECT schemaname, relname, indexrelname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY pg_relation_size(indexrelid) DESC;

-- Vektör index'inin boyutu
SELECT pg_size_pretty(pg_relation_size('idx_chunks_embedding'));
```

## Bakım

```sql
ANALYZE chunks;
VACUUM ANALYZE chunks;
REINDEX INDEX CONCURRENTLY idx_chunks_source_id;
```

`VACUUM FULL` tabloyu kilitler; production'da planlamadan çalıştırma. Toplu silme/yeniden embedding sonrası HNSW index'i şişebilir; `REINDEX INDEX CONCURRENTLY idx_chunks_embedding` planlı bir bakım işidir (uzun sürer, `maintenance_work_mem` gerektirir).

## Detaylı pattern'ler

Subquery → JOIN dönüşümleri, batch insert/update, materialized view, partitioning, cursor pagination ve aggregate optimizasyonu için [references/details.md](references/details.md).
