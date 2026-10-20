-- V13: Local private files. Schema only.
CREATE TABLE local_private_files (
 id                                  UUID PRIMARY KEY,
 key                                 VARCHAR(255) NOT NULL UNIQUE,
 mime                                VARCHAR(40) NOT NULL CHECK(mime IN ('image/jpeg','image/png','application/pdf')),
 content                             BYTEA NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 5242880),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
);
