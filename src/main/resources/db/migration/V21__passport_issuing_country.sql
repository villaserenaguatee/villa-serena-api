-- V21: Passport issuing country. Schema only.
ALTER TABLE guests ADD COLUMN passport_issuing_country VARCHAR(2);
ALTER TABLE guests ADD CONSTRAINT ck_issuing_country_format CHECK (passport_issuing_country IS NULL OR passport_issuing_country ~ '^[A-Z]{2}$');
