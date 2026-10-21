-- V12: Operations management. Schema only.
CREATE SEQUENCE work_order_code;
CREATE TABLE operation_records (
 type                                VARCHAR(30) NOT NULL CHECK(type IN ('activos','preventivos','ordenes','insumos','movimientos','compras','gastos','personal','correcciones')),
 id                                  UUID NOT NULL,
 data                                JSONB NOT NULL CHECK(jsonb_typeof(data)='object'),
 creation                            JSONB NOT NULL CHECK(jsonb_typeof(creation)='object'),
 version                             BIGINT NOT NULL DEFAULT 1,
 created_by                          BIGINT NOT NULL REFERENCES employees(id),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(type,id)
);
CREATE TABLE operations_audit (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 type                                VARCHAR(30) NOT NULL,
 record_id                           UUID NOT NULL,
 version                             BIGINT NOT NULL,
 employee_id                         BIGINT NOT NULL REFERENCES employees(id),
 previous                            JSONB,
 new                                 JSONB NOT NULL,
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(type,record_id,version)
);
CREATE UNIQUE INDEX uq_active_preventive_order ON operation_records((data->>'tareaPreventivaId')) WHERE type='ordenes' AND data->>'tareaPreventivaId' IS NOT NULL AND data->>'estado' NOT IN ('cerrada','cancelada');
CREATE UNIQUE INDEX uq_purchase_document ON operation_records(lower(data->>'proveedor'),lower(data->>'numeroFactura')) WHERE type='compras';
