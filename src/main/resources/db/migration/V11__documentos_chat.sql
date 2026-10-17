CREATE TABLE estancia_mensajes (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reserva_id BIGINT NOT NULL REFERENCES reservas(id),
 autor VARCHAR(10) NOT NULL CHECK(autor IN ('HUESPED','RECEPCION')),
 autor_id BIGINT NOT NULL,
 clave UUID NOT NULL,
 texto VARCHAR(2000) NOT NULL CHECK(length(trim(texto))>0),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(reserva_id,autor,autor_id,clave)
);
CREATE INDEX ix_estancia_mensajes_reserva ON estancia_mensajes(reserva_id,id);
CREATE TABLE checkin_documentos (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reserva_id BIGINT NOT NULL REFERENCES reservas(id),
 huesped_id BIGINT NOT NULL REFERENCES huespedes(id),
 clave UUID NOT NULL,
 nombre VARCHAR(150) NOT NULL,
 mime VARCHAR(40) NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),
 contenido BYTEA NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 5242880),
 estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','ACEPTADO','RECHAZADO')),
 motivo VARCHAR(500),
 revisado_por BIGINT REFERENCES empleados(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 revisado_en TIMESTAMPTZ,
 UNIQUE(reserva_id,huesped_id,clave)
);
CREATE INDEX ix_checkin_documentos_reserva ON checkin_documentos(reserva_id,id);
