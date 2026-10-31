-- Categorías de Room Service: franjas y horarios configurables.
ALTER TABLE menu_categories
  ADD COLUMN franja VARCHAR(20) NOT NULL DEFAULT 'OTROS',
  ADD COLUMN hora_inicio TIME,
  ADD COLUMN hora_fin TIME;
ALTER TABLE menu_categories
  ADD CONSTRAINT ck_menu_category_franja CHECK (franja IN ('DESAYUNO','ALMUERZO','CENA','BEBIDAS','OTROS')),
  ADD CONSTRAINT ck_menu_category_hours_pair CHECK ((hora_inicio IS NULL) = (hora_fin IS NULL));
