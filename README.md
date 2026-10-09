# villa-serena-api

API de Villa Serena (Spring Boot 4.1, Java 21, PostgreSQL 17 + Flyway). Ver `AGENTS.md` y la documentación en `villa-serena-docs`.

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
