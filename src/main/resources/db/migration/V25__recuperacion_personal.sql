CREATE TABLE personal_recuperaciones (
 id uuid PRIMARY KEY,
 correo_hash varchar(64) NOT NULL,
 empleado_id bigint REFERENCES empleados(id),
 token_hash varchar(64) UNIQUE,
 creado_en timestamptz NOT NULL,
 expira_en timestamptz NOT NULL,
 usado_en timestamptz,
 outbox_id bigint REFERENCES outbox(id)
);
CREATE INDEX personal_recuperaciones_correo ON personal_recuperaciones(correo_hash,creado_en);
ALTER TABLE empleados ADD COLUMN acceso_revocado_en timestamptz;
