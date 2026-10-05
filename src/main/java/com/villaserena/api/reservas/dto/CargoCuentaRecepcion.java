package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoCargo;
import com.villaserena.api.reservas.TipoCargo;

public record CargoCuentaRecepcion(Long id, OffsetDateTime fechaHora, TipoCargo tipo, String concepto, int cantidad,
        BigDecimal precioUnitario, BigDecimal monto, EstadoCargo estado, String motivoAnulacion, String responsable,
        String anuladoPor) {
}
