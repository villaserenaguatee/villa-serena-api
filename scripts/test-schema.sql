-- Schema constraint checks against local fixtures. All test writes are rolled back.
\set ON_ERROR_STOP 1
\set QUIET on
\pset footer off
BEGIN;

\echo '== Local fixtures'
SELECT (SELECT count(*) FROM hotel_configuration) AS hotel,
       (SELECT count(*) FROM invoice_series)      AS series,
       (SELECT count(*) FROM room_types)    AS room_types,
       (SELECT count(*) FROM rooms)        AS rooms,
       (SELECT count(*) FROM seasons)          AS seasons,
       (SELECT count(*) FROM menu_items)          AS menu_items,
       (SELECT count(*) FROM articles)           AS articles,
       (SELECT count(*) FROM channels)             AS channels,
       (SELECT count(*) FROM employees)           AS employees;

DO $$
BEGIN
  IF (SELECT count(*) FROM hotel_configuration) <> 1 THEN RAISE EXCEPTION 'FAIL: missing hotel configuration'; END IF;
  IF NOT EXISTS (SELECT 1 FROM invoice_series WHERE series = 'VS-A') THEN RAISE EXCEPTION 'FAIL: missing invoice series VS-A'; END IF;
  IF (SELECT count(*) FROM room_types) < 4 THEN RAISE EXCEPTION 'FAIL: missing room types'; END IF;
  IF (SELECT count(*) FROM rooms) < 12 THEN RAISE EXCEPTION 'FAIL: missing rooms'; END IF;
  IF (SELECT count(*) FROM seasons) < 2 THEN RAISE EXCEPTION 'FAIL: missing seasons'; END IF;
  IF (SELECT count(*) FROM menu_items) < 10 OR (SELECT count(DISTINCT category) FROM menu_items) < 3 THEN
    RAISE EXCEPTION 'FAIL: missing menu items or categories'; END IF;
  IF (SELECT count(*) FROM articles) < 6 THEN RAISE EXCEPTION 'FAIL: missing articles'; END IF;
  IF (SELECT count(*) FROM channels WHERE code IN ('BOOKING', 'EXPEDIA')) <> 2 THEN RAISE EXCEPTION 'FAIL: missing channels'; END IF;
  IF (SELECT count(*) FROM employees WHERE role = 'ADMIN' AND status = 'ACTIVO') < 2 THEN RAISE EXCEPTION 'FAIL: missing two active administrators'; END IF;
  IF (SELECT count(DISTINCT area) FROM employees WHERE role = 'MANTENIMIENTO_LIMPIEZA') <> 3 THEN
    RAISE EXCEPTION 'FAIL: missing staff area coverage'; END IF;
  IF EXISTS (SELECT 1 FROM employees WHERE password_hash LIKE '%${%') OR EXISTS (SELECT 1 FROM channels WHERE key_hash LIKE '%${%') THEN
    RAISE EXCEPTION 'FAIL: unresolved placeholder'; END IF;
  RAISE NOTICE 'OK: local fixtures are complete';
END $$;

INSERT INTO employees (full_name, email, phone, role, password_hash)
VALUES ('Prueba Recepción', 'prueba.recepcion@villaserena.test', '1', 'RECEPCION', 'x');
INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
VALUES ('Huésped de Prueba', 'prueba.huesped@example.test', '1', 'Guatemalteca', 'DPI', '1');
INSERT INTO room_types (name, description, capacity, base_price) VALUES ('Tipo de prueba', 'x', 4, 100.00);
INSERT INTO rooms (number, floor, room_type_id)
SELECT 'P901', 9, id FROM room_types WHERE name = 'Tipo de prueba';
INSERT INTO reservations (code, guest_id, room_type_id, room_id, checkin_date, checkout_date,
                      guest_count, status, channel, total)
SELECT 'VS-PRB001', h.id, t.id, rm.id, '2030-10-15', '2030-10-17', 2, 'CONFIRMADA', 'RECEPCION', 200.00
FROM guests h, room_types t, rooms rm
WHERE h.email = 'prueba.huesped@example.test' AND t.name = 'Tipo de prueba' AND rm.number = 'P901';

CREATE FUNCTION pg_temp.create_test_reservation(p_code TEXT, p_checkin DATE, p_checkout DATE, p_status TEXT,
                                       p_channel TEXT DEFAULT 'RECEPCION', p_external_id TEXT DEFAULT NULL,
                                       p_with_room BOOLEAN DEFAULT TRUE) RETURNS VOID AS $$
  INSERT INTO reservations (code, guest_id, room_type_id, room_id, checkin_date, checkout_date,
                        guest_count, status, channel, external_identifier, total)
  SELECT p_code, h.id, t.id, CASE WHEN p_with_room THEN rm.id END, p_checkin, p_checkout,
         2, p_status, p_channel, p_external_id, 100.00
  FROM guests h, room_types t, rooms rm
  WHERE h.email = 'prueba.huesped@example.test' AND t.name = 'Tipo de prueba' AND rm.number = 'P901';
$$ LANGUAGE sql;

\echo '== Security, catalogs and reservations'
DO $$ BEGIN
  INSERT INTO employees (full_name, email, phone, role, password_hash)
  VALUES ('Otra', 'PRUEBA.Recepcion@villaserena.test', '1', 'RECEPCION', 'x');
  RAISE EXCEPTION 'FAIL: duplicate employee email accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate employee email rejected';
END $$;

DO $$ BEGIN
  INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
  VALUES ('Otra', 'Prueba.Huesped@Example.test', '1', 'X', 'DPI', '1');
  RAISE EXCEPTION 'FAIL: duplicate guest email accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate guest email rejected';
END $$;

DO $$ BEGIN
  PERFORM pg_temp.create_test_reservation('VS-PRB002', '2030-10-16', '2030-10-18', 'CONFIRMADA');
  RAISE EXCEPTION 'FAIL: overlapping reservation accepted';
EXCEPTION WHEN exclusion_violation THEN RAISE NOTICE 'OK: overlapping reservation rejected by the exclusion constraint';
END $$;

DO $$ BEGIN PERFORM pg_temp.create_test_reservation('VS-PRB003', '2030-10-17', '2030-10-19', 'PENDIENTE_PAGO', 'DIRECTO_WEB'); END $$;
\echo 'OK: back-to-back reservation accepted'
DO $$ BEGIN PERFORM pg_temp.create_test_reservation('VS-PRB004', '2030-10-15', '2030-10-16', 'CANCELADA'); END $$;
\echo 'OK: overlapping cancelled reservation accepted'

DO $$ BEGIN
  INSERT INTO employees (full_name, email, phone, role, password_hash)
  VALUES ('Sin área', 'prueba.sinarea@villaserena.test', '1', 'MANTENIMIENTO_LIMPIEZA', 'x');
  RAISE EXCEPTION 'FAIL: maintenance/cleaning staff without an area accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: maintenance/cleaning staff without an area rejected';
END $$;

DO $$ BEGIN
  INSERT INTO employees (full_name, email, phone, role, area, password_hash)
  VALUES ('Con área', 'prueba.conarea@villaserena.test', '1', 'RECEPCION', 'LIMPIEZA', 'x');
  RAISE EXCEPTION 'FAIL: reception staff with an area accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: reception staff with an area rejected';
END $$;

DO $$ BEGIN PERFORM pg_temp.create_test_reservation('VS-PRB005', '2030-11-03', '2030-11-06', 'CONFIRMADA', 'BOOKING', 'PRB-1', FALSE); END $$;
DO $$ BEGIN
  PERFORM pg_temp.create_test_reservation('VS-PRB006', '2030-11-03', '2030-11-06', 'CONFIRMADA', 'BOOKING', 'PRB-1', FALSE);
  RAISE EXCEPTION 'FAIL: duplicate external identifier accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate channel and external identifier rejected';
END $$;

DO $$ BEGIN
  PERFORM pg_temp.create_test_reservation('VS-PRB007', '2030-12-01', '2031-01-01', 'CONFIRMADA', p_with_room => FALSE);
  RAISE EXCEPTION 'FAIL: 31-night stay accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: 31-night stay rejected';
END $$;

DO $$ BEGIN
  PERFORM pg_temp.create_test_reservation('VS-prb', '2030-12-01', '2030-12-02', 'CONFIRMADA', p_with_room => FALSE);
  RAISE EXCEPTION 'FAIL: invalid reservation code accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: invalid reservation code rejected';
END $$;

DO $$ BEGIN
  INSERT INTO rooms (number, floor, room_type_id)
  SELECT 'P901', 9, id FROM room_types WHERE name = 'Tipo de prueba';
  RAISE EXCEPTION 'FAIL: duplicate room number accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate room number rejected';
END $$;

INSERT INTO room_type_photos (room_type_id, object_key, is_primary)
SELECT id, 'prueba/a.jpg', TRUE FROM room_types WHERE name = 'Tipo de prueba';
DO $$ BEGIN
  INSERT INTO room_type_photos (room_type_id, object_key, is_primary)
  SELECT id, 'prueba/b.jpg', TRUE FROM room_types WHERE name = 'Tipo de prueba';
  RAISE EXCEPTION 'FAIL: two primary photos accepteds';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: second primary photo rejected';
END $$;

\echo '== Accounts, payments, operations, invoicing and system'
INSERT INTO accounts (reservation_id) SELECT id FROM reservations WHERE code = 'VS-PRB001';
INSERT INTO orders (reservation_id) SELECT id FROM reservations WHERE code = 'VS-PRB001';
INSERT INTO charges (account_id, type, concept, quantity, unit_price, total)
SELECT c.id, 'ALOJAMIENTO', 'Alojamiento', 1, 200.00, 200.00
FROM accounts c JOIN reservations r ON r.id = c.reservation_id WHERE r.code = 'VS-PRB001';
INSERT INTO charges (account_id, type, concept, quantity, unit_price, total, order_id)
SELECT c.id, 'ROOM_SERVICE', 'Room Service — Pedido de prueba', 1, 65.00, 65.00, p.id
FROM accounts c JOIN reservations r ON r.id = c.reservation_id JOIN orders p ON p.reservation_id = r.id
WHERE r.code = 'VS-PRB001';
INSERT INTO invoice_series (series, initial_number, last_number) VALUES ('PRB', 1, 0);
INSERT INTO invoices (account_id, series_id, number, buyer_tax_id, buyer_name, total,
                      trade_name, legal_name, hotel_tax_id, tax_address, address)
SELECT c.id, s.id, 1, 'CF', 'Prueba', 265.00, 'a', 'b', 'c', 'd', 'e'
FROM accounts c JOIN reservations r ON r.id = c.reservation_id, invoice_series s
WHERE r.code = 'VS-PRB001' AND s.series = 'PRB';

DO $$ BEGIN
  INSERT INTO charges (account_id, type, concept, quantity, unit_price, total, order_id)
  SELECT account_id, type, concept, quantity, unit_price, total, order_id
  FROM charges WHERE type = 'ROOM_SERVICE' AND concept = 'Room Service — Pedido de prueba';
  RAISE EXCEPTION 'FAIL: second charge for the same order accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: second charge for the same order rejected';
END $$;

DO $$ BEGIN
  INSERT INTO accounts (reservation_id) SELECT id FROM reservations WHERE code = 'VS-PRB001';
  RAISE EXCEPTION 'FAIL: second account for the reservation accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: second account for the reservation rejected';
END $$;

DO $$ BEGIN
  INSERT INTO invoices (account_id, series_id, number, buyer_tax_id, buyer_name, total,
                        trade_name, legal_name, hotel_tax_id, tax_address, address)
  SELECT f.account_id, f.series_id, 2, 'CF', 'X', 1.00, 'a', 'b', 'c', 'd', 'e'
  FROM invoices f JOIN invoice_series s ON s.id = f.series_id WHERE s.series = 'PRB';
  RAISE EXCEPTION 'FAIL: second invoice for the account accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: second invoice for the account rejected';
END $$;

INSERT INTO accounts (reservation_id) SELECT id FROM reservations WHERE code = 'VS-PRB005';
DO $$ BEGIN
  INSERT INTO invoices (account_id, series_id, number, buyer_tax_id, buyer_name, total,
                        trade_name, legal_name, hotel_tax_id, tax_address, address)
  SELECT c.id, s.id, 1, 'CF', 'X', 1.00, 'a', 'b', 'c', 'd', 'e'
  FROM accounts c JOIN reservations r ON r.id = c.reservation_id, invoice_series s
  WHERE r.code = 'VS-PRB005' AND s.series = 'PRB';
  RAISE EXCEPTION 'FAIL: duplicate invoice number accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate number within an invoice series rejected';
END $$;

DO $$ BEGIN
  UPDATE charges SET status = 'ANULADO', void_reason = 'error'
  WHERE type = 'ALOJAMIENTO' AND concept = 'Alojamiento' AND unit_price = 200.00;
  RAISE EXCEPTION 'FAIL: accommodation charge voided';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: voiding the accommodation charge rejected';
END $$;

DO $$ BEGIN
  UPDATE charges SET status = 'ANULADO' WHERE concept = 'Room Service — Pedido de prueba';
  RAISE EXCEPTION 'FAIL: void without a reason accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: void without a reason rejected';
END $$;

DO $$ BEGIN
  INSERT INTO payments (account_id, method, status, amount)
  SELECT c.id, 'RECURRENTE', 'PENDIENTE', 10.00 FROM accounts c JOIN reservations r ON r.id = c.reservation_id
  WHERE r.code = 'VS-PRB001';
  RAISE EXCEPTION 'FAIL: Recurrente payment without a checkout accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: Recurrente payment without a checkout rejected';
END $$;

CREATE FUNCTION pg_temp.create_test_request(p_type TEXT) RETURNS VOID AS $$
  INSERT INTO requests (reservation_id, room_id, type)
  SELECT r.id, r.room_id, p_type FROM reservations r WHERE r.code = 'VS-PRB001';
$$ LANGUAGE sql;

DO $$ BEGIN PERFORM pg_temp.create_test_request('LIMPIEZA'); END $$;
DO $$ BEGIN PERFORM pg_temp.create_test_request('ARTICULOS'); END $$;
\echo 'OK: article request alongside active cleaning accepted'
DO $$ BEGIN
  PERFORM pg_temp.create_test_request('LIMPIEZA');
  RAISE EXCEPTION 'FAIL: second active cleaning request accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: second active cleaning request rejected';
END $$;
DO $$ BEGIN
  UPDATE requests SET status = 'EN_PROCESO'
  WHERE type = 'LIMPIEZA' AND room_id = (SELECT id FROM rooms WHERE number = 'P901');
  RAISE EXCEPTION 'FAIL: in-progress request without an employee accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: in-progress request without an employee rejected';
END $$;
UPDATE requests
SET status = 'ATENDIDA', employee_id = (SELECT id FROM employees WHERE email = 'prueba.recepcion@villaserena.test')
WHERE type = 'LIMPIEZA' AND room_id = (SELECT id FROM rooms WHERE number = 'P901');
DO $$ BEGIN PERFORM pg_temp.create_test_request('LIMPIEZA'); END $$;
\echo 'OK: new cleaning accepted after completing the previous request'

INSERT INTO incidents (room_id, description, blocks_use, reported_by_employee_id)
SELECT h.id, 'Incidencia de prueba', TRUE, e.id
FROM rooms h, employees e WHERE h.number = 'P901' AND e.email = 'prueba.recepcion@villaserena.test';
DO $$ BEGIN
  UPDATE incidents SET status = 'RESUELTA', technician_id = reported_by_employee_id
  WHERE description = 'Incidencia de prueba';
  RAISE EXCEPTION 'FAIL: resolved incident without a solution accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: resolved incident without a solution rejected';
END $$;

DO $$ BEGIN
  INSERT INTO status_history (entity_type, entity_id, new_status, responsible_type)
  VALUES ('RESERVA', 1, 'CONFIRMADA', 'EMPLEADO');
  RAISE EXCEPTION 'FAIL: employee history without an identifier accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: employee history without an identifier rejected';
END $$;

DO $$ BEGIN
  UPDATE channels SET key_hash = 'clave-en-texto-plano' WHERE code = 'BOOKING';
  RAISE EXCEPTION 'FAIL: channel key without a hash accepted';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK: channel key without a hash rejected';
END $$;

INSERT INTO payment_events (event_id, type) VALUES ('evt_prueba', 'intent.succeeded');
DO $$ BEGIN
  INSERT INTO payment_events (event_id, type) VALUES ('evt_prueba', 'intent.succeeded');
  RAISE EXCEPTION 'FAIL: duplicate Recurrente event accepted';
EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK: duplicate Recurrente event rejected';
END $$;

ROLLBACK;
\echo '== All schema checks passed (test data rolled back)'
