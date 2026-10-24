package com.villaserena.api.turnos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Petición de creación de turno (esquema {@code TurnoPeticion}). */
public record TurnoPeticion(
        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,
        @NotBlank(message = "La hora de inicio es obligatoria.")
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de inicio debe tener formato HH:mm.")
        String horaInicio,
        @NotBlank(message = "La hora de fin es obligatoria.")
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de fin debe tener formato HH:mm.")
        String horaFin
) {}
