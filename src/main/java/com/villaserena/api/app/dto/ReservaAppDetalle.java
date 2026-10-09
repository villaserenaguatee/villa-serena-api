package com.villaserena.api.app.dto;

import java.time.LocalDate;

import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.dto.HabitacionReferencia;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * Detalle de una reserva en la app (esquema {@code ReservaAppDetalle}).
 * {@code habitacion} nula la app muestra como "Por asignar".
 */
public record ReservaAppDetalle(String codigo, LocalDate entrada, LocalDate salida, EstadoReserva estado,
        String horaCheckOut, TipoHabitacionReferencia tipoHabitacion, int numeroHuespedes,
        HabitacionReferencia habitacion) {
}
