package com.villaserena.api.hotel;

import jakarta.validation.constraints.Min;

/** Petición de edición parcial de amenidad (esquema {@code EditarAmenidadPeticion}). */
public record EditarAmenidadPeticion(
        String nombre,
        String descripcion,
        String fotoClave,
        String horario,
        String ubicacion,
        @Min(value = 0, message = "El orden no puede ser negativo.")
        Integer orden
) {}
