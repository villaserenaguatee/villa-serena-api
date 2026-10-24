package com.villaserena.api.turnos;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Petición para asignar un turno a un empleado en múltiples fechas (esquema {@code AsignarTurnosPeticion}). */
public record AsignarTurnosPeticion(
        @NotNull(message = "El empleado es obligatorio.")
        Long empleadoId,
        @NotNull(message = "El turno es obligatorio.")
        Long turnoId,
        @NotEmpty(message = "Debe especificar al menos una fecha.")
        List<LocalDate> fechas
) {}
