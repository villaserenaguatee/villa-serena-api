-- V5: Channels and system. Schema only.
CREATE TABLE channels (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                                VARCHAR(15) NOT NULL,
    name                                VARCHAR(60) NOT NULL,
    key_hash                            VARCHAR(64) NOT NULL,
    CONSTRAINT uq_channels_code UNIQUE (code),
    CONSTRAINT ck_channels_code CHECK (code IN ('BOOKING', 'EXPEDIA')),
    CONSTRAINT ck_channels_key_hash CHECK (key_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE status_history (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type                         VARCHAR(25)  NOT NULL,
    entity_id                           BIGINT       NOT NULL,
    previous_status                     VARCHAR(20),
    new_status                          VARCHAR(20)  NOT NULL,
    responsible_type                    VARCHAR(10)  NOT NULL,
    responsible_id                      BIGINT REFERENCES employees (id),
    reason                              VARCHAR(500),
    date                                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_history_entity_type CHECK (entity_type IN (
        'RESERVA', 'HABITACION_OCUPACION', 'HABITACION_CONDICION',
        'PEDIDO', 'SOLICITUD', 'INCIDENCIA')),
    CONSTRAINT ck_history_responsible_type CHECK (responsible_type IN (
        'EMPLEADO', 'HUESPED', 'CLIENTE', 'CANAL', 'STRIPE', 'SISTEMA')),
    CONSTRAINT ck_history_responsible
        CHECK ((responsible_type = 'EMPLEADO') = (responsible_id IS NOT NULL))
);

CREATE INDEX ix_history_entity ON status_history (entity_type, entity_id, date);

CREATE TABLE outbox (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type                                VARCHAR(30)  NOT NULL,
    recipient                           VARCHAR(255) NOT NULL,
    payload                             JSONB        NOT NULL,
    status                              VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE',
    attempts                            INT          NOT NULL DEFAULT 0,
    next_attempt_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error                          TEXT,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at                             TIMESTAMPTZ,
    CONSTRAINT ck_outbox_type CHECK (type IN (
        'CORREO_CONFIRMACION', 'CORREO_OTP', 'CORREO_FACTURA',
        'PUSH_PEDIDO_ENTREGADO', 'PUSH_SOLICITUD_ATENDIDA')),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_pending ON outbox (next_attempt_at) WHERE status = 'PENDIENTE';
