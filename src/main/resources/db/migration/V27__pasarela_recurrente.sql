-- ADR-017: nombres neutrales y soporte de Recurrente, conservando los pagos
-- y la auditoría históricos de Stripe sin cambiar su proveedor.
ALTER TABLE pagos DROP CONSTRAINT ck_pagos_metodo;
ALTER TABLE pagos DROP CONSTRAINT ck_pagos_stripe_session;
ALTER TABLE pagos RENAME COLUMN stripe_session_id TO checkout_id;
ALTER TABLE pagos RENAME COLUMN stripe_payment_intent_id TO pago_externo_id;
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_metodo
    CHECK (metodo IN ('STRIPE', 'RECURRENTE', 'CANAL', 'EFECTIVO', 'TARJETA', 'OTRO'));
ALTER TABLE pagos ADD CONSTRAINT ck_pagos_checkout
    CHECK ((metodo IN ('STRIPE', 'RECURRENTE')) = (checkout_id IS NOT NULL));
ALTER TABLE pagos RENAME CONSTRAINT uq_pagos_stripe_session TO uq_pagos_checkout;
ALTER TABLE stripe_eventos RENAME TO pago_eventos;

ALTER TABLE historial_estados DROP CONSTRAINT ck_historial_tipo_responsable;
ALTER TABLE historial_estados ADD CONSTRAINT ck_historial_tipo_responsable
    CHECK (tipo_responsable IN ('EMPLEADO', 'HUESPED', 'CLIENTE', 'CANAL', 'STRIPE', 'RECURRENTE', 'SISTEMA'));
