# Adopción del contrato canónico

El único contrato es `villa-serena-docs/openapi.yaml`. Se modifica mediante
issues/PR de docs, coordinando API, web y app. Spring implementa sus operaciones,
DTO, validación, errores y permisos; su Swagger es evidencia de implementación,
no otra fuente de verdad. No se genera Java desde el YAML ni se descarga el
contrato durante peticiones de negocio.

## Revisión adoptada

`api-contract.json` fija el repositorio `villaserenaguatee/villa-serena-docs` y el
commit completo `f30ff74fe939d467728c51c31d7aca157b3be34e`, integrado mediante docs
PR #20. El SHA-256 de su YAML es
`b1e4e8ecc10d6db74c170e7bdd12a02bd31b2cc98f8e3b7060d1fee04dd38e37`.
Local y CI leen el mismo manifiesto; no usan `main` cambiante ni otra copia YAML.

Para adoptar otra revisión, revisar primero su PR en docs, actualizar
deliberadamente `api-contract.json`, ejecutar las comprobaciones y coordinar los
consumidores afectados. No se debe adoptar una revisión solo para hacer pasar CI.

## Comprobar localmente

Además de Java 21, Maven y Docker para las pruebas, se necesitan Git, Node.js 24
y pnpm 12. Usar un checkout limpio de docs en la revisión fijada, ubicado donde
prefiera cada integrante; no cambiar el trabajo activo de otra persona.
`RUTA_DOCS` representa esa ubicación, no una carpeta que deba crearse con ese nombre.

```text
pnpm --dir "RUTA_DOCS" install --frozen-lockfile --ignore-scripts
node scripts/api-contract.mjs verify --docs "RUTA_DOCS"
node --test scripts/api-contract.test.mjs
```

Después, desde la raíz del API:

```text
./mvnw -B verify
node scripts/api-contract.mjs check --docs "RUTA_DOCS" --report-only
```

En PowerShell, el comando Maven equivalente es `.\mvnw.cmd -B verify`;
en CMD es `mvnw.cmd -B verify`.
Los comandos Node/pnpm funcionan en Windows, macOS y Linux. También se puede
configurar `VILLA_SERENA_DOCS_DIR` en el entorno y omitir `--docs`; no se exige
una ruta relativa ni un checkout hermano.

La comprobación rechaza otra revisión, cambios locales —incluidos archivos nuevos
no ignorados— o una subcarpeta en lugar de la raíz de docs. Las dependencias en
`node_modules`, ignoradas por Git, pueden estar instaladas. No instala dependencias
automáticamente ni cambia el checkout de docs.

`ExportacionOpenApiTest` consulta `/v3/api-docs` con MockMvc y PostgreSQL temporal,
sin levantar un servicio público ni usar producción. Escribe solo
`target/openapi-spring.json`. No necesita claves de pagos reales. La comparación
ejecuta la herramienta del checkout fijado:

```text
node "RUTA_DOCS/scripts/contrato-api.mjs" check-api --schema "RUTA_EXPORTACION" --revision SHA_COMPLETO
```

El wrapper guarda `target/contract-comparison.json`, con revisión, huella del
contrato y todas las operaciones faltantes/adicionales. `--schema` permite indicar
otra exportación temporal. No se versionan esos artefactos.

Sin `--report-only`, las diferencias devuelven código 1. En modo diagnóstico se
registran sin bloquear por divergencias conocidas; fallos de Git, revisión,
dependencias, exportación o herramientas **siguen siendo errores**. No se aplican
exclusiones de rutas.

## Estado de la reconciliación

Comparación sobre API base `4142208`, sin cambios funcionales en #58:

| Evidencia | Resultado |
|---|---|
| Canónico fijado | 130 operaciones HTTP |
| Exportación de Spring | 140 operaciones HTTP |
| Coincidencias exactas de ruta/método | 72 |
| Operaciones del contrato ausentes con esa ruta/método | 58 |
| Operaciones de Spring no declaradas con esa ruta/método | 68 |

Estos números no equivalen a funcionalidades por construir: hay capacidades
expuestas mediante rutas genéricas, nombres distintos y ampliaciones ya existentes.
No confirman equivalencia de comportamiento. Cada ejecución adjunta las listas
completas como artefactos de CI; no se silencian operaciones opcionales o pendientes.

| Área | Reconciliación pendiente |
|---|---|
| Público y acceso | Ofertas, verificación de correo y sus encabezados, contacto, recuperación del personal y perfil del huésped. |
| Pagos | Webhook `/pagos/recurrente/webhook` frente a `/pagos/stripe/webhook` del contrato. |
| Administración/gestión/laboral | Rutas genéricas de catálogos y gestión frente a rutas por recurso; distinguir capacidades opcionales o pendientes como amenidades/wifi. |
| Estadía y operación | Documentos, chat, objetos olvidados, solicitudes y sus rutas actuales. |
| Inventario | [docs #21](https://github.com/villaserenaguatee/villa-serena-docs/issues/21). |
| Hora estimada de llegada | [docs #22](https://github.com/villaserenaguatee/villa-serena-docs/issues/22); es una diferencia de DTO, no detectable comparando rutas. |
| Otras diferencias y DTO | [docs #23](https://github.com/villaserenaguatee/villa-serena-docs/issues/23), incluido país emisor, encabezados y validaciones. |

La comparación no comprueba DTO, nulabilidad, validación, respuestas, roles, área,
propiedad ni WebSocket. Las pruebas del API siguen siendo obligatorias. Entre las
aplicables están `HuespedDatosTest`, `BusquedaRecepcionIntegrationTest` (campos
requeridos, respuestas, nacionalidad omitida/nula y permisos),
`ReservaWebIntegrationTest`, `NacionalidadMigracionTest` y
`HoraEstimadaLlegadaIntegrationTest`. Pasarlas no certifica los demás flujos ni
resuelve las discrepancias de producto/documentación.

## CI y límites

El workflow conserva `./mvnw -B verify`, comprueba la procedencia de docs y las
pruebas del script; todas son verificaciones obligatorias. Instala las herramientas
con el lockfile del checkout fijado. Luego ejecuta la comparación **diagnóstica**,
la muestra en el resumen y adjunta ambos JSON temporales.

Un CI verde hoy no significa conformidad integral ni coincidencia de operaciones.
Tras reconciliar docs #21/#22/#23 y adoptar sus revisiones, retirar `--report-only`
para exigir coincidencia de rutas/métodos, manteniendo las pruebas de DTO y seguridad.
No ampliar el MVP ni eliminar rutas que use la web para forzar ese resultado.

Los documentos anteriores de endpoints/cobertura y el explorador HTML permanecen
como referencias históricas señaladas; no se actualizan como otro contrato.
