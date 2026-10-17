-- Registros nuevos; no modifica objetos locales ni registros de otros módulos.
CREATE TABLE objetos_encontrados (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 descripcion VARCHAR(300) NOT NULL,
 habitacion_numero VARCHAR(20) NOT NULL,
 hallado_en TIMESTAMPTZ NOT NULL,
 nota_interna VARCHAR(500) NOT NULL DEFAULT '',
 referencia_local VARCHAR(200) UNIQUE,
 foto BYTEA,
 foto_mime VARCHAR(30),
 creado_por BIGINT NOT NULL REFERENCES empleados(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 revision UUID,
 revision_datos JSONB,
 revisado_por BIGINT REFERENCES empleados(id),
 notificado_en TIMESTAMPTZ,
 CHECK ((foto IS NULL) = (foto_mime IS NULL)),
 CHECK (foto IS NULL OR octet_length(foto) <= 5242880)
);
CREATE TABLE objetos_avisos (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 objeto_id BIGINT NOT NULL UNIQUE REFERENCES objetos_encontrados(id),
 revision UUID NOT NULL,
 reserva_id BIGINT NOT NULL REFERENCES reservas(id),
 huesped_id BIGINT NOT NULL REFERENCES huespedes(id),
 destinatario VARCHAR(255) NOT NULL,
 datos JSONB NOT NULL,
 foto BYTEA,
 foto_mime VARCHAR(30),
 estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
   CHECK (estado IN ('PENDIENTE','ENVIANDO','ENVIADO','FALLIDO','INCIERTO')),
 intentos INT NOT NULL DEFAULT 0,
 ultimo_error VARCHAR(500),
 confirmado_por BIGINT NOT NULL REFERENCES empleados(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 iniciado_en TIMESTAMPTZ,
 enviado_en TIMESTAMPTZ,
 message_id VARCHAR(200) NOT NULL UNIQUE
);
CREATE INDEX ix_objetos_avisos_pendientes ON objetos_avisos(id) WHERE estado = 'PENDIENTE';
