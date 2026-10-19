-- La nacionalidad es opcional para el MVP; se conservan los valores históricos.
ALTER TABLE huespedes ALTER COLUMN nacionalidad DROP NOT NULL;
ALTER TABLE huespedes_adicionales ALTER COLUMN nacionalidad DROP NOT NULL;
