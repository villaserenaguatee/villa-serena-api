-- V1: Security and guests. Schema only.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE employees (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name                           VARCHAR(150) NOT NULL,
    email                               VARCHAR(150) NOT NULL,
    phone                               VARCHAR(30)  NOT NULL,
    role                                VARCHAR(30)  NOT NULL,
    area                                VARCHAR(20),
    status                              VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    password_hash                       VARCHAR(100) NOT NULL,
    must_change_password                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts                     INT          NOT NULL DEFAULT 0,
    locked_until                        TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_employees_role
        CHECK (role IN ('ADMIN', 'RECEPCION', 'ROOM_SERVICE', 'MANTENIMIENTO_LIMPIEZA')),
    CONSTRAINT ck_employees_area
        CHECK (area IN ('LIMPIEZA', 'MANTENIMIENTO', 'AMBAS')),

    CONSTRAINT ck_employees_area_by_role
        CHECK ((role = 'MANTENIMIENTO_LIMPIEZA') = (area IS NOT NULL)),
    CONSTRAINT ck_employees_status
        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_employees_attempts
        CHECK (failed_attempts >= 0)
);

CREATE UNIQUE INDEX uq_employees_email ON employees (lower(email));

CREATE TABLE guests (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name                           VARCHAR(150) NOT NULL,
    email                               VARCHAR(150) NOT NULL,
    phone                               VARCHAR(30)  NOT NULL,
    nationality                         VARCHAR(60)  NOT NULL,
    document_type                       VARCHAR(10)  NOT NULL,
    document_number                     VARCHAR(30)  NOT NULL,
    otp_failed_attempts                 INT          NOT NULL DEFAULT 0,
    otp_locked_until                    TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_guests_document_type
        CHECK (document_type IN ('DPI', 'PASAPORTE')),
    CONSTRAINT ck_guests_otp_attempts
        CHECK (otp_failed_attempts >= 0)
);

CREATE UNIQUE INDEX uq_guests_email ON guests (lower(email));

CREATE TABLE refresh_tokens (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash                          VARCHAR(100) NOT NULL,
    user_type                           VARCHAR(10)  NOT NULL,
    employee_id                         BIGINT REFERENCES employees (id),
    guest_id                            BIGINT REFERENCES guests (id),
    expires_at                          TIMESTAMPTZ  NOT NULL,
    revoked_at                          TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_user CHECK (
        (user_type = 'EMPLEADO' AND employee_id IS NOT NULL AND guest_id IS NULL)
        OR (user_type = 'HUESPED' AND guest_id IS NOT NULL AND employee_id IS NULL)
    )
);

CREATE INDEX ix_refresh_tokens_employee ON refresh_tokens (employee_id);
CREATE INDEX ix_refresh_tokens_guest ON refresh_tokens (guest_id);

CREATE TABLE otp_codes (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guest_id                            BIGINT       NOT NULL REFERENCES guests (id),
    code_hash                           VARCHAR(100) NOT NULL,
    expires_at                          TIMESTAMPTZ  NOT NULL,
    used_at                             TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_otp_codes_guest ON otp_codes (guest_id);

CREATE TABLE push_devices (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guest_id                            BIGINT       NOT NULL REFERENCES guests (id),
    expo_token                          VARCHAR(255) NOT NULL,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_devices_token UNIQUE (expo_token)
);

CREATE INDEX ix_push_devices_guest ON push_devices (guest_id);
