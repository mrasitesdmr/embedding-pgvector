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
CREATE UNIQUE INDEX uk_ingestion_jobs_source_id_active ON ingestion_jobs (source_id)
    WHERE stage NOT IN ('DONE', 'FAILED');
