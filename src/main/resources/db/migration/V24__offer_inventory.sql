-- V24: Offer inventory. Schema only.
CREATE TABLE room_features(room_id bigint PRIMARY KEY REFERENCES rooms(id),content jsonb NOT NULL CHECK(jsonb_typeof(content)='object'),provenance text NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE catalog_offers ADD COLUMN requirements jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE TABLE catalog_inventory_before_v24(entity text NOT NULL,key text NOT NULL,content jsonb NOT NULL,PRIMARY KEY(entity,key));
