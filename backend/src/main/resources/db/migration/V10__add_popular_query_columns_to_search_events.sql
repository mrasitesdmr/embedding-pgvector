ALTER TABLE search_events ADD COLUMN query_key text NOT NULL;

ALTER TABLE search_events ADD COLUMN keyword_count integer NULL;

ALTER TABLE search_events ADD COLUMN best_distance double precision NULL;

ALTER TABLE search_events ADD CONSTRAINT ck_search_events_keyword_count CHECK (keyword_count IS NULL OR keyword_count >= 0);

ALTER TABLE search_events ADD CONSTRAINT ck_search_events_best_distance CHECK (best_distance IS NULL OR best_distance >= 0);

CREATE INDEX idx_search_events_workspace_id_query_key ON search_events (workspace_id, query_key);
