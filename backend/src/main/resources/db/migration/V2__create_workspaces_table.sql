CREATE TABLE workspaces (
    id         bigint      PRIMARY KEY,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_workspaces_name CHECK (char_length(name) BETWEEN 1 AND 80)
);
