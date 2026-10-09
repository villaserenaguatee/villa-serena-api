package com.villaserena.api.reservas.dto;

import java.time.LocalDate;

import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;

/** Barra del Gantt (esquema {@code ReservaCalendario}). */
public record ReservaCalendario(String codigo, String huespedPrincipal, LocalDate entrada, LocalDate salida,
        EstadoReserva estado, CanalReserva canal, Long tipoHabitacionId, Long habitacionId) {
}
