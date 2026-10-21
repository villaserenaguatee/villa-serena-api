# Arranque por entorno

Los únicos perfiles de ejecución son **local**, **dev** y **prod**. `test` es exclusivo de las pruebas automatizadas. `local` es el predeterminado al ejecutar Maven sin indicar perfil; la imagen Docker usa `prod` por defecto y cada Compose selecciona el suyo explícitamente. Los perfiles desconocidos o combinados hacen fallar el arranque.

| Perfil | Servicios | Archivos privados | Configuración |
|---|---|---|---|
| `local` | API + PostgreSQL | Adaptador local persistente, sin almacenamiento remoto | `.env.local` |
| `dev` | API + PostgreSQL + Mailpit + Prometheus + Grafana; R2 remoto | R2 remoto | `.env.dev` |
| `prod` | API conectada a infraestructura provisionada | Amazon S3 | Variables inyectadas; SSM en el despliegue existente |

`dev` sirve para desarrollar y demostrar las integraciones. R2 se prueba directamente en Cloudflare desde tu computadora; no se ejecuta un emulador de objetos. Mailpit permanece. El aprovisionamiento y la comprobación remota se describen en la [guía R2/S3 de infra](https://github.com/villaserenaguatee/villa-serena-infra/blob/main/docs/ALMACENAMIENTO.md).

No hace falta un cuarto perfil `staging`: actualmente no hay un despliegue de preproducción separado. El perfil antiguo `demo` se retira. Ningún entorno carga datos ficticios al arrancar. Flyway aplica solo el esquema.

Ejecuta los comandos desde la raíz de **villa-serena-api**. Docker Compose administra los servicios; el perfil Spring configura el API. No necesitas levantar también el Compose antiguo de `infra`. Usa un solo stack local a la vez para evitar conflictos de puertos.

## Variables indispensables

| Variables | `local` | `dev` | `prod` |
|---|---|---|---|
| `JWT_SECRET` | Obligatoria | Obligatoria | Obligatoria; clave propia de producción |
| `DB_PASSWORD` | Obligatoria para Compose/Maven | Obligatoria para Compose/Maven | No se usa |
| `DB_USER`, `DB_NAME` | Opcionales; `villaserena` | Opcionales; `villaserena` | No se usan |
| `DB_URL` | Maven: opcional, `jdbc:postgresql://localhost:5432/villaserena`; Compose lo configura | Igual | No se usa |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Alternativa estándar de Spring si se inyectan | Igual | Obligatorias; Compose/CD dan URL y usuario del servidor actual |
| `WEB_URL`, `CORS_ORIGENES` | Opcionales; `http://localhost:3000` | Igual | Obligatorias; orígenes HTTPS, separados por comas en CORS |
| `GRAFANA_ADMIN_PASSWORD` | No se necesita | Obligatoria para el stack completo | Grafana se administra aparte |
| `STORAGE_ENDPOINT`, `STORAGE_REGION` | No se necesitan | Endpoint HTTPS de R2 y región `auto` | Endpoint vacío; región S3, normalmente `us-east-1` |
| `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` | No se necesitan | Credenciales R2 acotadas al bucket privado | Se omiten: rol EC2 y credenciales temporales |
| `STORAGE_BUCKET_PRIVATE`, `ARCHIVOS_URL_PUBLICA` | No se necesitan para archivos privados locales | Bucket privado R2 y dominio público de assets | Bucket privado S3 y URL pública CloudFront |
| `BOOTSTRAP_ADMIN_ENABLED` | Opcional; `false` | Opcional; `false` | Opcional; `false` |
| `ADMIN_EMAIL`, `ADMIN_NAME`, `ADMIN_PASSWORD` | Solo al habilitar bootstrap | Igual | Igual |

`JWT_SECRET` es Base64 de al menos 32 bytes aleatorios, usado por HS256. Genera un valor por entorno con `openssl rand -base64 32` y pégalo en su configuración; no lo subas a Git. Para las contraseñas locales puedes generar valores con `openssl rand -hex 24`.

Las plantillas contienen campos vacíos que debes completar. En los archivos locales usa `CLAVE=valor`, sin comillas, expansiones de shell ni comentarios al final del valor: Spring los lee como propiedades y Compose como dotenv. No uses `source`. Las variables exportadas de tu terminal tienen prioridad: elimina valores viejos como `SPRING_PROFILES_ACTIVE`, `DB_URL` o `SPRING_DATASOURCE_*` si interfieren.

## 1. Local: API + PostgreSQL

- [ ] Instala Docker con Compose **2.24.4 o posterior** e inicia su daemon.
- [ ] Copia la plantilla:

```bash
cp .env.local.example .env.local
```

- [ ] Completa únicamente `DB_PASSWORD` y `JWT_SECRET`. Deja `BOOTSTRAP_ADMIN_ENABLED=false` salvo que quieras crear el primer administrador.
- [ ] Arranca:

```bash
docker compose --env-file .env.local -f compose.local.yml up -d --build --wait
curl --fail http://localhost:8080/actuator/health
```

- [ ] La respuesta debe indicar `UP`. Para revisar problemas:

```bash
docker compose --env-file .env.local -f compose.local.yml logs --tail=100 api
```

Un API saludable puede tener todas las tablas de negocio vacías: el hotel y sus catálogos se configuran después del arranque.

Los archivos privados se guardan mediante el adaptador local en PostgreSQL; no se abre ninguna conexión a un bucket remoto. Las URLs de descarga apuntan a `API_PUBLIC_URL` (por defecto `http://localhost:8080`). El correo no está disponible en este modo: las operaciones que envíen correo necesitan un SMTP; el outbox reintenta los envíos fallidos. Tampoco se simulan pagos ni push externos.

### API con Maven, PostgreSQL en Docker

Necesitas Java 21; Maven lo proporciona el wrapper.

- [ ] Con la misma `.env.local`, arranca solo PostgreSQL:

```bash
docker compose --env-file .env.local -f compose.local.yml up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- [ ] Comprueba `/actuator/health` en otra terminal. No ejecutes simultáneamente el API de Compose y Maven en el puerto 8080. Si ya levantaste todo el stack, primero ejecuta `docker compose --env-file .env.local -f compose.local.yml stop api`.

Si cambias `DB_NAME`, `DB_USER` o `POSTGRES_PORT`, ajusta también `DB_URL` para Maven; Compose establece su URL interna automáticamente. `API_PORT` cambia el puerto publicado por Docker; actualiza `API_PUBLIC_URL` si lo modificas. Maven usa `SERVER_PORT` si se cambia su puerto.

## 2. Dev: todas las integraciones locales

- [ ] Detén el stack `local` si está ejecutándose (ver sección de parada).
- [ ] Copia la plantilla:

```bash
cp .env.dev.example .env.dev
```

- [ ] Completa `DB_PASSWORD`, `JWT_SECRET` y `GRAFANA_ADMIN_PASSWORD`.
- [ ] Para probar archivos, provisiona R2 según la guía de infra y completa `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY`, `STORAGE_BUCKET_PRIVATE` y `ARCHIVOS_URL_PUBLICA`. La región es `auto` y `STORAGE_PATH_STYLE=true`. No se crean buckets desde el arranque del API.
- [ ] Las credenciales de Recurrente son opcionales y de Sandbox. Sin configurar R2 el API puede arrancar, pero las operaciones de archivos fallarán hasta configurarlo.
- [ ] Arranca y comprueba:

```bash
docker compose --env-file .env.dev -f compose.dev.yml up -d --build --wait
curl --fail http://localhost:8080/actuator/health
docker compose --env-file .env.dev -f compose.dev.yml ps -a
```

- [ ] Si vas a probar archivos, ejecuta la comprobación remota explícita de la sección siguiente. Un API `UP` no certifica conectividad con R2.

| Servicio | Dirección |
|---|---|
| API / Swagger | `http://localhost:8080` / `/swagger-ui/index.html` |
| Mailpit | `http://localhost:8025` (SMTP: `localhost:1025`) |
| R2 | Endpoint y dominio remoto obtenidos desde Terraform |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |

`STORAGE_ENDPOINT` es el endpoint S3 de tu cuenta R2, también usado para firmar enlaces privados. `ARCHIVOS_URL_PUBLICA` es el dominio del bucket público: no sirve para firmar URLs privadas. El navegador debe poder acceder a ambos. Las credenciales permanecen exclusivamente en el backend.

Para comprobar R2 con el SDK real (requiere los valores válidos en `.env.dev`):

```bash
STORAGE_SMOKE_TEST=true ./mvnw -B -Dtest=AlmacenS3RemoteTest test
```

En PowerShell, establece `$env:STORAGE_SMOKE_TEST='true'`, ejecuta Maven y elimina esa variable al terminar. La prueba lee `.env.dev` como propiedades, escribe un objeto único bajo `_checks/`, verifica lectura y descarga firmada, comprueba que la descarga anónima esté bloqueada y elimina el objeto. No carga datos SQL ni necesita arrancar el API. La prueba falla si no puede limpiar; con versionado S3, la eliminación deja una versión anterior protegida y un marcador de borrado.

### API con Maven, integraciones en Docker

- [ ] Si el API de Compose está ejecutándose, detenlo con `docker compose --env-file .env.dev -f compose.dev.yml stop api`.
- [ ] Arranca solo las dependencias y luego el API:

```bash
PROMETHEUS_CONFIG=./docker/prometheus-host.yml docker compose --env-file .env.dev -f compose.dev.yml up -d postgres mailpit prometheus grafana
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En PowerShell usa `$env:PROMETHEUS_CONFIG='./docker/prometheus-host.yml'` antes del comando Docker y elimina esa variable al volver al API en Docker. Para volver al modo Docker, ejecuta el comando de arranque normal de `dev`; recreará Prometheus con el destino `api:8080`. El destino de Maven presupone el puerto 8080.

## 3. Producción

Este cambio conserva el despliegue actual en EC2: `infra` provisiona PostgreSQL y la red `villa-serena-backend`; el Compose de producción ejecuta **solo el API**, conectado a esa infraestructura. No levantes un segundo PostgreSQL ni el stack `dev` en producción.

- [ ] Prepara la infraestructura de producción según la documentación de `infra` y verifica la conectividad desde la red del API.
- [ ] Publica una imagen verificada en GHCR con la arquitectura del servidor (el despliegue actual usa ARM64).
- [ ] Configura en SSM, bajo `/villa-serena/prod/api/`, al menos `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `WEB_URL` y `CORS_ORIGENES`. El Compose proporciona la URL y el usuario actuales; si cambian, configura también `SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`.
- [ ] Aplica `aws/storage` de infra después de provisionar el rol EC2. Copia sus salidas `private_bucket`, `storage_region` y `public_assets_url` a SSM como `STORAGE_BUCKET_PRIVATE`, `STORAGE_REGION` y `ARCHIVOS_URL_PUBLICA`. Deja `STORAGE_ENDPOINT` vacío y omite `STORAGE_ACCESS_KEY_ID` y `STORAGE_SECRET_ACCESS_KEY`: el SDK usa credenciales temporales del rol EC2. Configura `APP_DOWNLOAD_URL` si publicas el APK.
- [ ] Configura SMTP y Recurrente cuando vayas a usar esas funciones. `WEB_URL` y los orígenes CORS deben ser HTTPS; no se aceptan comodines ni rutas. No copies credenciales locales a producción.
- [ ] Mantén `BOOTSTRAP_ADMIN_ENABLED=false`, o configura el alta inicial explícita de la siguiente sección.
- [ ] Ejecuta el flujo CD existente. Ahora publica `compose.prod.yml`; el script obtiene secretos de SSM y comprueba `/actuator/health`. Spring no importa `.env.local`, `.env.dev` ni el `.env` antiguo con `prod`.
- [ ] Verifica la salud y el acceso a través del proxy HTTPS. Swagger/OpenAPI quedan deshabilitados en producción. No expongas Actuator directamente a Internet desde el proxy.

Alternativa manual en un servidor ya provisionado, con las mismas condiciones:

```bash
cp .env.prod.example .env.prod
# Completa los valores reales y protege el archivo:
chmod 600 .env.prod
# Sustituye la referencia por la imagen publicada que vas a desplegar:
IMAGE_URI=ghcr.io/organizacion/imagen:revision docker compose --env-file .env.prod -p villa-serena-api -f compose.prod.yml up -d --wait
curl --fail http://127.0.0.1:8080/actuator/health
```

El modo manual y CD son alternativas: no gestiones la misma instalación simultáneamente con dos Compose. CD instala el archivo como `/opt/villa-serena/api/compose.yml` y usa su `.env` privado; no usa los ejemplos locales.

Para ejecutar el JAR/Maven con `prod`, inyecta las variables obligatorias mediante el entorno de tu proceso y selecciona `SPRING_PROFILES_ACTIVE=prod`; no se importa ningún archivo local automáticamente. Por ejemplo, con las variables ya inyectadas:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Bootstrap: primer administrador

El bootstrap no es un seed de Flyway. Es un alta operativa opcional después de migrar el esquema, disponible en los tres entornos.

- [ ] Antes del primer arranque, establece `BOOTSTRAP_ADMIN_ENABLED=true` y completa `ADMIN_EMAIL`, `ADMIN_NAME`, `ADMIN_PASSWORD` en la configuración de ese entorno (SSM en producción).
- [ ] Usa una contraseña temporal aleatoria de **al menos 12 caracteres y hasta 72 bytes UTF-8**. No se imprime ni guarda en texto plano en la tabla.
- [ ] Arranca/recrea el API; se crea un único usuario `ADMIN`, activo y obligado a cambiar la contraseña. Un error de configuración habilitada detiene el arranque con un mensaje sin secretos.
- [ ] Inicia sesión y cambia la contraseña desde la aplicación (`POST /api/v1/auth/cambiar-contrasena`).
- [ ] Vuelve a `BOOTSTRAP_ADMIN_ENABLED=false`, retira `ADMIN_EMAIL`, `ADMIN_NAME`, `ADMIN_PASSWORD` y recrea/reinicia el API (o vuelve a desplegar si usas SSM/CD).

Si ya existe cualquier administrador, no se crea otro ni se modifica su contraseña aunque cambie el correo configurado. Un bloqueo transaccional de PostgreSQL serializa arranques simultáneos. No es una herramienta de recuperación de cuentas ni para crear administradores adicionales.

Después de cambiar la contraseña inicial, Administración utiliza las cuentas reales del login:

- `GET /api/v1/admin/empleados`: lista cuentas; admite filtros `rol` y `estado`.
- `POST /api/v1/admin/empleados`: crea una cuenta activa y devuelve una contraseña temporal una sola vez.
- `PATCH /api/v1/admin/empleados/{id}`: edita nombre, teléfono, rol y área; el correo permanece fijo.
- `PATCH /api/v1/admin/empleados/{id}/estado`: activa o desactiva la cuenta.
- `POST /api/v1/admin/empleados/{id}/restablecer-contrasena`: devuelve una nueva contraseña temporal y revoca los refresh anteriores.

Todas requieren un JWT de `ADMIN` con contraseña definitiva. No puedes cambiar tu propio rol ni desactivar tu cuenta. Guarda y entrega la contraseña temporal de forma privada: no se envía por correo ni puede consultarse después. El empleado debe cambiarla al iniciar sesión. Estas operaciones no necesitan integraciones externas ni fixtures.

En la web, `/api/admin/empleados` corresponde al BFF de Next.js, que llama a `/api/v1/admin/empleados` en Spring. Si pruebas directamente el API, incluye `/api/v1`.

Si prefieres cargar fixtures en una instancia local vacía, deja el bootstrap apagado: el script manual rechaza tablas de negocio que ya contienen registros. Ver [carga local explícita](../README.md#carga-local-explícita). No ejecutes fixtures en producción.

## Logs en local y dev

Ambos perfiles escriben en consola automáticamente: arranque e integraciones en `INFO`, código del API en `DEBUG` y una línea por petición HTTP con método, ruta, estado y duración. Incluye rechazos de seguridad (401/403), validaciones, rutas inexistentes (404) y errores (500). Los errores de negocio muestran su código en DEBUG; las excepciones inesperadas conservan su stack trace.

```text
HTTP GET /api/v1/admin/empleados -> 200 (12 ms)
HTTP GET /api/v1/ruta-inexistente -> 404 (3 ms)
```

Las trazas HTTP no incluyen query, cabeceras ni cuerpos. Se mantiene `INFO` para bibliotecas de Spring y terceros: DEBUG/TRACE global puede imprimir DTO con credenciales, tokens o detalles de integraciones. Estos valores por defecto no requieren variables nuevas; una configuración explícita `LOGGING_LEVEL_*` en tu entorno puede sobrescribirlos.

Después de incorporar cambios de código o configuración, reconstruye y recrea únicamente el API del entorno que estés usando (con sus dependencias ya levantadas):

```bash
# local
docker compose --env-file .env.local -f compose.local.yml up -d --build --no-deps api
docker compose --env-file .env.local -f compose.local.yml logs -f --tail=100 api

# dev (alternativa a local)
docker compose --env-file .env.dev -f compose.dev.yml up -d --build --no-deps api
docker compose --env-file .env.dev -f compose.dev.yml logs -f --tail=100 api
```

También funciona `docker logs -f --tail=100 NOMBRE_DEL_CONTENEDOR_API` (consulta el nombre con `docker ps`). Un `docker restart` reutiliza la imagen anterior; para incorporar estos cambios necesitas la reconstrucción. Con `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` o `dev`, los mismos logs aparecen directamente en esa terminal. Producción conserva sus niveles habituales y no activa este registro HTTP de desarrollo.

## Parada y comprobaciones

Cada entorno local tiene su propio volumen. Detener no borra datos:

```bash
docker compose --env-file .env.local -f compose.local.yml down
# O, si estás usando dev:
docker compose --env-file .env.dev -f compose.dev.yml down
```

No agregues `-v` salvo que decidas borrar los volúmenes. Cambiar `DB_PASSWORD` en el dotenv no cambia la contraseña de una instancia ya inicializada: conserva la anterior o rótala explícitamente.

Pruebas automatizadas (requieren Docker, no requieren tus archivos dotenv):

```bash
./mvnw -B clean verify
```

`test` usa Testcontainers, secretos públicos exclusivos de prueba y carga fixtures después de Flyway. Las pruebas de esquema vacío usan `local` y `prod` con configuración aislada. El nombre del antiguo test `MigracionesDemoTests` alude a los datos ficticios, no a un perfil disponible.

## Desde la configuración anterior

- [ ] Conserva tu `.env` anterior como referencia privada; ya no se importa automáticamente. Copia solo los valores que corresponden a `.env.local` o `.env.dev`. No renombres ni borres volúmenes existentes sin respaldar lo que necesitas.
- [ ] Sustituye `SPRING_PROFILES_ACTIVE=demo` por `local` o `dev`, según necesites integraciones; no combines perfiles.
- [ ] Sustituye `compose.demo.yml` por `compose.local.yml` o `compose.dev.yml`, con su `--env-file` explícito. El antiguo `compose.api.yml` pasa a `compose.prod.yml`.
- [ ] El helper opcional pasa de `scripts/start-demo.mjs` a `scripts/start-dev.mjs`. **Abre un túnel público temporal**: solo ejecútalo deliberadamente cuando quieras compartir el API; no forma parte de ninguno de los arranques normales.
- [ ] El Compose histórico de `infra` sigue disponible para quienes aún lo usen; los comandos de esta guía son la entrada para estos nuevos entornos. No ejecutes ambos sobre los mismos puertos.

## Desde MinIO hacia R2/S3

- [ ] Provisiona los destinos y configura las nuevas variables `STORAGE_*`; las antiguas `MINIO_*` se retiran y no se usan como alias.
- [ ] Si tienes archivos que conservar, cópialos a los nuevos buckets manteniendo sus claves relativas antes de retirar el servicio anterior. Actualiza las URLs públicas que apuntaban al host anterior; cambiar `ARCHIVOS_URL_PUBLICA` no reescribe URLs absolutas ya guardadas.
- [ ] Recrea el stack dev con su comando habitual. Los servicios MinIO anteriores quedan como huérfanos: tras respaldar/copiar los objetos, puedes retirarlos deliberadamente con `down --remove-orphans`. Los volúmenes antiguos no se borran automáticamente.
- [ ] Comprueba carga, lectura, URL firmada y rechazo anónimo. Solo entonces decide si quieres borrar los volúmenes antiguos.

Las pruebas automatizadas usan PostgreSQL Testcontainers y un servidor HTTP de contrato del SDK, sin MinIO ni claves reales. La prueba remota está deshabilitada por defecto. Las rutas de archivos privados de `local` conservan su adaptador persistente; cambiar a `dev` no traslada automáticamente esos objetos al bucket remoto.
