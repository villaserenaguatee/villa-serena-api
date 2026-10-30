-- Extensión opcional de amenidades; no crea oferta, horarios ni precios de ejemplo.
CREATE TABLE experiences (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 amenity_id BIGINT NOT NULL UNIQUE REFERENCES hotel_amenities(id),
 category VARCHAR(80) NOT NULL,
 active BOOLEAN NOT NULL,
 mode VARCHAR(20) NOT NULL CHECK(mode IN ('INMEDIATA','APROBACION','COORDINACION')),
 pricing VARCHAR(20) NOT NULL CHECK(pricing IN ('GRATUITA','POR_PERSONA','POR_RESERVA')),
 price NUMERIC(12,2) NOT NULL CHECK(price>=0),
 payment VARCHAR(20) NOT NULL CHECK(payment IN ('SIN_CARGO','CUENTA','ANTICIPADO')),
 duration_minutes INT NOT NULL CHECK(duration_minutes BETWEEN 1 AND 1440),
 requires_stay BOOLEAN NOT NULL,
 cancellable BOOLEAN NOT NULL,
 cancel_notice_minutes INT NOT NULL CHECK(cancel_notice_minutes BETWEEN 0 AND 525600),
 version BIGINT NOT NULL DEFAULT 0,
 CHECK ((pricing='GRATUITA' AND price=0 AND payment='SIN_CARGO') OR (pricing<>'GRATUITA' AND price>0 AND payment<>'SIN_CARGO'))
);
CREATE TABLE experience_slots (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 experience_id BIGINT NOT NULL REFERENCES experiences(id),
 starts_at TIMESTAMPTZ NOT NULL,
 ends_at TIMESTAMPTZ NOT NULL CHECK(ends_at>starts_at),
 capacity INT NOT NULL CHECK(capacity BETWEEN 1 AND 10000),
 active BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(experience_id,starts_at)
);
CREATE TABLE experience_bookings (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reference VARCHAR(40) NOT NULL UNIQUE,
 reservation_id BIGINT NOT NULL REFERENCES reservations(id),
 slot_id BIGINT NOT NULL REFERENCES experience_slots(id),
 request_key UUID NOT NULL,
 people INT NOT NULL CHECK(people BETWEEN 1 AND 10000),
 experience_name VARCHAR(150) NOT NULL,
 total NUMERIC(12,2) NOT NULL CHECK(total>=0),
 payment VARCHAR(20) NOT NULL CHECK(payment IN ('SIN_CARGO','CUENTA')),
 cancellable BOOLEAN NOT NULL,
 cancel_notice_minutes INT NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDIENTE','CONFIRMADA','RECHAZADA','CANCELADA')),
 reason VARCHAR(500),
 charge_id BIGINT UNIQUE REFERENCES charges(id),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 decided_by BIGINT REFERENCES employees(id),
 UNIQUE(reservation_id,request_key)
);
CREATE UNIQUE INDEX uq_experience_booking_active ON experience_bookings(reservation_id,slot_id) WHERE status IN ('PENDIENTE','CONFIRMADA');
CREATE INDEX ix_experience_capacity ON experience_bookings(slot_id,status);
CREATE INDEX ix_experience_slots_date ON experience_slots(experience_id,starts_at);
CREATE TABLE experience_inquiries (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 experience_id BIGINT NOT NULL REFERENCES experiences(id),
 reservation_id BIGINT NOT NULL REFERENCES reservations(id),
 request_key UUID NOT NULL,
 comment VARCHAR(1000) NOT NULL,
 response VARCHAR(1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 responded_by BIGINT REFERENCES employees(id),
 UNIQUE(reservation_id,request_key)
);
