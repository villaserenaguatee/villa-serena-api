package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

/** Tipo disponible para Recepción, con las habitaciones que quedan (esquema {@code OpcionDisponibleRecepcion}). */
public record OpcionDisponibleRecepcion(TipoHabitacionReferencia tipoHabitacion, int capacidad,
        int habitacionesDisponibles, int noches, BigDecimal total, List<PrecioNoche> desglose) {
}
