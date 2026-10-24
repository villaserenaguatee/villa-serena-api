-- V31: Amenidades del hotel y configuración de Wi-Fi (HU-ADM-11, HU-HUE-18)

CREATE TABLE hotel_amenities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    schedule VARCHAR(150) NOT NULL DEFAULT '',
    location VARCHAR(150) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    photo_key VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE hotel_wifi (
    id SMALLINT PRIMARY KEY,
    network_name VARCHAR(100) NOT NULL,
    password VARCHAR(100) NOT NULL,
    CONSTRAINT ck_hotel_wifi_single_row CHECK (id = 1)
);
