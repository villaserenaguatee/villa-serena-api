-- =====================================================================
-- Callback de Flyway del perfil "demo" (no es una migración versionada).
-- Antes de migrar, comprueba que DEMO_PASSWORD_HASH, CANAL1_KEY_HASH y
-- CANAL2_KEY_HASH tengan un valor válido (ver .env.example). Si faltan,
-- el API no arranca y no se carga ningún dato de la demostración.
-- =====================================================================
DO $validar$
BEGIN
  IF '${demo_password_hash}' !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$' THEN
    RAISE EXCEPTION 'Perfil demo: DEMO_PASSWORD_HASH falta o no es un hash BCrypt (ver .env.example)';
  END IF;
  IF '${canal1_key_hash}' !~ '^[0-9a-f]{64}$' OR '${canal2_key_hash}' !~ '^[0-9a-f]{64}$' THEN
    RAISE EXCEPTION 'Perfil demo: CANAL1_KEY_HASH o CANAL2_KEY_HASH falta o no es un SHA-256 en hexadecimal (ver .env.example)';
  END IF;
END
$validar$;
