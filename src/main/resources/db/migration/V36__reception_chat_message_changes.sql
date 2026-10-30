-- Conserva el mensaje original al eliminarlo; el API oculta su texto.
ALTER TABLE stay_messages ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stay_messages ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE stay_messages ADD COLUMN deleted_at TIMESTAMPTZ;
