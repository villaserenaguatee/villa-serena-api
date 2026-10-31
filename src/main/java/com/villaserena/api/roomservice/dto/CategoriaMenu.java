package com.villaserena.api.roomservice.dto;

import java.time.LocalTime;

/** Categoría del menú con franja y horario opcional. */
public record CategoriaMenu(long id, String nombre, String franja, LocalTime horaInicio, LocalTime horaFin) {
    public CategoriaMenu(long id, String nombre) {
        this(id, nombre, "OTROS", null, null);
    }
}
