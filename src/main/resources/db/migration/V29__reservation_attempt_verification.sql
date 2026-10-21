-- V29: Reservation attempt verification. Schema only.
ALTER TABLE reservation_requests
    ADD COLUMN verification_id UUID REFERENCES reservation_email_verifications(id);
