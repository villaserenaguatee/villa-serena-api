-- V20: Reservation email verification. Schema only.
CREATE TABLE reservation_email_verifications (
 id                                  UUID PRIMARY KEY,
 email                               VARCHAR(150) NOT NULL,
 draft                               UUID NOT NULL,
 code_hash                           VARCHAR(100) NOT NULL,
 created_at                          TIMESTAMPTZ NOT NULL,
 expires_at                          TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
 verified_at                         TIMESTAMPTZ,
 proof_hash                          VARCHAR(64),
 proof_expires_at                    TIMESTAMPTZ,
 consumed_at                         TIMESTAMPTZ,
 outbox_id                           BIGINT NOT NULL REFERENCES outbox(id)
);
CREATE INDEX ix_reservation_email_deliveries ON reservation_email_verifications(email, created_at DESC);
CREATE INDEX ix_reservation_email_draft ON reservation_email_verifications(email, draft, created_at DESC);
CREATE UNIQUE INDEX uq_reservation_email_proof ON reservation_email_verifications(proof_hash) WHERE proof_hash IS NOT NULL;
