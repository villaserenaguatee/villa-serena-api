# Backend integrado — 16 octubre 2026

Rama basada en develop remoto. Reúne el repositorio Java actual y las dos últimas clases de verificación de reserva que estaban en la preparación del frontend. No incluye Java duplicado en el repositorio web, JARs, base de datos, archivos de huéspedes, credenciales ni evidencias privadas.

Incluye acceso unificado/recuperación del personal, códigos de huésped y verificación previa a reservar separados, validaciones de persona/documentos/teléfono, catálogo e inventario compartido, reservas/cuenta/pago/factura, documentos/chat privados, solicitudes y gestión laboral/operativa, correo e idempotencia. Stripe conserva el webhook: regresar a la web no confirma el pago. No se envió correo ni ejecutó pago durante esta entrega.

## Reproducción y datos

Java 21 / Maven. Variables o `.env` excluido; consultar `.env.example`. SMTP/OAuth/JWT/Stripe/base de datos nunca deben exponerse al navegador. Crear claves propias; no reutilizar fixtures de pruebas como secretos del servicio. **La clave JWT de ejemplo antigua fue reutilizada localmente: debe rotarse antes de exponer el entorno**, revocando sesiones de forma coordinada. No se cambió el entorno local durante esta entrega. Los hashes ya presentes en tests son fixtures públicos anteriores, no contraseñas actuales del personal.

Flyway V7–V25 más esquema previo: objetos, documentos/chat, operaciones/laboral, catálogo, acceso, contacto, verificación, país emisor, Google, inventario y recuperación. V24 conserva reservas y controla cupo físico compartido; no se adjunta dump. Perfiles demo/test usan datos ficticios; no activarlos en producción. Revisar números de habitaciones e historial Flyway antes de aplicar a otra base: las migraciones tienen precondiciones, no son un importador genérico ni se repiten manualmente. Identificaciones y credenciales locales del personal no se publican como migraciones.

CORS con orígenes concretos; WEB_URL y APP_URL_RETORNO del frontend. En despliegue usar HTTPS/TLS y callbacks exactos. Documentos privados no deben publicarse como carpeta estática. Revisar acceso Actuator/OpenAPI para el despliegue.

## Estado

**No se ejecutaron pruebas ni compilación por petición del usuario.** Solo revisión de los archivos incluidos. No acredita recorridos nuevos ni ausencia de vulnerabilidades. Pendientes: cuotas distribuidas, auditoría de dependencias Java/imagen, carreras de renovación y casos interactivos específicos. FEL/operaciones bancarias reales no implementadas. Personalización Stripe aplazada, conservando el Sandbox actual.

Sin merge, despliegue ni PR.
