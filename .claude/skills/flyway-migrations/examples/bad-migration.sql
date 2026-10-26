-- ❌ KÖTÜ — V4_add_column.sql (tek alt çizgi: Flyway bu dosyayı migration olarak tanımaz)

-- Dolu tabloya default'suz NOT NULL kolon: mevcut satırlar yüzünden fail olur
ALTER TABLE chunks ADD COLUMN token_count integer NOT NULL;

-- Kolonu doğrudan rename etmek: rolling deploy sırasında eski sürüm bozulur
ALTER TABLE sources RENAME COLUMN ingested_at TO ready_at;

-- Dolu tabloda CONCURRENTLY olmadan index: index bitene kadar yazmalar kilitlenir
-- (HNSW index'i milyonlarca satırda dakikalar sürer)
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);

-- Dev'e özel sahte veri migration'da: @Profile("dev") seeder'a ait
INSERT INTO sources (workspace_id, name, kind) VALUES (42, 'Deneme kaynağı', 'MARKDOWN');

-- Boyutsuz vector kolonu: index kurulamaz, farklı modelin vektörleri karışır
ALTER TABLE chunks ADD COLUMN embedding_v2 vector;

-- Proje tip kurallarına aykırı: UUID PK, varchar(255), timezone'suz timestamp
CREATE TABLE search_logs (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    query      varchar(255),
    created_at timestamp
);

-- Daha önce çalışmış V2'yi "düzeltmek" için düzenlemek de bu dosyaya dahildir:
-- checksum uyuşmaz ve tüm ortamlarda validate fail olur
