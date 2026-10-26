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

CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX idx_chunks_tsv ON chunks USING gin (tsv);
CREATE INDEX idx_chunks_workspace_id ON chunks (workspace_id);
CREATE INDEX idx_chunks_source_id_chunk_no ON chunks (source_id, chunk_no);

ALTER SEQUENCE chunks_id_seq OWNED BY chunks.id;
