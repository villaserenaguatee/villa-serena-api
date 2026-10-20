-- V8: Lost item returns. Schema only.
ALTER TABLE lost_items ADD COLUMN physical_status VARCHAR(15) NOT NULL DEFAULT 'EN_RESGUARDO'
 CHECK (physical_status IN ('EN_RESGUARDO','DEVUELTO'));
ALTER TABLE lost_items ADD COLUMN returned_at TIMESTAMPTZ;
ALTER TABLE lost_items ADD COLUMN returned_by BIGINT REFERENCES employees(id);
