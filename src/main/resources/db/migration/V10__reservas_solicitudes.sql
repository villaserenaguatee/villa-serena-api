CREATE TABLE reservas_solicitudes (
 alcance VARCHAR(100) NOT NULL,
 clave VARCHAR(100) NOT NULL,
 huella VARCHAR(64) NOT NULL,
 respuesta JSONB NOT NULL,
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(alcance,clave)
);
