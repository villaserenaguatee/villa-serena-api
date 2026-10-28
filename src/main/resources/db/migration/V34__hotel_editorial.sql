-- Sin semillas ni cambios en información operativa/fiscal existente.
CREATE TABLE hotel_editorial_drafts (
    language VARCHAR(2) PRIMARY KEY CHECK (language IN ('es', 'en')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    content JSONB NOT NULL CHECK (jsonb_typeof(content) = 'array'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL REFERENCES employees(id)
);

CREATE TABLE hotel_editorial_publications (
    language VARCHAR(2) NOT NULL CHECK (language IN ('es', 'en')),
    revision BIGINT NOT NULL CHECK (revision > 0),
    content JSONB NOT NULL CHECK (jsonb_typeof(content) = 'array'),
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by BIGINT NOT NULL REFERENCES employees(id),
    PRIMARY KEY (language, revision)
);
