package com.villaserena.api.turnos;

import java.time.LocalDate;

import com.villaserena.api.piso.dto.ResponsableEmpleado;

/** Asignación de turno a un empleado en una fecha (esquema {@code AsignacionTurno}). */
public record AsignacionTurno(
        Long id,
        ResponsableEmpleado empleado,
        TurnoAdmin turno,
        LocalDate fecha
) {}
