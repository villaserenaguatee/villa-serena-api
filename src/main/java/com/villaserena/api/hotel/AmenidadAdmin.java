package com.villaserena.api.hotel;

/** Amenidad del hotel en vista administrativa (esquema {@code AmenidadAdmin}). */
public record AmenidadAdmin(
        Long id,
        String nombre,
        String descripcion,
        String fotoClave,
        String horario,
        String ubicacion,
        Integer orden,
        String estado
) {}
