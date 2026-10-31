package com.villaserena.api.roomservice.dto;

/** Categoría del menú (esquema {@code CategoriaMenu}). */
public record CategoriaMenu(long id, String nombre, String franja, java.time.LocalTime horaInicio, java.time.LocalTime horaFin) {
    public CategoriaMenu(long id, String nombre) { this(id, nombre, "OTROS", null, null); }
 {
}
