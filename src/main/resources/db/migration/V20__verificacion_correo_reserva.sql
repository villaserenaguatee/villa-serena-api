-- Independent purpose from codigos_otp (guest sign-in). Existing rows are untouched.
CREATE TABLE reserva_correo_verificaciones (
 id UUID PRIMARY KEY,
 correo VARCHAR(150) NOT NULL,
 borrador UUID NOT NULL,
 codigo_hash VARCHAR(100) NOT NULL,
 creado_en TIMESTAMPTZ NOT NULL,
 expira_en TIMESTAMPTZ NOT NULL,
 intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
 verificado_en TIMESTAMPTZ,
 comprobante_hash VARCHAR(64),
 comprobante_expira_en TIMESTAMPTZ,
 consumido_en TIMESTAMPTZ,
 outbox_id BIGINT NOT NULL REFERENCES outbox(id)
);
CREATE INDEX ix_reserva_correo_envios ON reserva_correo_verificaciones(correo, creado_en DESC);
CREATE INDEX ix_reserva_correo_borrador ON reserva_correo_verificaciones(correo, borrador, creado_en DESC);
CREATE UNIQUE INDEX uq_reserva_correo_comprobante ON reserva_correo_verificaciones(comprobante_hash) WHERE comprobante_hash IS NOT NULL;
