-- V17: Offer catalog. Schema only.
CREATE TABLE catalog_configurations(key varchar(10) PRIMARY KEY,room_type_id bigint NOT NULL REFERENCES room_types(id));
CREATE TABLE catalog_offers(slug varchar(80) PRIMARY KEY,room_type_id bigint NOT NULL REFERENCES room_types(id),base_price numeric(12,2) NOT NULL CHECK(base_price>0),content jsonb NOT NULL,sort_order int NOT NULL);
CREATE TABLE catalog_offer_rooms(slug varchar(80) REFERENCES catalog_offers(slug),room_id bigint REFERENCES rooms(id),evidence text NOT NULL,PRIMARY KEY(slug,room_id));
CREATE TABLE reservation_offers(reservation_id bigint PRIMARY KEY REFERENCES reservations(id),slug varchar(80) NOT NULL REFERENCES catalog_offers(slug),content jsonb NOT NULL);
