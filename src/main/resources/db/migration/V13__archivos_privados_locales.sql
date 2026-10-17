CREATE TABLE archivos_privados_locales (
 id UUID PRIMARY KEY,
 clave VARCHAR(255) NOT NULL UNIQUE,
 mime VARCHAR(40) NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),
 contenido BYTEA NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 5242880),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
