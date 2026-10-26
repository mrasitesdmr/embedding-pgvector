CREATE TABLE search_events (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id      bigint      NOT NULL,
    created_at        timestamptz NOT NULL,
    channel           text        NULL,
    query             text        NOT NULL,
    mode              text        NOT NULL,
    filter_strategy   text        NULL,
    ef_search         integer     NULL,
    result_count      integer     NOT NULL,
    top_chunk_id      bigint      NULL,
    top_chunk_heading text        NULL,
    total_ms          bigint      NOT NULL,
    CONSTRAINT fk_search_events_workspaces FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT ck_search_events_mode CHECK (mode IN ('KEYWORD', 'VECTOR', 'HYBRID')),
    CONSTRAINT ck_search_events_filter_strategy CHECK (filter_strategy IS NULL OR filter_strategy IN ('POST_FILTER', 'ITERATIVE_SCAN')),
    CONSTRAINT ck_search_events_result_count CHECK (result_count >= 0)
);

CREATE INDEX idx_search_events_workspace_id_created_at ON search_events (workspace_id, created_at DESC, id DESC);
