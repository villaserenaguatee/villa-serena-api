package com.villaserena.api.turnos;

import jakarta.validation.constraints.Pattern;

/** Petición de edición parcial de turno (esquema {@code EditarTurnoPeticion}). */
public record EditarTurnoPeticion(
        String nombre,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de inicio debe tener formato HH:mm.")
        String horaInicio,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de fin debe tener formato HH:mm.")
        String horaFin
) {}
