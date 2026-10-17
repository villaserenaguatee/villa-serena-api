CREATE SEQUENCE gestion_orden_codigo;
CREATE TABLE gestion_registros (
 tipo VARCHAR(30) NOT NULL CHECK(tipo IN ('activos','preventivos','ordenes','insumos','movimientos','compras','gastos','personal','correcciones')),
 id UUID NOT NULL,
 datos JSONB NOT NULL CHECK(jsonb_typeof(datos)='object'),
 creacion JSONB NOT NULL CHECK(jsonb_typeof(creacion)='object'),
 version BIGINT NOT NULL DEFAULT 1,
 creado_por BIGINT NOT NULL REFERENCES empleados(id),
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(tipo,id)
);
CREATE TABLE gestion_auditoria (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 tipo VARCHAR(30) NOT NULL,
 registro_id UUID NOT NULL,
 version BIGINT NOT NULL,
 empleado_id BIGINT NOT NULL REFERENCES empleados(id),
 anterior JSONB,
 nuevo JSONB NOT NULL,
 creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(tipo,registro_id,version)
);
CREATE UNIQUE INDEX uq_preventivo_orden_activa ON gestion_registros((datos->>'tareaPreventivaId')) WHERE tipo='ordenes' AND datos->>'tareaPreventivaId' IS NOT NULL AND datos->>'estado' NOT IN ('cerrada','cancelada');
CREATE UNIQUE INDEX uq_compra_documento ON gestion_registros(lower(datos->>'proveedor'),lower(datos->>'numeroFactura')) WHERE tipo='compras';
