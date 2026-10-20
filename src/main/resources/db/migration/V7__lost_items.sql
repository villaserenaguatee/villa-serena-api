-- V7: Lost items. Schema only.
CREATE TABLE lost_items (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 description                         VARCHAR(300) NOT NULL,
 room_number                         VARCHAR(20) NOT NULL,
 found_at                            TIMESTAMPTZ NOT NULL,
 internal_note                       VARCHAR(500) NOT NULL DEFAULT '',
 local_reference                     VARCHAR(200) UNIQUE,
 photo                               BYTEA,
 photo_mime                          VARCHAR(30),
 created_by                          BIGINT NOT NULL REFERENCES employees(id),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 review                              UUID,
 data_revision                       JSONB,
 reviewed_by                         BIGINT REFERENCES employees(id),
 notified_at                         TIMESTAMPTZ,
 CHECK ((photo IS NULL) = (photo_mime IS NULL)),
 CHECK (photo IS NULL OR octet_length(photo) <= 5242880)
);
CREATE TABLE lost_item_notifications (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 item_id                             BIGINT NOT NULL UNIQUE REFERENCES lost_items(id),
 review                              UUID NOT NULL,
 reservation_id                      BIGINT NOT NULL REFERENCES reservations(id),
 guest_id                            BIGINT NOT NULL REFERENCES guests(id),
 recipient                           VARCHAR(255) NOT NULL,
 data                                JSONB NOT NULL,
 photo                               BYTEA,
 photo_mime                          VARCHAR(30),
 status                              VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
   CHECK (status IN ('PENDIENTE','ENVIANDO','ENVIADO','FALLIDO','INCIERTO')),
 attempts                            INT NOT NULL DEFAULT 0,
 last_error                          VARCHAR(500),
 confirmed_by                        BIGINT NOT NULL REFERENCES employees(id),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 started_at                          TIMESTAMPTZ,
 sent_at                             TIMESTAMPTZ,
 message_id                          VARCHAR(200) NOT NULL UNIQUE
);
CREATE INDEX ix_lost_item_notifications_pending ON lost_item_notifications(id) WHERE status = 'PENDIENTE';
