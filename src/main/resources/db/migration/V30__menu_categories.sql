-- V30: Categorías del menú de Room Service para administración y catálogo oficial.
CREATE TABLE menu_categories (
    id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                                VARCHAR(60) NOT NULL,
    CONSTRAINT uq_menu_categories_name UNIQUE (name)
);

INSERT INTO menu_categories (name)
SELECT DISTINCT category FROM menu_items WHERE category IS NOT NULL ORDER BY category;

ALTER TABLE menu_items ADD COLUMN category_id BIGINT REFERENCES menu_categories (id);

UPDATE menu_items m
SET category_id = c.id
FROM menu_categories c
WHERE m.category = c.name;
