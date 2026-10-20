-- V4: Accounts payments and invoicing. Schema only.
CREATE TABLE accounts (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id                      BIGINT      NOT NULL REFERENCES reservations (id),
    status                              VARCHAR(10) NOT NULL DEFAULT 'ABIERTA',
    opened_at                           TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at                           TIMESTAMPTZ,
    CONSTRAINT uq_accounts_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ABIERTA', 'CERRADA'))
);

CREATE TABLE charges (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id                          BIGINT        NOT NULL REFERENCES accounts (id),
    type                                VARCHAR(15)   NOT NULL,
    service_category                    VARCHAR(15),
    concept                             VARCHAR(150)  NOT NULL,
    quantity                            INT           NOT NULL,
    unit_price                          NUMERIC(12,2) NOT NULL,
    total                               NUMERIC(12,2) NOT NULL,
    status                              VARCHAR(10)   NOT NULL DEFAULT 'VIGENTE',
    order_id                            BIGINT REFERENCES orders (id),

    created_by_employee_id              BIGINT REFERENCES employees (id),
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    void_reason                         VARCHAR(500),
    voided_by_employee_id               BIGINT REFERENCES employees (id),
    voided_at                           TIMESTAMPTZ,

    CONSTRAINT uq_charges_order UNIQUE (order_id),
    CONSTRAINT ck_charges_type CHECK (type IN ('ALOJAMIENTO', 'ROOM_SERVICE', 'SERVICIO')),
    CONSTRAINT ck_charges_service_category
        CHECK (service_category IN ('RESTAURANTE', 'LAVANDERIA', 'ESTACIONAMIENTO', 'OTRO')),
    CONSTRAINT ck_charges_category_by_type
        CHECK ((type = 'SERVICIO') = (service_category IS NOT NULL)),
    CONSTRAINT ck_charges_order_by_type
        CHECK ((type = 'ROOM_SERVICE') = (order_id IS NOT NULL)),
    CONSTRAINT ck_charges_quantity CHECK (quantity > 0),
    CONSTRAINT ck_charges_price CHECK (unit_price > 0),
    CONSTRAINT ck_charges_status CHECK (status IN ('VIGENTE', 'ANULADO')),

    CONSTRAINT ck_charges_void_reason
        CHECK ((status = 'ANULADO') = (void_reason IS NOT NULL)),

    CONSTRAINT ck_charges_accommodation_not_voided
        CHECK (NOT (type = 'ALOJAMIENTO' AND status = 'ANULADO'))
);

CREATE INDEX ix_charges_account ON charges (account_id);

CREATE TABLE payments (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id                          BIGINT        NOT NULL REFERENCES accounts (id),
    method                              VARCHAR(10)   NOT NULL,
    status                              VARCHAR(15)   NOT NULL,
    amount                              NUMERIC(12,2) NOT NULL,
    reference                           VARCHAR(100),
    stripe_session_id                   VARCHAR(255),
    stripe_payment_intent_id            VARCHAR(255),
    recorded_by_employee_id             BIGINT REFERENCES employees (id),
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    approved_at                         TIMESTAMPTZ,
    refunded_at                         TIMESTAMPTZ,
    CONSTRAINT ck_payments_method CHECK (method IN ('STRIPE', 'CANAL', 'EFECTIVO', 'TARJETA', 'OTRO')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDIENTE', 'APROBADO', 'FALLIDO', 'REEMBOLSADO')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT uq_payments_stripe_session UNIQUE (stripe_session_id),
    CONSTRAINT ck_payments_stripe_session
        CHECK ((method = 'STRIPE') = (stripe_session_id IS NOT NULL))
);

CREATE INDEX ix_payments_account ON payments (account_id);

CREATE TABLE stripe_events (
    event_id                            VARCHAR(255) PRIMARY KEY,
    type                                VARCHAR(100) NOT NULL,
    received_at                         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE invoice_series (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    series                              VARCHAR(10) NOT NULL,
    initial_number                      INT         NOT NULL,
    last_number                         INT         NOT NULL,
    CONSTRAINT uq_invoice_series_series UNIQUE (series),
    CONSTRAINT ck_invoice_series_initial_number CHECK (initial_number >= 1),
    CONSTRAINT ck_invoice_series_last_number CHECK (last_number >= initial_number - 1)
);

CREATE TABLE invoices (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id                          BIGINT        NOT NULL REFERENCES accounts (id),
    series_id                           BIGINT        NOT NULL REFERENCES invoice_series (id),
    number                              INT           NOT NULL,
    status                              VARCHAR(10)   NOT NULL DEFAULT 'EMITIDA',
    issued_at                           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    buyer_tax_id                        VARCHAR(20)   NOT NULL,
    buyer_name                          VARCHAR(150)  NOT NULL,
    total                               NUMERIC(12,2) NOT NULL,
    pdf_key                             VARCHAR(255),
    trade_name                          VARCHAR(150)  NOT NULL,
    legal_name                          VARCHAR(150)  NOT NULL,
    hotel_tax_id                        VARCHAR(20)   NOT NULL,
    tax_address                         VARCHAR(255)  NOT NULL,
    address                             VARCHAR(255)  NOT NULL,

    CONSTRAINT uq_invoices_account UNIQUE (account_id),

    CONSTRAINT uq_invoices_series_number UNIQUE (series_id, number),
    CONSTRAINT ck_invoices_status CHECK (status = 'EMITIDA')
);
