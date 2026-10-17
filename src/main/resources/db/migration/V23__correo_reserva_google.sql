ALTER TABLE reserva_correo_verificaciones ALTER COLUMN outbox_id DROP NOT NULL;
ALTER TABLE reserva_correo_verificaciones ADD COLUMN origen VARCHAR(10) NOT NULL DEFAULT 'CODIGO';
ALTER TABLE reserva_correo_verificaciones ADD CONSTRAINT ck_reserva_correo_origen CHECK (origen IN ('CODIGO','GOOGLE'));
