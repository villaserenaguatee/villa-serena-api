package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoCargo;
import com.villaserena.api.reservas.TipoCargo;

/** Cargo visible para el huésped: sin responsables (VIS-01). */
public record CargoCuentaHuesped(Long id, OffsetDateTime fechaHora, TipoCargo tipo, String concepto, int cantidad,
        BigDecimal precioUnitario, BigDecimal monto, EstadoCargo estado, String motivoAnulacion) {

    public static CargoCuentaHuesped de(CargoCuentaRecepcion c) {
        return new CargoCuentaHuesped(c.id(), c.fechaHora(), c.tipo(), c.concepto(), c.cantidad(), c.precioUnitario(),
                c.monto(), c.estado(), c.motivoAnulacion());
    }
}
