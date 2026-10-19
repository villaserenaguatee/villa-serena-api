-- Dato opcional de cada reserva, expresado en la hora local del hotel.
-- Sin DEFAULT ni backfill: los registros existentes permanecen sin hora estimada.
ALTER TABLE reservas
    ADD COLUMN hora_estimada_llegada TIME WITHOUT TIME ZONE,
    ADD CONSTRAINT ck_reservas_hora_estimada_llegada
        CHECK (hora_estimada_llegada < TIME '24:00:00'
               AND EXTRACT(SECOND FROM hora_estimada_llegada) = 0);
