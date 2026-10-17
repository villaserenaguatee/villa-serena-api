package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

import com.villaserena.api.reservas.EstadoCuenta;
import com.villaserena.api.reservas.EstadoReserva;

/** Cuenta para Recepción (HU-REC-13): con responsables y referencias de pago. */
public record CuentaRecepcion(String codigoReserva, EstadoReserva estadoReserva, EstadoCuenta estadoCuenta,
        String nombreHuesped, List<PrecioNoche> detalleNoches, BigDecimal totalCargosVigentes,
        BigDecimal totalPagosAprobados, BigDecimal saldo, Long facturaId, List<CargoCuentaRecepcion> cargos,
        List<PagoCuentaRecepcion> pagos) {
}
