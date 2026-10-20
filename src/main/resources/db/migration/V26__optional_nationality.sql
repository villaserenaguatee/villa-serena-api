-- V26: Optional nationality. Schema only.
ALTER TABLE guests ALTER COLUMN nationality DROP NOT NULL;
ALTER TABLE additional_guests ALTER COLUMN nationality DROP NOT NULL;
