package com.villaserena.api.estadia.dto;

import com.villaserena.api.estadia.CondicionHabitacion;
import com.villaserena.api.estadia.OcupacionHabitacion;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * Estado de una habitación para Recepción (esquema {@code HabitacionEstado}).
 * {@code incidenciaBloqueante} solo se llena si la condición es FUERA_DE_SERVICIO.
 */
public record HabitacionEstado(Long id, String numero, int piso, TipoHabitacionReferencia tipoHabitacion,
        OcupacionHabitacion ocupacion, CondicionHabitacion condicion, boolean llegaHoy, boolean saleHoy,
        boolean incidenciaPendiente, IncidenciaBloqueante incidenciaBloqueante) {
}
