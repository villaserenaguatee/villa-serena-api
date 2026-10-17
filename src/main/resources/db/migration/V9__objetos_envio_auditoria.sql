CREATE TABLE objetos_envio_eventos (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 aviso_id BIGINT NOT NULL REFERENCES objetos_avisos(id),
 tipo VARCHAR(30) NOT NULL CHECK(tipo IN ('REINTENTO_CONTROLADO','RESULTADO_SMTP')),
 intento INT NOT NULL,
 anterior_estado VARCHAR(15),
 resultado VARCHAR(15),
 error VARCHAR(500),
 empleado_id BIGINT REFERENCES empleados(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 detalle JSONB NOT NULL DEFAULT '{}'
);
CREATE UNIQUE INDEX ix_objetos_reintento_controlado ON objetos_envio_eventos(aviso_id)
 WHERE tipo='REINTENTO_CONTROLADO';
