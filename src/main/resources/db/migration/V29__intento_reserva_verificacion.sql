-- Solo las solicitudes públicas nuevas se vinculan a la prueba consumida.
-- No se inventa titularidad para intentos históricos sin vínculo verificable.
ALTER TABLE reservas_solicitudes
    ADD COLUMN verificacion_id UUID REFERENCES reserva_correo_verificaciones(id);
