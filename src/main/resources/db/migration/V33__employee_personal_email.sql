-- Additive compatibility: existing email remains the unique staff login.
-- No backfill: existing IDs, credentials, permissions and sessions are unchanged.
ALTER TABLE employees ADD COLUMN personal_email VARCHAR(150);
COMMENT ON COLUMN employees.personal_email IS 'Optional personal contact; never used for authentication';
