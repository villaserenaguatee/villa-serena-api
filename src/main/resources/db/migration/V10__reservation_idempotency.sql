-- V10: Reservation idempotency. Schema only.
CREATE TABLE reservation_requests (
 scope                               VARCHAR(100) NOT NULL,
 key                                 VARCHAR(100) NOT NULL,
 fingerprint                         VARCHAR(64) NOT NULL,
 response                            JSONB NOT NULL,
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(scope,key)
);
