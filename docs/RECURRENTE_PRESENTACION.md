# Presentación del checkout hospedado de Recurrente

## Alcance

Se cambia únicamente el nombre del ítem de la reserva web a `Reserva Villa Serena · <código real>` y se añade el parámetro oficial `items[].image_url` con el logo original de la Web: `WEB_URL/villa-serena-logo.png`. El cliente reutiliza ese logo también para los checkouts de saldo, sin cambiar su descripción. La URL base se toma de la configuración existente; no se crean secretos ni variables nuevas. El logo debe ser accesible públicamente por HTTPS desde Recurrente en Sandbox y producción.

El contrato canónico de Villa Serena y el consumidor Web no cambian. Importes en centavos, GTQ, cantidad, cargos únicos, métodos de pago, cuotas, metadata, expiración, success_url/cancel_url y checkout_url permanecen iguales. No se modifican firmas Svix, webhooks, idempotencia, OTP, reservas ni estados de pago. No se crean productos permanentes manuales ni se usa product_id: se conserva el modo inline actual.

## De dónde salen los textos

- Producto: PagoOnlineService enviaba `Reserva <código> — Hotel Villa Serena` como `items[0].name`. Ahora envía la variante solicitada en una sola línea. La referencia sigue siendo `reserva.getCodigo()` y su metadata se conserva.
- Comercio: no hay `Auto Hotel Villa Serena` en el código revisado ni se envía un nombre de comercio en el payload. Es consistente con el perfil de cuenta de Recurrente; no se ha inspeccionado ni modificado la cuenta para confirmar sus valores. No se puede garantizar desde este repositorio cómo Recurrente compone el sufijo `— Villa Serena`.
- TEST: depende de las credenciales/ambiente del proveedor. No se cambian llaves ni se activa modo real.

## Configuración manual del comercio

La guía oficial documenta Configuración → Editar perfil → Nombre. En la cuenta correcta y en Sandbox, revisar el valor y, si el propietario lo autoriza, poner `Hotel Villa Serena` y guardar. No cambiar razón social, NIT, documentos, bancos ni configuración de facturación. Este campo también afecta el nombre enviado al banco para cobros nuevos: no es una etiqueta aislada del checkout. Si aparece un segundo nombre de tienda o sufijo, confirmar su procedencia en la cuenta o con soporte; no inventar un campo para eliminarlo.

Fuente: https://ayuda.recurrente.com/es/p/con-que-nombre-le-aparece-mi-cobro-a-mi-cliente

## Logo y marca

El archivo original ya está en `villa-serena-web/public/villa-serena-logo.png`. Para configurar el logo de tienda manualmente, la ruta oficial es Tienda → Ajustes → Apariencia → Logo → seleccionar ese archivo → Guardar. En la interfaz nueva puede figurar Configuración → Formas de vender → Tienda → Apariencia. El artículo documenta la tienda, no garantiza que reemplace el icono R del encabezado de todos los checkouts API. No se ha modificado la cuenta.

La imagen del producto inline sí está documentada como `image_url`; esta PR la envía sin alterar el cobro. Una imagen de producto y el logo de comercio son elementos diferentes. La aparición exacta en el checkout debe comprobarse con un checkout nuevo de Sandbox después del despliegue autorizado; las sesiones existentes no se reescriben.

Fuentes:
- https://docs.recurrente.com/referencia-api/api-reference/checkouts/create-checkout (la versión Markdown expone las propiedades completas del ítem, incluido image_url).
- https://docs.recurrente.com/referencia-api/api-reference/checkouts/create-checkout.md
- https://ayuda.recurrente.com/es/p/como-personalizar-la-apariencia-de-tu-tienda-en-linea
- https://ayuda.recurrente.com/es/p/como-funcionan-las-tiendas-en-linea-en-recurrente

## Título y referencia separados

La creación inline documenta name y metadata, pero no un subtítulo visible de referencia de reserva. Metadata no garantiza una representación visible. Por eso se usa la alternativa solicitada `Reserva Villa Serena · VS-XXXXX`, con código dinámico. No se añade una propiedad description, reference o comercio sin documentación del proveedor.

## Idioma: pendiente de confirmación oficial

La referencia actual de creación y la guía/SDK oficial de checkout no documentan language, locale ni lang=es para la redirección hospedada. El código de Villa Serena no agrega lang=en: conserva checkout_url del proveedor. Ver lang=en en una sesión no demuestra que modificarlo sea una opción estable y soportada. No se añade ni se reescribe ese parámetro; tampoco se cambia la redirección a un iframe ni se instala otro SDK.

Antes de forzar español, confirmar con Recurrente qué opción oficial soporta el checkout hospedado y si es por cuenta, navegador o URL. La documentación de Citas sobre idioma del navegador corresponde a otra página y no se extrapola. No se declara que Recurrente carezca de español; se declara que no hay evidencia documental suficiente para forzarlo en esta integración.

Fuentes adicionales revisadas:
- https://docs.recurrente.com/guias-espanol/guias/embedded-checkouts
- https://github.com/recurrente/recurrente-checkout/blob/master/recurrente-checkout.js

## Validación

Los tests del adaptador verifican dos códigos/importes distintos, nombre, imagen, GTQ, metadata, vencimiento y conservación de checkout_url. ReservaWebIntegrationTest comprueba el nombre contra el código generado, importe contra el total real de la reserva y metadata/ruta de retorno sin cambios. CI ejecuta integración con PostgreSQL aislado; no se usan llaves reales ni se hacen solicitudes al proveedor.

Las pruebas Web comprueban su BFF y redirección existentes sin cambios de código ni una nueva PR Web. No se han creado pagos ni reservas de prueba en producción. La comprobación visual del comercio, logo y lenguaje dentro del checkout real de Sandbox requiere una sesión de prueba nueva y acceso autorizado a la cuenta; no se presenta como terminada.
