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

CREATE INDEX idx_sources_workspace_id ON sources (workspace_id);
CREATE UNIQUE INDEX uk_sources_workspace_id_name ON sources (workspace_id, lower(name));

ALTER SEQUENCE sources_id_seq OWNED BY sources.id;
