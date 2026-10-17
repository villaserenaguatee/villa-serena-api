-- No nationality inference or backfill of historical documents.
ALTER TABLE huespedes ADD COLUMN pais_emisor_pasaporte VARCHAR(2);
ALTER TABLE huespedes ADD CONSTRAINT ck_pais_emisor_formato CHECK (pais_emisor_pasaporte IS NULL OR pais_emisor_pasaporte ~ '^[A-Z]{2}$');
