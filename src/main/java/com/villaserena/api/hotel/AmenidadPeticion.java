package com.villaserena.api.hotel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Petición de creación de amenidad (esquema {@code AmenidadPeticion}). */
public record AmenidadPeticion(
        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,
        String descripcion,
        String fotoClave,
        String horario,
        String ubicacion,
        @Min(value = 0, message = "El orden no puede ser negativo.")
        Integer orden
) {
    public AmenidadPeticion {
        descripcion = descripcion == null ? "" : descripcion;
        horario = horario == null ? "" : horario;
        ubicacion = ubicacion == null ? "" : ubicacion;
        orden = orden == null ? 0 : orden;
    }
}
