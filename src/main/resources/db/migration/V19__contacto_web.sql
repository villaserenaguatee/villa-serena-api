CREATE TABLE contacto_web (
 id uuid PRIMARY KEY,
 huella varchar(64) NOT NULL,
 nombre varchar(150) NOT NULL,
 correo varchar(150) NOT NULL,
 mensaje text NOT NULL CHECK(length(mensaje) BETWEEN 1 AND 5000),
 estado varchar(12) NOT NULL CHECK(estado IN ('ENVIANDO','ENVIADO','FALLIDO','INCIERTO')),
 intentos int NOT NULL DEFAULT 1,
 error_codigo varchar(40),
 creado_en timestamptz NOT NULL DEFAULT now(),
 actualizado_en timestamptz NOT NULL DEFAULT now(),
 enviado_en timestamptz
);
