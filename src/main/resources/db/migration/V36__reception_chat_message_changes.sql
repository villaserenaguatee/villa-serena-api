-- Conserva el mensaje original al eliminarlo; el API oculta su texto.
ALTER TABLE stay_messages ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stay_messages ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE stay_messages ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE reception_chat_preferences (
 employee_id BIGINT NOT NULL REFERENCES employees(id),
 reservation_id BIGINT NOT NULL REFERENCES reservations(id),
 favorite BOOLEAN NOT NULL DEFAULT false,
 archived BOOLEAN NOT NULL DEFAULT false,
 removed BOOLEAN NOT NULL DEFAULT false,
 last_read_id BIGINT NOT NULL DEFAULT 0 CHECK(last_read_id>=0),
 PRIMARY KEY(employee_id,reservation_id)
);
