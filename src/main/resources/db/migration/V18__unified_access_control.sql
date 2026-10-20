-- V18: Unified access control. Schema only.
CREATE TABLE unified_access_control (
 email varchar(150) PRIMARY KEY,
 attempts int NOT NULL DEFAULT 0 CHECK(attempts>=0),
 locked_until timestamptz
);
