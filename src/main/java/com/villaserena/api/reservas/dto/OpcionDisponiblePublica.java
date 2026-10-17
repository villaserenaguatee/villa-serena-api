package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

/** Tipo disponible para la web pública, sin cantidad restante (esquema {@code OpcionDisponiblePublica}). */
public record OpcionDisponiblePublica(TipoHabitacionPublico tipoHabitacion, int noches, BigDecimal total,
        List<PrecioNoche> desglose) {

    public static OpcionDisponiblePublica de(TipoHabitacionPublico tipo, Cotizacion c) {
        return new OpcionDisponiblePublica(tipo, c.noches(), c.total(), c.desglose());
    }
}
