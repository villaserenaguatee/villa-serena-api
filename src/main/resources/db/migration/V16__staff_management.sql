-- V16: Staff management. Schema only.
CREATE TABLE staff_attendance (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 employee_id                         BIGINT NOT NULL REFERENCES employees(id), date DATE NOT NULL,
 shift                               VARCHAR(60) NOT NULL, status VARCHAR(15) NOT NULL CHECK(status IN ('presente','ausente','descanso','pendiente')),
 observation                         VARCHAR(500) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 1,
 UNIQUE(employee_id,date)
);
CREATE TABLE staff_corrections (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, employee_id BIGINT NOT NULL REFERENCES employees(id),
 field                               VARCHAR(20) NOT NULL CHECK(field IN ('nombre','telefono','correo')),
 previous                            VARCHAR(150) NOT NULL, new VARCHAR(150) NOT NULL, reason VARCHAR(500) NOT NULL,
 status                              VARCHAR(15) NOT NULL DEFAULT 'pendiente' CHECK(status IN ('pendiente','aprobada','rechazada')),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ,
 resolved_by                         BIGINT REFERENCES employees(id), response VARCHAR(500), version BIGINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX staff_pending_correction ON staff_corrections(employee_id,field) WHERE status='pendiente';
CREATE TABLE staff_payroll (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, period DATE NOT NULL CHECK(extract(day from period)=1),
 employee_id                         BIGINT NOT NULL REFERENCES employees(id), name VARCHAR(150) NOT NULL,
 base                                NUMERIC(12,2) NOT NULL CHECK(base>=0), bonuses NUMERIC(12,2) NOT NULL CHECK(bonuses>=0),
 deductions                          NUMERIC(12,2) NOT NULL CHECK(deductions>=0), net NUMERIC(12,2) NOT NULL CHECK(net>=0),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(), created_by BIGINT NOT NULL REFERENCES employees(id),
 UNIQUE(period,employee_id), CHECK(net=base+bonuses-deductions)
);
CREATE TABLE staff_audit (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, entity VARCHAR(30) NOT NULL,
 record                              VARCHAR(100) NOT NULL, employee_id BIGINT NOT NULL REFERENCES employees(id),
 previous                            JSONB, new JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
