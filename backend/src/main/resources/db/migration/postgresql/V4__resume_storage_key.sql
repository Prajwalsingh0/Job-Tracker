-- V4: move resume documents out of the database.
--
-- Documents now live in a storage backend (local filesystem today) addressed by
-- storage_key. file_data becomes nullable so a one-time startup migration can move any
-- existing blobs across without losing data; the column is left in place until every
-- row has been migrated.

ALTER TABLE resumes ADD COLUMN storage_key VARCHAR(255);

ALTER TABLE resumes ALTER COLUMN file_data DROP NOT NULL;

CREATE INDEX idx_resumes_storage_key ON resumes (storage_key);
