-- V11: Documents and stay messages. Schema only.
CREATE TABLE stay_messages (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reservation_id                      BIGINT NOT NULL REFERENCES reservations(id),
 author                              VARCHAR(10) NOT NULL CHECK(author IN ('HUESPED','RECEPCION')),
 author_id                           BIGINT NOT NULL,
 key                                 UUID NOT NULL,
 text                                VARCHAR(2000) NOT NULL CHECK(length(trim(text))>0),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(reservation_id,author,author_id,key)
);
CREATE INDEX ix_stay_messages_reservation ON stay_messages(reservation_id,id);
CREATE TABLE checkin_documents (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reservation_id                      BIGINT NOT NULL REFERENCES reservations(id),
 guest_id                            BIGINT NOT NULL REFERENCES guests(id),
 key                                 UUID NOT NULL,
 name                                VARCHAR(150) NOT NULL,
 mime                                VARCHAR(40) NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),
 content                             BYTEA NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 5242880),
 status                              VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK(status IN ('PENDIENTE','ACEPTADO','RECHAZADO')),
 reason                              VARCHAR(500),
 reviewed_by                         BIGINT REFERENCES employees(id),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_at                         TIMESTAMPTZ,
 UNIQUE(reservation_id,guest_id,key)
);
CREATE INDEX ix_checkin_documents_reservation ON checkin_documents(reservation_id,id);
