package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;

/** Fila de la búsqueda de reservas de Recepción (esquema {@code ReservaResumen}). */
public record ReservaResumen(String codigo, HuespedReferencia huespedPrincipal, LocalDate entrada, LocalDate salida,
        int noches, int numeroHuespedes, TipoHabitacionReferencia tipoHabitacion, HabitacionReferencia habitacion,
        EstadoReserva estado, CanalReserva canal, String identificadorExterno, BigDecimal total,
        BigDecimal saldoPendiente) {
}
