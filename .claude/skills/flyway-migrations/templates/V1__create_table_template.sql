-- V1__enable_vector_extension.sql
-- pgvector eklentisi ilk migration'da, vektör kolonu kullanan her tablodan önce açılır.
-- Düz postgres imajında eklenti yoktur; pgvector eklentili imaj gerekir (deployment/CLAUDE.md).

CREATE EXTENSION IF NOT EXISTS vector;


-- V2__create_workspaces_table.sql
-- Dosya adını değiştir: V<sıradaki numara>__create_<tablo>_table.sql (bir dosya = bir tablo)
-- Çalışma alanları seed verisiyle gelir (id'ler sabit: 42, 19 ...); bu yüzden PK identity değil.

CREATE TABLE workspaces (
    id         bigint      PRIMARY KEY,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_workspaces_name CHECK (char_length(name) BETWEEN 1 AND 80)
);


-- V3__create_sources_table.sql
-- Seed kaynaklarının id'leri sabittir (301, 302 ...); yeni kaynaklar sequence'ten 10001'den başlar.

CREATE SEQUENCE sources_id_seq START WITH 10001 INCREMENT BY 1;

CREATE TABLE sources (
    id           bigint      PRIMARY KEY,
    workspace_id bigint      NOT NULL,
    name         text        NOT NULL,
    kind         text        NOT NULL,
    version      text        NULL,
    file_name    text        NULL,
    status       text        NOT NULL,
    chunk_count  integer     NOT NULL DEFAULT 0,
    synced_at    timestamptz NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_sources_workspaces FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT ck_sources_name CHECK (char_length(name) BETWEEN 2 AND 80),
    CONSTRAINT ck_sources_kind CHECK (kind IN ('WEB_CRAWL', 'PDF', 'MARKDOWN', 'CSV', 'TICKET_EXPORT')),
    CONSTRAINT ck_sources_status CHECK (status IN ('INGESTING', 'READY', 'FAILED')),
    CONSTRAINT ck_sources_version CHECK (version IS NULL OR char_length(version) BETWEEN 1 AND 40),
    CONSTRAINT ck_sources_chunk_count CHECK (chunk_count >= 0)
);

-- Foreign key'ler her zaman indexlenir; PostgreSQL ve JPA bunu otomatik yapmaz
CREATE INDEX idx_sources_workspace_id ON sources (workspace_id);

-- Aynı çalışma alanında büyük/küçük harf duyarsız tekil kaynak adı
CREATE UNIQUE INDEX uk_sources_workspace_id_name ON sources (workspace_id, lower(name));

ALTER SEQUENCE sources_id_seq OWNED BY sources.id;


-- V4__create_chunks_table.sql
-- Sequence 50'şer artar: Hibernate pooled optimizer ile toplu insert'te her parça için ayrı nextval gerekmez.

CREATE SEQUENCE chunks_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE chunks (
    id              bigint       PRIMARY KEY,
    workspace_id    bigint       NOT NULL,
    source_id       bigint       NOT NULL,
    chunk_no        integer      NOT NULL,
    heading         text         NULL,
    content         text         NOT NULL,
    token_count     integer      NOT NULL,
    tsv             tsvector     GENERATED ALWAYS AS (
                        to_tsvector('turkish'::regconfig, coalesce(heading, '') || ' ' || content)) STORED,
    embedding       vector(1024) NOT NULL,
    embedding_model text         NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chunks_workspaces FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_chunks_sources FOREIGN KEY (source_id) REFERENCES sources (id) ON DELETE CASCADE,
    CONSTRAINT ck_chunks_chunk_no CHECK (chunk_no >= 0),
    CONSTRAINT ck_chunks_content CHECK (char_length(content) > 0),
    CONSTRAINT ck_chunks_token_count CHECK (token_count >= 0)
);

-- Vektör kolu: HNSW + cosine. Operatör sınıfı sorgudaki operatörle aynı aileden olmalı (<=> ↔ vector_cosine_ops).
-- Boş tabloda bu migration'da kurulabilir; dolu tabloya sonradan ekleniyorsa CONCURRENTLY ve ayrı dosya (SKILL.md).
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- Keyword kolu: Türkçe tam metin araması (başlık + içerik)
CREATE INDEX idx_chunks_tsv ON chunks USING gin (tsv);

-- workspace_id kopyası filtreli vektör aramasında join'i önler (çok kiracılı filtre)
CREATE INDEX idx_chunks_workspace_id ON chunks (workspace_id);
CREATE INDEX idx_chunks_source_id_chunk_no ON chunks (source_id, chunk_no);

ALTER SEQUENCE chunks_id_seq OWNED BY chunks.id;


-- V5__create_ingestion_jobs_table.sql

CREATE TABLE ingestion_jobs (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id    bigint           NOT NULL,
    source_id       bigint           NOT NULL,
    version         text             NULL,
    file_name       text             NOT NULL,
    file_size_bytes bigint           NOT NULL,
    stage           text             NOT NULL,
    progress        double precision NOT NULL DEFAULT 0,
    embedded_chunks integer          NOT NULL DEFAULT 0,
    total_chunks    integer          NULL,
    error_code      text             NULL,
    created_at      timestamptz      NOT NULL,
    started_at      timestamptz      NULL,
    finished_at     timestamptz      NULL,
    CONSTRAINT fk_ingestion_jobs_workspaces FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_ingestion_jobs_sources FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT ck_ingestion_jobs_stage CHECK (stage IN ('QUEUED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'WRITING', 'DONE', 'FAILED')),
    CONSTRAINT ck_ingestion_jobs_progress CHECK (progress BETWEEN 0 AND 1),
    CONSTRAINT ck_ingestion_jobs_file_size_bytes CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_ingestion_jobs_embedded_chunks CHECK (embedded_chunks >= 0)
);

CREATE INDEX idx_ingestion_jobs_workspace_id ON ingestion_jobs (workspace_id);
CREATE INDEX idx_ingestion_jobs_source_id ON ingestion_jobs (source_id);

-- Bir kaynağın aynı anda tek aktif işi olur; servisteki kontrolün son güvencesi (spring-transactions)
CREATE UNIQUE INDEX uk_ingestion_jobs_source_id_active ON ingestion_jobs (source_id)
    WHERE stage NOT IN ('DONE', 'FAILED');
