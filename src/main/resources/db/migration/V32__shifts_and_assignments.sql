-- V32: Catálogo de turnos y asignaciones semanales de personal (HU-ADM-12)

CREATE TABLE work_shifts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (status IN ('ACTIVO', 'INACTIVO'))
);

CREATE TABLE shift_assignments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    shift_id BIGINT NOT NULL REFERENCES work_shifts(id),
    work_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shift_assignments_range ON shift_assignments (work_date, employee_id);
