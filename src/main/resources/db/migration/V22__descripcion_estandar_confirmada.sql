-- Preserve original metadata internally; no price, type, room or reservation is changed.
CREATE TABLE catalogo_revision_interna (
 slug VARCHAR(80) PRIMARY KEY REFERENCES catalogo_ofertas(slug),
 contenido_anterior JSONB NOT NULL,
 motivo TEXT NOT NULL,
 revisado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO catalogo_revision_interna(slug,contenido_anterior,motivo)
SELECT slug,contenido,'Tipo real 3 Estándar Jardín; habitaciones 101 (id10) y102 (id9), piso1, queen, capacidad2. Superficie25m2 y servicios sin respaldo individual; se omiten del contenido público. Tarifa comercial420 se conserva, independiente de tarifa histórica del tipo650.'
FROM catalogo_ofertas WHERE slug='habitacion-estandar';
UPDATE catalogo_ofertas
SET contenido=(contenido-'pendingFeatures'-'size') || jsonb_build_object(
 'description','Una habitación con cama queen para dos huéspedes, en el primer piso. Un espacio para descansar durante tu visita a Villa Serena.',
 'features',jsonb_build_array('1 cama queen','Capacidad máxima: 2 huéspedes','Primer piso'),
 'verifiedFeatures',jsonb_build_array('1 cama queen','Capacidad máxima: 2 huéspedes','Primer piso'),
 'inventoryNote','Cama queen · Hasta 2 huéspedes · Primer piso'
)
WHERE slug='habitacion-estandar';
