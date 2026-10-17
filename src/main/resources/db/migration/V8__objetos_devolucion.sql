-- La situación física es independiente del resultado del correo.
ALTER TABLE objetos_encontrados ADD COLUMN estado_fisico VARCHAR(15) NOT NULL DEFAULT 'EN_RESGUARDO'
 CHECK (estado_fisico IN ('EN_RESGUARDO','DEVUELTO'));
ALTER TABLE objetos_encontrados ADD COLUMN devuelto_en TIMESTAMPTZ;
ALTER TABLE objetos_encontrados ADD COLUMN devuelto_por BIGINT REFERENCES empleados(id);
