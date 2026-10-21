-- V25: Staff access recovery. Schema only.
CREATE TABLE staff_recoveries (
 id uuid PRIMARY KEY,
 email_hash varchar(64) NOT NULL,
 employee_id bigint REFERENCES employees(id),
 token_hash varchar(64) UNIQUE,
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 used_at timestamptz,
 outbox_id bigint REFERENCES outbox(id)
);
CREATE INDEX staff_recoveries_email ON staff_recoveries(email_hash,created_at);
ALTER TABLE employees ADD COLUMN access_revoked_at timestamptz;
