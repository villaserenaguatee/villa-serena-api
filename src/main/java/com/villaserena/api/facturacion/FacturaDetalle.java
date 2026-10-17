package com.villaserena.api.facturacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.villaserena.api.reservas.MetodoPago;

/**
 * Factura de demostración (esquema {@code FacturaDetalle}): copia inmutable con los
 * cargos VIGENTE y los pagos aprobados de la cuenta.
 */
public record FacturaDetalle(Long id, String estado, String serie, int numero, OffsetDateTime emitidaEn,
        String codigoReserva, Hotel hotel, Comprador comprador, List<Cargo> cargos, List<Pago> pagos,
        BigDecimal total, String leyendaIva, String leyendaLegal) {

    public static final String LEYENDA_IVA = "IVA incluido";
    public static final String LEYENDA_LEGAL = "Factura de demostración — no válida ante la SAT";

    public record Hotel(String nombre, String nombreComercial, String razonSocial, String nit, String direccionFiscal,
            String telefono, String correo) {
    }

    public record Comprador(String nit, String nombreComprador) {
    }

    public record Cargo(String concepto, int cantidad, BigDecimal precioUnitario, BigDecimal monto) {
    }

    public record Pago(MetodoPago metodo, BigDecimal monto) {
    }
}
