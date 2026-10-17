CREATE TABLE laboral_asistencia (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 empleado_id BIGINT NOT NULL REFERENCES empleados(id), fecha DATE NOT NULL,
 turno VARCHAR(60) NOT NULL, estado VARCHAR(15) NOT NULL CHECK(estado IN ('presente','ausente','descanso','pendiente')),
 observacion VARCHAR(500) NOT NULL DEFAULT '', version BIGINT NOT NULL DEFAULT 1,
 UNIQUE(empleado_id,fecha)
);
CREATE TABLE laboral_correcciones (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, empleado_id BIGINT NOT NULL REFERENCES empleados(id),
 campo VARCHAR(20) NOT NULL CHECK(campo IN ('nombre','telefono','correo')),
 anterior VARCHAR(150) NOT NULL, nuevo VARCHAR(150) NOT NULL, motivo VARCHAR(500) NOT NULL,
 estado VARCHAR(15) NOT NULL DEFAULT 'pendiente' CHECK(estado IN ('pendiente','aprobada','rechazada')),
 creada_en TIMESTAMPTZ NOT NULL DEFAULT now(), resuelta_en TIMESTAMPTZ,
 resuelta_por BIGINT REFERENCES empleados(id), respuesta VARCHAR(500), version BIGINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX laboral_correccion_pendiente ON laboral_correcciones(empleado_id,campo) WHERE estado='pendiente';
CREATE TABLE laboral_nominas (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, periodo DATE NOT NULL CHECK(extract(day from periodo)=1),
 empleado_id BIGINT NOT NULL REFERENCES empleados(id), nombre VARCHAR(150) NOT NULL,
 base NUMERIC(12,2) NOT NULL CHECK(base>=0), bonos NUMERIC(12,2) NOT NULL CHECK(bonos>=0),
 descuentos NUMERIC(12,2) NOT NULL CHECK(descuentos>=0), neto NUMERIC(12,2) NOT NULL CHECK(neto>=0),
 creada_en TIMESTAMPTZ NOT NULL DEFAULT now(), creada_por BIGINT NOT NULL REFERENCES empleados(id),
 UNIQUE(periodo,empleado_id), CHECK(neto=base+bonos-descuentos)
);
CREATE TABLE laboral_auditoria (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, entidad VARCHAR(30) NOT NULL,
 registro VARCHAR(100) NOT NULL, empleado_id BIGINT NOT NULL REFERENCES empleados(id),
 anterior JSONB, nuevo JSONB NOT NULL, creada_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
