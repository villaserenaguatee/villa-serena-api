package com.villaserena.api.reservas.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservaRecepcionPeticion(
        @NotNull(message = "El huésped es obligatorio.") Long huespedId,
        @NotNull(message = "El tipo de habitación es obligatorio.") Long tipoHabitacionId,
        @NotNull(message = "La fecha de entrada es obligatoria.") LocalDate entrada,
        @NotNull(message = "La fecha de salida es obligatoria.") LocalDate salida,
        @NotNull(message = "El número de huéspedes es obligatorio.") @Min(value = 1, message = "Debe haber al menos un huésped.") Integer numeroHuespedes,
        Long habitacionId) {
}
