TRUNCATE search_events, ingestion_jobs, chunks, sources, workspaces RESTART IDENTITY CASCADE;

INSERT INTO workspaces (id, name, created_at) VALUES (42, 'Belgelerim', now());
