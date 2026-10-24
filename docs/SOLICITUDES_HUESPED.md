# Solicitudes del huésped — API #22

Las rutas oficiales reutilizan la creación de limpieza y artículos y conservan
las rutas anteriores. No necesitan una migración ni cambian solicitudes históricas.

| Método | Ruta bajo `/api/v1/app/reservas/{codigo}` | Resultado |
|---|---|---|
| GET | `/solicitudes` | 200, lista de `SolicitudHuesped` |
| POST | `/solicitudes/limpieza` | 201, `SolicitudHuesped` |
| POST | `/solicitudes/articulos` | 201, `SolicitudHuesped` |
| POST | `/solicitudes/{id}/cancelar` | 200, `SolicitudHuesped` |

La vista incluye `id`, `tipo`, `creadaEn`, `estado`, `comentario` y `articulos`.
Cada artículo incluye `articuloId`, `nombre` y `cantidad`. No incorpora identidad
del empleado, datos del huésped ni información de cuenta. Limpieza devuelve
artículos vacíos y `comentario` puede ser `null`.

Solo HUESPED puede usar estas cuatro operaciones. La reserva y la solicitud deben
pertenecerle; un recurso inexistente o ajeno responde 404. La consulta sigue
permitida después del check-out. La creación exige EN_ESTADIA y habitación
asignada; no cambia la condición de la habitación ni descuenta inventario.

El cuerpo oficial de artículos contiene `articulos`, sin `clave` obligatoria.
Se rechazan listas vacías, elementos nulos, artículos inexistentes o repetidos,
cantidades no positivas y superiores al máximo del catálogo. Las rutas antiguas
de artículos mantienen su `clave`, idempotencia y respuesta reducida HTTP 200.

La reserva y la habitación se bloquean al solicitar limpieza, preservando la
restricción de una LIMPIEZA PENDIENTE o EN_PROCESO por habitación, incluso ante
concurrencia. El conflicto devuelve `SOLICITUD_ACTIVA` (409). Cancelar requiere
PENDIENTE; otro estado devuelve `SOLICITUD_CAMBIADA` (409), sin borrar registros.
La cancelación y la toma desde Limpieza se coordinan mediante el bloqueo de la
solicitud. El historial se guarda en la misma transacción y el evento de
`/topic/solicitudes` se publica después del commit.

## Verificación

Con Java 21, Maven wrapper y Docker en ejecución:

```sh
./mvnw -B -Dtest=SolicitudesHuespedIntegrationTest test
./mvnw -B verify
```

`SolicitudesHuespedIntegrationTest` usa PostgreSQL 17 temporal con Flyway y datos
ficticios aislados. Comprueba los DTO completos, permisos, titularidad, estados,
validaciones, listado tras el check-out, rutas anteriores e idempotencia. También
verifica rollback sin eventos, creación de limpieza simultánea y cancelación
frente a toma por el empleado, así como consulta y atención desde Limpieza.

Se contrastaron las operaciones y esquemas de solicitudes de la revisión fijada
por API (`f30ff74fe939d467728c51c31d7aca157b3be34e`) y de la revisión citada en el
seguimiento de #22 (`77225a11d06daf576a404e2a557185868bd49c56`); el bloque no requiere
cambiar el contrato ni la revisión adoptada por este consumidor.

Estas pruebas acreditan el bloque HTTP/BD y la publicación del evento tras el
commit. No acreditan recepción en una pantalla web ni entrega push a un teléfono.
La validación con app, WebSocket del consumidor y development build permanece en
#22 y sus dependencias. La PR no cierra automáticamente la issue completa.
