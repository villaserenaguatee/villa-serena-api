-- V28: Estimated arrival time. Schema only.
ALTER TABLE reservations
    ADD COLUMN estimated_arrival_time TIME WITHOUT TIME ZONE,
    ADD CONSTRAINT ck_reservations_estimated_arrival_time
        CHECK (estimated_arrival_time < TIME '24:00:00'
               AND EXTRACT(SECOND FROM estimated_arrival_time) = 0);
