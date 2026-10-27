# villa-serena-api

API de Villa Serena (Spring Boot 4.1, Java 21, PostgreSQL 17 + Flyway). Ver `AGENTS.md` y la documentación en `villa-serena-docs`.

## Entornos y arranque

Tres perfiles: **local** (API + PostgreSQL), **dev** (servicios locales y R2 remoto) y **prod** (despliegue con S3 e infraestructura de producción). `local` es el predeterminado con Maven; `test` se reserva para pruebas. `demo` se retira.

La [guía paso a paso de entornos](docs/ENTORNOS.md) contiene las variables mínimas, comandos de Docker/Maven, producción y bootstrap del primer administrador. Empieza por esa guía.

## Contrato y verificación

El único contrato canónico está en [villa-serena-docs](https://github.com/villaserenaguatee/villa-serena-docs/blob/main/openapi.yaml).
`api-contract.json` fija su revisión para local y CI. No se mantiene una copia YAML/JSON en API.
La [guía de adopción](docs/CONTRATO_API.md) contiene los comandos, la configuración
del checkout y el seguimiento de las diferencias. Las peticiones de negocio no
descargan ni generan el contrato.

`./mvnw -B verify` prueba y empaqueta el API, y exporta su OpenAPI a
`target/openapi-spring.json`. La comparación con docs se ejecuta por separado:
es diagnóstica hasta reconciliar las operaciones; las comprobaciones de revisión,
herramientas, DTO y permisos sí son obligatorias en CI.

La [guía de seguridad de CI/CD](docs/SEGURIDAD_CI.md) describe los gates de Trivy,
SonarQube y cobertura JaCoCo, su configuración y las excepciones justificadas.

## Esquema y datos locales (#59)

Flyway versiona **solo el esquema**, en `src/main/resources/db/migration`, con nombres y comentarios SQL en inglés. Todos los perfiles usan esa misma carpeta. Los valores de negocio (`CONFIRMADA`, `RECEPCION`, etc.) conservan su representación actual. JDBC y JPA mapean el esquema en inglés a los DTO y atributos Java existentes.

Ningún perfil carga seeds al arrancar. El perfil `local` habilita los adaptadores de archivos locales. Los datos ficticios están en `scripts/demo/load-data.sql`, fuera de los recursos de la aplicación y del JAR. Las reservas ficticias automáticas existen únicamente en el código de pruebas. El alta opcional del primer administrador sigue siendo una configuración explícita mediante `BOOTSTRAP_ADMIN_ENABLED=true`, `ADMIN_EMAIL`, `ADMIN_NAME` y `ADMIN_PASSWORD`; está deshabilitada por defecto. Ver el [checklist de bootstrap](docs/ENTORNOS.md#bootstrap-primer-administrador).

### Carga local explícita

1. Arranca el API y espera a que Flyway termine y `/actuator/health` responda correctamente.
2. Exporta `DEMO_PASSWORD_HASH`, `CANAL1_KEY_HASH` y `CANAL2_KEY_HASH` en tu terminal con valores privados válidos: un hash BCrypt de la contraseña local y dos hashes SHA-256 hexadecimales de 64 caracteres para las claves de canal. El script los lee del entorno de `psql`; no se envían al API. No ejecutes `.env` con `source`: Spring lo interpreta como propiedades.
3. Ejecuta la carga contra un contenedor **local y con las tablas de negocio vacías**. Ajusta el nombre, usuario y nombre de la instancia a tu entorno:

```bash
DEMO_CONTAINER=villa-serena-local-postgres-1
docker exec -i \
  -e DEMO_PASSWORD_HASH -e CANAL1_KEY_HASH -e CANAL2_KEY_HASH \
  "$DEMO_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U villaserena -d villaserena \
  < scripts/demo/load-data.sql
```

El script valida los hashes, comprueba que el esquema esté migrado y rechaza tablas de negocio con datos. Ejecuta la carga en una transacción: un fallo revierte los registros insertados. Una segunda ejecución falla sin duplicar ni borrar datos. Nunca lo ejecutes en producción. No se agrega un endpoint HTTP para cargar fixtures.

### Historial anterior y verificación

Esta reorganización está autorizada antes del primer despliegue de producción: cambia nombres, descripciones y checksums de las migraciones anteriores. Los contenedores locales con el historial anterior necesitan una instancia nueva; conserva antes cualquier dato que necesites. No se ejecuta `clean` de Flyway ni se repara el historial automáticamente. Después de adoptar este esquema, conserva las migraciones aplicadas y utiliza versiones nuevas para evolucionarlo. V6 ya no existe: contenía el seed antiguo y su número queda reservado.

```bash
./mvnw -B verify
```

Las pruebas requieren Docker, aplican Flyway en contenedores temporales y cargan el mismo script local después de las migraciones, fuera de su historial. Comprueban el arranque vacío, la carga posterior, las restricciones y la conservación de un catálogo propio durante una actualización.

`scripts/test-schema.sql` comprueba las restricciones sobre una instancia local con los datos de prueba cargados. Su transacción se revierte al terminar:

```bash
docker exec -i "$DEMO_CONTAINER" psql -X -U villaserena -d villaserena < scripts/test-schema.sql
```

## Pagos con Recurrente (issue #54, ADR-017)

El API crea checkouts alojados en GTQ con tarjetas, por el total de la reserva o el saldo de la estadía. Configura `RECURRENTE_PUBLIC_KEY`, `RECURRENTE_SECRET_KEY` y `RECURRENTE_WEBHOOK_SECRET` en tu entorno local, según la guía de entornos. La clave pública queda disponible para integraciones que la necesiten; el checkout del backend solo requiere la secreta. Para pruebas usa una cuenta Sandbox, sin dinero real.

Registra `POST /api/v1/pagos/recurrente/webhook` en el dashboard de Recurrente con una URL accesible por el proveedor. La verificación requiere el cuerpo original y los tres encabezados Svix: `svix-id`, `svix-timestamp`, `svix-signature`. El timestamp admite cinco minutos de diferencia. El mensaje se deduplica por `svix-id` y el pago se confirma consultando el checkout y conciliando su monto y moneda GTQ; la redirección al navegador no confirma el cobro.

La [guía oficial de webhooks](https://docs.recurrente.com/guias-espanol/comenzar/webhooks) documenta `intent.succeeded` para cuentas nuevas y Sandbox. Los nombres `checkout.succeeded` / `payment.succeeded` del ADR no corresponden a ese formato. El endpoint también admite un aviso `checkout.expired` únicamente si la consulta confirma `expired`; el catálogo actual no garantiza su emisión. El job local consulta y vence los checkouts cada minuto después del plazo de 30 minutos, sin depender de un aviso de vencimiento. Un pago `payment_in_progress` mantiene la reserva pendiente hasta poder reconciliarlo.

Los reembolsos usan `intent_id` y una llave idempotente estable; solo `status: succeeded` permite cancelar con reembolso. Una respuesta pendiente requiere consultar/reintentar la operación, sin marcarla como reembolsada.

Flyway V27 renombra las columnas de pasarela y la tabla de eventos, agrega `RECURRENTE` y conserva los pagos y responsables históricos `STRIPE`. No convierte cobros históricos en cobros de Recurrente ni los envía a su API. Si su cancelación requiere reembolso, responde `PAGO_LEGADO_REQUIERE_CONCILIACION` y conserva la reserva hasta conciliar con el proveedor original. Antes de migrar una instalación con checkouts Stripe aún pendientes, deben conciliarse con el proveedor anterior. El contrato de docs debe reconciliar esa transición y web/app adoptar su revisión y tipos con `RECURRENTE`.

Validación automática: `./mvnw test` usa PostgreSQL 17 mediante Testcontainers; `PasarelaRecurrenteClientTest` valida el contrato HTTP y firmas con datos ficticios. La prueba completa en Sandbox requiere claves configuradas y un endpoint registrado en Recurrente.

## Movimientos de insumos

`GET` y `POST /api/v1/gestion/movimientos` usan el módulo `inventario`: un controlador específico adapta el JSON existente, `MovimientosInsumosService` coordina la transacción, `MovimientoInsumoPeticion` convierte los datos a campos tipados y `MovimientoInsumo` aplica las reglas sin depender de Spring. El adaptador JDBC conserva las tablas, versiones, auditoría y bloqueos existentes, incluidos los compartidos con compras y órdenes.

La petición conserva el envoltorio `{id, version, datos}` y sus campos adicionales por compatibilidad con la web. El servicio comprueba reenvíos y versiones antes de interpretar el movimiento. Un movimiento ya registrado es inmutable; su reenvío idéntico devuelve el registro original sin modificar nuevamente las existencias. Solo Administración puede consultar o registrar movimientos. La prueba `MovimientosInsumosIntegrationTest` cubre esos comportamientos, rollback y solicitudes concurrentes sobre PostgreSQL 17.

## Hora estimada de llegada (issue #57)

La creación pública por tipo (`POST /api/v1/publico/reservas`) y por oferta
(`POST /api/v1/publico/ofertas/{slug}/reservas`) acepta `horaEstimadaLlegada`,
opcional, como cadena `HH:mm` de `00:00` a `23:59`, por ejemplo `"18:30"`.
Omitirla o enviar `null` significa sin indicar; una cadena vacía, segundos o zona
horaria responden 400 `DATOS_INVALIDOS`. Es una hora local de `America/Guatemala`,
independiente de la política y las validaciones del check-in.

`GET /api/v1/reservas/{codigo}`, autorizado para Recepción, devuelve el campo con
el mismo formato o `null`. Flyway V28 agrega una columna nullable sin valor por
defecto ni actualización de reservas históricas. Un reenvío idéntico conserva la
reserva; cambiar la hora con la misma `Idempotency-Key` responde 409
`BOOKING_REQUEST_CONFLICT`. Los reintentos anteriores sin hora siguen siendo válidos.

El contrato canónico se mantiene en el repositorio de docs; su actualización y la
conexión del campo en la web se coordinan en [docs #22](https://github.com/villaserenaguatee/villa-serena-docs/issues/22).
El API consume la revisión de docs fijada en `api-contract.json` (adopción #58).
