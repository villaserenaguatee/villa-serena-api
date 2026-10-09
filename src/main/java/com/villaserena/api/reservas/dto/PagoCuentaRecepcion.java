package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.MetodoPago;

public record PagoCuentaRecepcion(Long id, OffsetDateTime fechaHora, MetodoPago metodo, BigDecimal monto,
        EstadoPago estado, String referencia, String responsable) {
}
