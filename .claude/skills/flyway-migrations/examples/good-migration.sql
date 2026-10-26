-- ✅ İYİ — SKILL.md'deki sırayla aynı dosyalar; her dosya tek bir değişiklik yapar ve adı içeriğini anlatır.

-- V3__add_ingested_at_to_sources.sql
-- Nullable kolon: mevcut satırlar için güvenli, tablo yeniden yazılmaz
ALTER TABLE sources ADD COLUMN ingested_at timestamptz;

-- V5__add_chunks_embedding_hnsw_index.sql  (+ V5__add_chunks_embedding_hnsw_index.sql.conf → executeInTransaction=false)
-- Dolu tabloda index: CONCURRENTLY, kendi dosyasında tek başına; operatör sınıfı sorgudaki <=> ile aynı aile
CREATE INDEX CONCURRENTLY idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);

-- V6__add_ready_at_to_sources.sql
-- Rename (ingested_at → ready_at) yerine expand/contract'ın ilk adımı: yeni kolon + backfill
ALTER TABLE sources ADD COLUMN ready_at timestamptz;
UPDATE sources SET ready_at = ingested_at WHERE ready_at IS NULL AND ingested_at IS NOT NULL;
-- Tekrar backfill (V7) ve ingested_at'in DROP'u (V8), kod yeni kolonu okuduktan sonraki migration'lardadır.

-- V9__add_token_count_to_chunks.sql
-- Sabit default'lu NOT NULL kolon (PostgreSQL 11+ tabloyu yeniden yazmaz)
ALTER TABLE chunks ADD COLUMN token_count integer NOT NULL DEFAULT 0;
