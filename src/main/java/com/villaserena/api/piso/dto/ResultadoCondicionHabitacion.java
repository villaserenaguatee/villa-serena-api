package com.villaserena.api.piso.dto;

import com.villaserena.api.estadia.CondicionHabitacion;
import com.villaserena.api.estadia.OcupacionHabitacion;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/**
 * Habitación después de iniciar, interrumpir o terminar la limpieza (esquema
 * {@code ResultadoCondicionHabitacion}).
 */
public record ResultadoCondicionHabitacion(HabitacionReferencia habitacion, OcupacionHabitacion ocupacion,
        CondicionHabitacion condicion, ResponsableEmpleado empleadoACargo) {
}
