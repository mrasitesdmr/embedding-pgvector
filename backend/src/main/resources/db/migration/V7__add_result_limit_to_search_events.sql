ALTER TABLE search_events ADD COLUMN result_limit integer NULL;

ALTER TABLE search_events
    ADD CONSTRAINT ck_search_events_result_limit CHECK (result_limit IS NULL OR result_limit BETWEEN 1 AND 50);
