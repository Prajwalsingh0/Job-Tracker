-- V4 (H2 twin). Same intent as the PostgreSQL migration; H2 spells "drop NOT NULL"
-- differently, which is the only divergence.

ALTER TABLE resumes ADD COLUMN storage_key VARCHAR(255);

ALTER TABLE resumes ALTER COLUMN file_data SET NULL;

CREATE INDEX idx_resumes_storage_key ON resumes (storage_key);
