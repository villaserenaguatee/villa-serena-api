package com.villaserena.api.tiemporeal.dto;

/** Estado operativo de una habitación (esquema {@code HabitacionEvento}). */
public record HabitacionEvento(long habitacionId, String numero, int piso, String ocupacion, String condicion,
        boolean llegaHoy, boolean saleHoy, boolean incidenciaPendiente) {
}
