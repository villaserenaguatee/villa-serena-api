-- V14: Catalog administration. Schema only.
ALTER TABLE room_types ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE employees ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE employees ADD COLUMN profile JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(profile)='object');
ALTER TABLE seasons ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE seasons ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
CREATE TABLE administration_audit (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 resource                            VARCHAR(30) NOT NULL,
 record_id                           BIGINT NOT NULL,
 employee_id                         BIGINT NOT NULL REFERENCES employees(id),
 previous                            JSONB,
 new                                 JSONB NOT NULL,
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now()
);
