package com.villaserena.api.estadia.dto;

import java.math.BigDecimal;

import com.villaserena.api.reservas.EstadoCuenta;
import com.villaserena.api.reservas.EstadoReserva;

/** Resultado del check-out: la factura va con el formato {@code FacturaDetalle} de Hugo. */
public record CheckoutResultado(String codigoReserva, EstadoReserva estadoReserva, EstadoCuenta estadoCuenta,
        BigDecimal saldo, Object factura) {
}
