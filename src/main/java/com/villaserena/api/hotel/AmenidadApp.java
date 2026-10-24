package com.villaserena.api.hotel;

/** Amenidad activa visible para el huésped (esquema {@code AmenidadApp}). */
public record AmenidadApp(
        Long id,
        String nombre,
        String descripcion,
        String horario,
        String ubicacion,
        Integer orden,
        String fotoUrl
) {}
