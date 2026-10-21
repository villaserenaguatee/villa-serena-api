-- V3: Room service requests and incidents. Schema only.
CREATE TABLE orders (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id                      BIGINT      NOT NULL REFERENCES reservations (id),
    status                              VARCHAR(15) NOT NULL DEFAULT 'NUEVO',
    notes                               VARCHAR(500),
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_orders_status
        CHECK (status IN ('NUEVO', 'EN_PREPARACION', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO'))
);

CREATE INDEX ix_orders_reservation ON orders (reservation_id);
CREATE INDEX ix_orders_status ON orders (status);

CREATE TABLE order_items (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id                            BIGINT        NOT NULL REFERENCES orders (id),
    menu_item_id                        BIGINT        NOT NULL REFERENCES menu_items (id),
    quantity                            INT           NOT NULL,
    unit_price                          NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price CHECK (unit_price > 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE requests (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id                      BIGINT      NOT NULL REFERENCES reservations (id),
    room_id                             BIGINT      NOT NULL REFERENCES rooms (id),
    type                                VARCHAR(10) NOT NULL,
    status                              VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    comment                             VARCHAR(500),

    employee_id                         BIGINT REFERENCES employees (id),
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_requests_type CHECK (type IN ('LIMPIEZA', 'ARTICULOS')),
    CONSTRAINT ck_requests_status
        CHECK (status IN ('PENDIENTE', 'EN_PROCESO', 'ATENDIDA', 'CANCELADA')),

    CONSTRAINT ck_requests_assignee
        CHECK (status NOT IN ('EN_PROCESO', 'ATENDIDA') OR employee_id IS NOT NULL)
);

CREATE INDEX ix_requests_reservation ON requests (reservation_id);

CREATE UNIQUE INDEX uq_requests_active_cleaning
    ON requests (room_id)
    WHERE type = 'LIMPIEZA' AND status IN ('PENDIENTE', 'EN_PROCESO');

CREATE TABLE request_items (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id                          BIGINT NOT NULL REFERENCES requests (id),
    article_id                          BIGINT NOT NULL REFERENCES articles (id),
    quantity                            INT    NOT NULL,
    CONSTRAINT uq_request_items_article UNIQUE (request_id, article_id),
    CONSTRAINT ck_request_items_quantity CHECK (quantity > 0)
);

CREATE TABLE incidents (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id                             BIGINT      NOT NULL REFERENCES rooms (id),
    description                         TEXT        NOT NULL,
    blocks_use                          BOOLEAN     NOT NULL,

    photo_key                           VARCHAR(255),
    status                              VARCHAR(15) NOT NULL DEFAULT 'REPORTADA',
    reported_by_employee_id             BIGINT      NOT NULL REFERENCES employees (id),
    technician_id                       BIGINT REFERENCES employees (id),
    solution                            TEXT,
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_incidents_status CHECK (status IN ('REPORTADA', 'EN_PROCESO', 'RESUELTA')),
    CONSTRAINT ck_incidents_technician
        CHECK (status = 'REPORTADA' OR technician_id IS NOT NULL),

    CONSTRAINT ck_incidents_solution
        CHECK ((status = 'RESUELTA') = (solution IS NOT NULL))
);

CREATE INDEX ix_incidents_room ON incidents (room_id);
