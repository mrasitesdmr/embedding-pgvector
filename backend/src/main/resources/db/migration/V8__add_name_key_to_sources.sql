ALTER TABLE sources ADD COLUMN name_key text NULL;

UPDATE sources SET name_key = lower(translate(normalize(name, NFC), 'İIı', 'iii'));

ALTER TABLE sources ALTER COLUMN name_key SET NOT NULL;

DROP INDEX uk_sources_workspace_id_name;

CREATE UNIQUE INDEX uk_sources_workspace_id_name_key ON sources (workspace_id, name_key);
