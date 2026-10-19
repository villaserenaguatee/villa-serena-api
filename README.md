# villa-serena-api

API de Villa Serena (Spring Boot 4.1, Java 21, PostgreSQL 17 + Flyway). Ver `AGENTS.md` y la documentación en `villa-serena-docs`.

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

## Migraciones y datos de demostración

| Carpeta | Contenido | Cuándo se aplica |
|---|---|---|
| `src/main/resources/db/migration` | Esquema (V1 a V5 y las migraciones nuevas). | Siempre. |
| `src/main/resources/db/demo` | Datos ficticios de presentación (`V6__datos_iniciales.sql`: hotel, habitaciones, tarifas, menú, canales simulados y empleados de prueba) y la validación de sus hashes. | Solo con el perfil `demo`. |

- **Local y demostración:** activa el perfil con `SPRING_PROFILES_ACTIVE=demo` y define `DEMO_PASSWORD_HASH`, `CANAL1_KEY_HASH` y `CANAL2_KEY_HASH` (ver `.env.example`). Si falta alguno, el API no arranca y no carga datos.
- **Sin perfil (configuración destinada a producción):** solo se crea el esquema. No se cargan empleados, canales, tarifas ni habitaciones ficticias, y no se piden los hashes de demostración. Nunca actives `demo` en producción.
- **Datos mínimos del sistema:** por ahora ninguno va en las migraciones; la configuración del hotel, el catálogo y los canales reales los registra Administración.

**Bases que ya aplicaron V1 a V6** (antes V6 estaba en `db/migration`): el archivo se movió sin cambiar su contenido, así que su checksum es el mismo y Flyway valida sin reparar nada. Con el perfil `demo` todo sigue igual; sin el perfil, el API arranca y los datos ya cargados se conservan (no se borra nada). No edites migraciones ya aplicadas: agrega una nueva.

**Numeración:** los números de versión son únicos entre `db/migration` y `db/demo`. Coordina el siguiente número con el equipo antes de crear una migración en cualquiera de las dos carpetas.

## Probar el esquema

`scripts/probar-esquema.sql` **no es una migración ni sirve para inicializar una base**: es una prueba. Comprueba los datos de demostración y las restricciones de la base de datos (EXCLUDE de reservas, correos únicos, un cargo por pedido, una factura por cuenta, etc.). Corre dentro de una transacción que se deshace al final, así que **no deja datos**. Si algo falla, se detiene y muestra `FALLO ...`. Necesita una base migrada con el perfil `demo`.

Las pruebas automáticas (`./mvnw test`) lo ejecutan sobre una base temporal con el perfil `demo` y comprueban que sin el perfil no se carga ningún dato ficticio.

**Sobre una base ya migrada por Flyway** (con `SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run`):

```bash
docker exec -i villa-serena-dev-postgres-1 psql -U villaserena -d villaserena < scripts/probar-esquema.sql
```

**Sin Spring, en una base temporal** (aplica el esquema y los datos de demostración con valores de prueba en los placeholders y la borra al final):

```bash
C=villa-serena-dev-postgres-1; DB=vs_prueba_esquema
docker exec $C psql -U villaserena -d postgres -c "CREATE DATABASE $DB"
for f in $(ls src/main/resources/db/migration/V*.sql src/main/resources/db/demo/V*.sql | awk -F/ '{print $NF" "$0}' | sort -V | cut -d' ' -f2); do
  sed -e 's/\${demo_password_hash}/hash-de-prueba-no-real/' \
      -e 's/\${canal1_key_hash}/'"$(printf '0%.0s' {1..63})1"'/' \
      -e 's/\${canal2_key_hash}/'"$(printf '0%.0s' {1..63})2"'/' \
      "$f" | docker exec -i $C psql -q -U villaserena -d $DB -v ON_ERROR_STOP=1
done
docker exec -i $C psql -U villaserena -d $DB < scripts/probar-esquema.sql
docker exec $C psql -U villaserena -d postgres -c "DROP DATABASE $DB WITH (FORCE)"
```

## Pagos con Recurrente (issue #54, ADR-017)

El API crea checkouts alojados en GTQ con tarjetas, por el total de la reserva o el saldo de la estadía. Configura `RECURRENTE_PUBLIC_KEY`, `RECURRENTE_SECRET_KEY` y `RECURRENTE_WEBHOOK_SECRET` en tu entorno local, según `.env.example`. La clave pública queda disponible para integraciones que la necesiten; el checkout del backend solo requiere la secreta. Para pruebas usa una cuenta Sandbox, sin dinero real.

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
