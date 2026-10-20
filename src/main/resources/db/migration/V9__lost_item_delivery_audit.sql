-- V9: Lost item delivery audit. Schema only.
CREATE TABLE lost_item_delivery_events (
 id                                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 notification_id                     BIGINT NOT NULL REFERENCES lost_item_notifications(id),
 type                                VARCHAR(30) NOT NULL CHECK(type IN ('REINTENTO_CONTROLADO','RESULTADO_SMTP')),
 attempt                             INT NOT NULL,
 previous_status                     VARCHAR(15),
 result                              VARCHAR(15),
 error                               VARCHAR(500),
 employee_id                         BIGINT REFERENCES employees(id),
 created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
 detail                              JSONB NOT NULL DEFAULT '{}'
);
CREATE UNIQUE INDEX ix_items_controlled_retry ON lost_item_delivery_events(notification_id)
 WHERE type='REINTENTO_CONTROLADO';
