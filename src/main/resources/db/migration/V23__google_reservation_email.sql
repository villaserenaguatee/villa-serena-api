-- V23: Google reservation email. Schema only.
ALTER TABLE reservation_email_verifications ALTER COLUMN outbox_id DROP NOT NULL;
ALTER TABLE reservation_email_verifications ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'CODIGO';
ALTER TABLE reservation_email_verifications ADD CONSTRAINT ck_reservation_email_source CHECK (source IN ('CODIGO','GOOGLE'));
