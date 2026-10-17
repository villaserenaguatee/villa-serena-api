-- Control independiente de intentos del formulario sin selector de método.
-- No altera contraseñas, OTP, sesiones ni contadores de los accesos anteriores.
CREATE TABLE acceso_unificado_control (
 correo varchar(150) PRIMARY KEY,
 intentos int NOT NULL DEFAULT 0 CHECK(intentos>=0),
 bloqueado_hasta timestamptz
);
