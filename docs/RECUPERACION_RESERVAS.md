# Estado de pago y recuperación de reservas

Implementación de [API #68](https://github.com/villaserenaguatee/villa-serena-api/issues/68),
coordinada con [docs #25](https://github.com/villaserenaguatee/villa-serena-docs/issues/25)
y su [PR de contrato #26](https://github.com/villaserenaguatee/villa-serena-docs/pull/26).
El contrato canónico permanece en docs. Esta guía explica la integración; no mantiene
otra copia de sus esquemas. `api-contract.json` conserva la revisión adoptada hasta
que se revise e integre el cambio en docs y se coordine su adopción por consumidores.

## Estado y siguiente acción

La consulta pública existente es `GET /api/v1/publico/reservas/{codigo}/estado`.
La consulta del pago de saldo es `GET /api/v1/app/reservas/{codigo}/pago-saldo/estado`,
con JWT de huésped y control de titularidad; una reserva ajena responde 404.

La interfaz utiliza `accionPago`: iniciar el primer pago, continuar el mismo enlace,
esperar, reintentar o no ofrecer una acción de cobro. No debe deducir la acción
exclusivamente de `PENDIENTE_PAGO` o del saldo.

`estadoPago` sigue siendo el registro local. El checkout puede estar pagado en
Recurrente mientras ese registro siga pendiente: corresponde esperar confirmación,
no mostrar una reserva confirmada ni iniciar otro cobro. Las consultas no aprueban
pagos, no crean sesiones y no consumen comprobantes. Si la pasarela no responde,
no se ofrece un reintento. El POST de pago vuelve a validar antes de actuar.

**Compatibilidad:** se conservan los campos anteriores, pero `puedeReintentar`
pasa a significar exclusivamente que puede sustituirse un intento fallido o vencido.
Para el primer intento y para continuar el enlace se usa `accionPago`. Web y app
necesitan adoptar esta semántica junto con los tipos generados desde docs.

## Recuperar una respuesta de creación perdida

Antes del POST, la web conserva la clave del intento y el borrador. Conserva el
comprobante de correo siguiendo sus medidas actuales de protección, sin ponerlo
en URLs, logs ni issues. Las peticiones siguen pasando por el BFF.

`GET /api/v1/publico/reservas/intento` recibe los encabezados `Idempotency-Key`,
`X-Booking-Draft` y `X-Booking-Email-Verification`. Para una reserva de oferta se
indica `origen=OFERTA`; para una normal se usa `RESERVA`, valor predeterminado.
No requiere el cuerpo original. Devuelve el código y el estado actual, sin datos
personales de la reserva.

Una creación aún bloqueada en otra transacción devuelve 409
`SOLICITUD_EN_PROCESO`. Un 404 no acredita que sea seguro crear otra reserva:
puede tratarse de un resultado todavía no registrado, de una prueba incorrecta o
vencida, o de un intento histórico. Se conserva la clave y se consulta nuevamente
o se reenvía el POST original con la misma clave y los mismos datos. La consulta
no puede detectar una petición que aún no haya llegado al servidor.

Un comprobante vencido requiere verificar nuevamente el mismo correo y borrador.
La nueva prueba puede recuperar el intento original, sin consumirla otra vez ni
crear una reserva. Otra prueba del mismo correo con distinto borrador no autoriza
la recuperación. La consulta tiene límite de solicitudes por IP y no se almacena
en caché.

## Persistencia y compatibilidad histórica

V29 agrega un vínculo nullable con la verificación consumida. La creación de la
reserva, el consumo del comprobante, la respuesta idempotente y el vínculo se
confirman en la misma transacción. No modifica migraciones aplicadas ni asigna
propietarios por inferencia.

Los intentos anteriores sin vínculo mantienen su reenvío idempotente original,
pero no habilitan la nueva consulta de recuperación. No se cambia el esquema de
estados contables del pago: el proceso observado se representa en el DTO.

La app conserva su ventana de salida en America/Guatemala, saldo positivo y ausencia
de pedidos en camino. Un pago aprobado anterior no impide pagar cargos posteriores.
Un saldo cero no inicia otro checkout.

## Comprobación

Las pruebas de integración usan PostgreSQL 17 con Testcontainers y pasarela simulada;
no realizan cobros reales. Cubren recuperación normal y de ofertas, titularidad,
comprobantes vencidos/renovados, intentos históricos y creación en otra conexión.
Las pruebas de pagos cubren estados del proveedor, incertidumbre, vencimiento,
reutilización y sustitución de checkout, y acceso del huésped al saldo.

La prueba real del BFF, la app y Recurrente requiere la adopción del contrato y la
coordinación con sus consumidores; no queda acreditada por estas pruebas del API.
