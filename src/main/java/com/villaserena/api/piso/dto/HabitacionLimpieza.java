package com.villaserena.api.piso.dto;

import java.time.OffsetDateTime;

import com.villaserena.api.estadia.CondicionHabitacion;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/** Habitación pendiente de limpieza (esquema {@code HabitacionLimpieza}). */
public record HabitacionLimpieza(HabitacionReferencia habitacion, CondicionHabitacion condicion, boolean llegadaHoy,
        OffsetDateTime suciaDesde, ResponsableEmpleado empleadoACargo) {
}
