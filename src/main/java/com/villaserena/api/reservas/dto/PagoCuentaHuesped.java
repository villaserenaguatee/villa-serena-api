package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.MetodoPago;

/** Pago visible para el huésped: sin referencias de Stripe ni responsables. */
public record PagoCuentaHuesped(Long id, OffsetDateTime fechaHora, MetodoPago metodo, BigDecimal monto,
        EstadoPago estado) {

    public static PagoCuentaHuesped de(PagoCuentaRecepcion p) {
        return new PagoCuentaHuesped(p.id(), p.fechaHora(), p.metodo(), p.monto(), p.estado());
    }
}
