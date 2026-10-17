package com.villaserena.api.reservas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Habitación a asignar a la reserva (esquema {@code AsignarHabitacionPeticion}). */
public record AsignarHabitacionPeticion(@NotNull @Positive Long habitacionId) {
}
