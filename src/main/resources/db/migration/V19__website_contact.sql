-- V19: Website contact. Schema only.
CREATE TABLE web_contact (
 id uuid PRIMARY KEY,
 fingerprint varchar(64) NOT NULL,
 name varchar(150) NOT NULL,
 email varchar(150) NOT NULL,
 message text NOT NULL CHECK(length(message) BETWEEN 1 AND 5000),
 status varchar(12) NOT NULL CHECK(status IN ('ENVIANDO','ENVIADO','FALLIDO','INCIERTO')),
 attempts int NOT NULL DEFAULT 1,
 error_code varchar(40),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 sent_at timestamptz
);
