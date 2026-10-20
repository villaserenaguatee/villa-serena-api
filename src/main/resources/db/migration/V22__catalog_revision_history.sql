-- V22: Catalog revision history. Schema only.
CREATE TABLE catalog_internal_revision (
 slug                                VARCHAR(80) PRIMARY KEY REFERENCES catalog_offers(slug),
 previous_content                    JSONB NOT NULL,
 reason                              TEXT NOT NULL,
 reviewed_at                         TIMESTAMPTZ NOT NULL DEFAULT now()
);
