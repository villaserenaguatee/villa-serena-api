-- V27: Recurrente payments. Schema only.
ALTER TABLE payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE payments DROP CONSTRAINT ck_payments_stripe_session;
ALTER TABLE payments RENAME COLUMN stripe_session_id TO checkout_id;
ALTER TABLE payments RENAME COLUMN stripe_payment_intent_id TO external_payment_id;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method
    CHECK (method IN ('STRIPE', 'RECURRENTE', 'CANAL', 'EFECTIVO', 'TARJETA', 'OTRO'));
ALTER TABLE payments ADD CONSTRAINT ck_payments_checkout
    CHECK ((method IN ('STRIPE', 'RECURRENTE')) = (checkout_id IS NOT NULL));
ALTER TABLE payments RENAME CONSTRAINT uq_payments_stripe_session TO uq_payments_checkout;
ALTER TABLE stripe_events RENAME TO payment_events;

ALTER TABLE status_history DROP CONSTRAINT ck_history_responsible_type;
ALTER TABLE status_history ADD CONSTRAINT ck_history_responsible_type
    CHECK (responsible_type IN ('EMPLEADO', 'HUESPED', 'CLIENTE', 'CANAL', 'STRIPE', 'RECURRENTE', 'SISTEMA'));
