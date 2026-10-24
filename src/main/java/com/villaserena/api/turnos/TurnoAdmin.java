package com.villaserena.api.turnos;

/** Turno de trabajo en vista administrativa (esquema {@code TurnoAdmin}). */
public record TurnoAdmin(
        Long id,
        String nombre,
        String horaInicio,
        String horaFin,
        String estado
) {}
