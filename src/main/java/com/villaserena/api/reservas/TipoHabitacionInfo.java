package com.villaserena.api.reservas;

import java.math.BigDecimal;

/** Datos del tipo de habitación que necesitan la tarifa y la disponibilidad (solo lectura). */
public record TipoHabitacionInfo(long id, String nombre, String descripcion, int capacidad, BigDecimal precioBase,
        BigDecimal ajusteFinSemanaPct, boolean activo) {
}
