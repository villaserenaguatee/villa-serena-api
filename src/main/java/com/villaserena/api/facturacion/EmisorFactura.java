package com.villaserena.api.facturacion;

/**
 * Emisión de la factura de demostración (HU-REC-15). La implementa Hugo (OBJ-4B):
 * correlativo con SELECT ... FOR UPDATE, PDF con OpenPDF y correo por el Outbox.
 * <p>
 * El check-out la llama <b>dentro</b> de su transacción, después de registrar el
 * pago: si lanza una excepción, el check-out completo se deshace (RN-RES-022).
 */
public interface EmisorFactura {

    /**
     * @return el id de la factura y su detalle con el formato del esquema
     *         {@code FacturaDetalle} del contrato (se devuelve tal cual en el check-out)
     */
    FacturaEmitida emitir(long cuentaId, String nit, String nombreComprador);

    record FacturaEmitida(long id, Object detalle) {
    }
}
