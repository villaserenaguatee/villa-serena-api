# Gestión de ofertas y características — API #83

La PR #97 implementó el CRUD básico de tipos y habitaciones; la gestión comercial
completa continúa en este bloque. Se reutilizan fotos de #82 y las tablas
`room_types`, `room_type_photos`, `rooms`, `room_features`, `catalog_offers` y
`catalog_offer_rooms`. No se añade una migración ni se ejecutan datos demo en
producción. El contrato se propone coordinadamente en docs, seguimiento #40.

## Recorrido de Administración

1. Crear o editar el tipo y las unidades físicas con las operaciones existentes.
2. Registrar capacidad, superficie, camas y características de cada unidad con
   `PUT /api/v1/admin/habitaciones/{id}/caracteristicas`. No cambia piso ni tipo.
   Una unidad con reservas activas no puede cambiar las características prometidas.
3. Cargar y asociar la galería al tipo con el flujo de #82. El catálogo usa las
   URLs devueltas por el API, sin reconstruir direcciones de S3 ni copiar objetos.
4. Crear la oferta mediante `POST /api/v1/admin/ofertas/{slug}`; el slug queda
   inmutable. Para editar, `PUT` con el mismo recurso reemplaza todos los campos.
   `GET /api/v1/admin/ofertas` y `GET .../{slug}` permiten consultar y recargar.
5. Cambiar solo publicación con `PATCH .../{slug}/publicacion`, cuerpo
   `{"publicada":false}` o `true`. Despublicar conserva contenido y vínculos,
   incluso en ofertas anteriores cuyo contenido todavía requiere revisión.

Todas las operaciones nuevas exigen ADMIN. El GET de características devuelve
`{}` cuando la unidad existe pero aún no tiene características registradas.

Ejemplo del cuerpo completo de creación o edición de oferta (usar IDs reales):

```json
{
  "tipoHabitacionId": 1,
  "nombre": "Suite Jardín",
  "descripcion": "Habitación con vista al jardín",
  "piso": 1,
  "capacidad": 2,
  "superficieM2": 30,
  "camas": {"queen": 1},
  "caracteristicas": ["Wi-Fi", "Vista al jardín"],
  "preferencias": ["Descanso"],
  "precioPorNoche": 650.00,
  "publicada": true,
  "orden": 1,
  "habitacionIds": [1, 2]
}
```

La superficie, camas y características anunciadas deben estar acreditadas en las
unidades vinculadas. Todas comparten el piso anunciado; la capacidad física puede
ser mayor a la ofertada, pero esta no supera el tipo. Publicar necesita tipo
activo, galería y al menos una habitación activa acreditada. Los vínculos de un
PUT se validan antes de reemplazarse; un error no deja una edición parcial.

## Cantidad, precio y reservas

- `cantidadHabitaciones` cuenta vínculos físicos de la oferta y
  `cantidadHabitacionesTipo` cuenta filas reales de `rooms` del tipo, incluyendo
  inactivas. No son cupos manuales ni disponibilidad por fechas.
- Varias unidades vinculadas producen una sola tarjeta, identificada por slug.
- El precio de oferta (`precioPorNoche`) se guarda en `catalog_offers.base_price`;
  `price` público y cotización consultan esa misma fuente. La tarifa por tipo de
  `room_types.base_price` es independiente, como en el recorrido actual.
- Las ofertas anteriores conservan publicación por defecto; las despublicadas
  se excluyen de catálogo, disponibilidad y nuevas reservas. `bookable` continúa
  indicando elegibilidad general y no disponibilidad para fechas concretas.
- Se reutiliza la disponibilidad real y el inventario compartido existente.
  La creación relee oferta y precio después de bloquear el tipo. Editar contenido,
  vínculos o precio no reescribe total, noches ni snapshot de reservas anteriores.
- Principal y galería se leen del tipo al consultar; no quedan desactualizadas
  por guardar una copia en la oferta. Las referencias ilustrativas históricas
  se conservan cuando aún no hay galería asociada al tipo.
- Escrituras registran ADMIN y valores previos/nuevos en `administration_audit`;
  las ofertas usan `record_id` del tipo padre y conservan slug en el contenido.

## Verificación y límites

```sh
./mvnw -B -Dtest=OfertasAdminIntegrationTest,TiposHabitacionAdminIntegrationTest,HabitacionesAdminIntegrationTest test
./mvnw -B verify
```

Las pruebas nuevas utilizan PostgreSQL temporal y almacenamiento de prueba:
permisos, edición/recarga, fotos compartidas, publicación, una tarjeta con varias
unidades, cantidades derivadas, validación de vínculos, auditoría, cotización,
creación real de reserva, conservación histórica y exclusión por falta de cupo.

Esto no acredita el formulario web de Administración, la carga de una foto nueva
en S3/CloudFront real ni un despliegue. La conexión del consumidor se coordina en
web #69 después de revisar el contrato de docs. No se cambia el diseño público,
la integración de pagos ni las migraciones aplicadas.
