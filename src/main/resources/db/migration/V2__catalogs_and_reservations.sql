-- V2: Catalogs and reservations. Schema only.
CREATE TABLE hotel_configuration (
    id                                  SMALLINT     PRIMARY KEY,
    name                                VARCHAR(150) NOT NULL,
    description                         TEXT         NOT NULL,
    address                             VARCHAR(255) NOT NULL,
    phone                               VARCHAR(30)  NOT NULL,
    email                               VARCHAR(150) NOT NULL,
    trade_name                          VARCHAR(150) NOT NULL,
    legal_name                          VARCHAR(150) NOT NULL,
    tax_id                              VARCHAR(20)  NOT NULL,
    tax_address                         VARCHAR(255) NOT NULL,
    CONSTRAINT ck_hotel_configuration_single_row CHECK (id = 1)
);

CREATE TABLE hotel_photos (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_key                          VARCHAR(255) NOT NULL,
    sort_order                          INT          NOT NULL DEFAULT 0
);

CREATE TABLE room_types (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                                VARCHAR(100)  NOT NULL,
    description                         TEXT          NOT NULL,
    capacity                            INT           NOT NULL,
    base_price                          NUMERIC(12,2) NOT NULL,
    weekend_adjustment_pct              NUMERIC(5,2)  NOT NULL DEFAULT 0,
    status                              VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO',
    CONSTRAINT uq_room_types_name UNIQUE (name),
    CONSTRAINT ck_room_types_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_room_types_price CHECK (base_price > 0),
    CONSTRAINT ck_room_types_adjustment CHECK (weekend_adjustment_pct > -100),
    CONSTRAINT ck_room_types_status CHECK (status IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE room_type_photos (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_type_id                        BIGINT       NOT NULL REFERENCES room_types (id),
    object_key                          VARCHAR(255) NOT NULL,
    is_primary                          BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order                          INT          NOT NULL DEFAULT 0
);

CREATE INDEX ix_room_type_photos_type ON room_type_photos (room_type_id);
CREATE UNIQUE INDEX uq_room_type_photos_primary
    ON room_type_photos (room_type_id) WHERE is_primary;

CREATE TABLE rooms (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                              VARCHAR(10) NOT NULL,
    floor                               INT         NOT NULL,
    room_type_id                        BIGINT      NOT NULL REFERENCES room_types (id),
    occupancy                           VARCHAR(10) NOT NULL DEFAULT 'LIBRE',
    condition                           VARCHAR(20) NOT NULL DEFAULT 'LIMPIA',
    status                              VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',

    cleaning_employee_id                BIGINT REFERENCES employees (id),
    CONSTRAINT uq_rooms_number UNIQUE (number),
    CONSTRAINT ck_rooms_occupancy CHECK (occupancy IN ('LIBRE', 'OCUPADA')),
    CONSTRAINT ck_rooms_condition
        CHECK (condition IN ('LIMPIA', 'SUCIA', 'EN_LIMPIEZA', 'FUERA_DE_SERVICIO')),
    CONSTRAINT ck_rooms_status CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_rooms_cleaning_assignee
        CHECK ((condition = 'EN_LIMPIEZA') = (cleaning_employee_id IS NOT NULL))
);

CREATE INDEX ix_rooms_type ON rooms (room_type_id);

CREATE TABLE seasons (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                                VARCHAR(100) NOT NULL,
    start_date                          DATE         NOT NULL,
    end_date                            DATE         NOT NULL,
    adjustment_pct                      NUMERIC(5,2) NOT NULL,
    applies_to_all                      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_seasons_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_seasons_adjustment CHECK (adjustment_pct > -100)
);

CREATE TABLE season_room_types (
    season_id                           BIGINT NOT NULL REFERENCES seasons (id) ON DELETE CASCADE,
    room_type_id                        BIGINT NOT NULL REFERENCES room_types (id),
    PRIMARY KEY (season_id, room_type_id)
);

CREATE TABLE menu_items (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category                            VARCHAR(60)   NOT NULL,
    name                                VARCHAR(100)  NOT NULL,
    description                         TEXT          NOT NULL,
    price                               NUMERIC(12,2) NOT NULL,
    photo_key                           VARCHAR(255),
    availability                        VARCHAR(10)   NOT NULL DEFAULT 'DISPONIBLE',
    status                              VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO',
    exhausted_at                        TIMESTAMPTZ,
    exhausted_by_employee_id            BIGINT REFERENCES employees (id),
    CONSTRAINT ck_menu_items_price CHECK (price > 0),
    CONSTRAINT ck_menu_items_availability CHECK (availability IN ('DISPONIBLE', 'AGOTADO')),
    CONSTRAINT ck_menu_items_status CHECK (status IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE articles (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                                VARCHAR(100) NOT NULL,
    maximum_quantity                    INT          NOT NULL,
    CONSTRAINT uq_articles_name UNIQUE (name),
    CONSTRAINT ck_articles_maximum_quantity CHECK (maximum_quantity >= 1)
);

CREATE TABLE reservations (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                                VARCHAR(9)    NOT NULL,
    guest_id                            BIGINT        NOT NULL REFERENCES guests (id),
    room_type_id                        BIGINT        NOT NULL REFERENCES room_types (id),
    room_id                             BIGINT REFERENCES rooms (id),
    checkin_date                        DATE          NOT NULL,
    checkout_date                       DATE          NOT NULL,
    guest_count                         INT           NOT NULL,
    status                              VARCHAR(15)   NOT NULL,
    channel                             VARCHAR(15)   NOT NULL,
    external_identifier                 VARCHAR(60),
    total                               NUMERIC(12,2) NOT NULL,
    created_by_employee_id              BIGINT REFERENCES employees (id),
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    room_assigned_at                    TIMESTAMPTZ,
    room_assigned_by_employee_id        BIGINT REFERENCES employees (id),
    CONSTRAINT uq_reservations_code UNIQUE (code),
    CONSTRAINT ck_reservations_code CHECK (code ~ '^VS-[A-Z0-9]{6}$'),
    CONSTRAINT ck_reservations_status
        CHECK (status IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'EN_ESTADIA', 'FINALIZADA', 'CANCELADA')),
    CONSTRAINT ck_reservations_channel
        CHECK (channel IN ('DIRECTO_WEB', 'RECEPCION', 'BOOKING', 'EXPEDIA')),

    CONSTRAINT ck_reservations_external_identifier
        CHECK ((channel IN ('BOOKING', 'EXPEDIA')) = (external_identifier IS NOT NULL)),

    CONSTRAINT uq_reservations_channel_identifier UNIQUE (channel, external_identifier),

    CONSTRAINT ck_reservations_dates CHECK (checkout_date > checkin_date),
    CONSTRAINT ck_reservations_max_nights CHECK (checkout_date - checkin_date <= 30),
    CONSTRAINT ck_reservations_guest_count CHECK (guest_count >= 1),

    CONSTRAINT ex_reservations_room_overlap EXCLUDE USING gist (
        room_id WITH =,
        daterange(checkin_date, checkout_date, '[)') WITH &&
    ) WHERE (status IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'EN_ESTADIA'))
);

CREATE INDEX ix_reservations_guest ON reservations (guest_id);
CREATE INDEX ix_reservations_type_dates ON reservations (room_type_id, checkin_date, checkout_date);

CREATE TABLE reservation_nights (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id                      BIGINT        NOT NULL REFERENCES reservations (id),
    date                                DATE          NOT NULL,
    price                               NUMERIC(12,2) NOT NULL,
    season_name                         VARCHAR(100),
    is_weekend                          BOOLEAN       NOT NULL,
    CONSTRAINT uq_reservation_nights_date UNIQUE (reservation_id, date)
);

CREATE TABLE additional_guests (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id                      BIGINT       NOT NULL REFERENCES reservations (id),
    full_name                           VARCHAR(150) NOT NULL,
    document_type                       VARCHAR(10)  NOT NULL,
    document_number                     VARCHAR(30)  NOT NULL,
    nationality                         VARCHAR(60)  NOT NULL,
    CONSTRAINT ck_additional_guests_document_type
        CHECK (document_type IN ('DPI', 'PASAPORTE'))
);

CREATE INDEX ix_additional_guests_reservation ON additional_guests (reservation_id);
