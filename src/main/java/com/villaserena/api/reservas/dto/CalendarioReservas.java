package com.villaserena.api.reservas.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos del Gantt (esquema {@code CalendarioReservas}): habitaciones agrupadas por tipo y
 * las reservas del rango; las que tienen {@code habitacionId} nulo van en "Sin asignar".
 */
public record CalendarioReservas(LocalDate desde, LocalDate hasta, List<Grupo> grupos,
        List<ReservaCalendario> reservas) {

    public record Grupo(TipoHabitacionReferencia tipoHabitacion, List<HabitacionReferencia> habitaciones) {
    }
}
