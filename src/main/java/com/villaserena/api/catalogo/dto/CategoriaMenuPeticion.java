package com.villaserena.api.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalTime;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Petición de creación o edición de categoría del menú (esquema {@code CategoriaMenuPeticion}).
 */
public record CategoriaMenuPeticion(
        @NotBlank @Size(min = 1, max = 60) String nombre,
        @Pattern(regexp = "DESAYUNO|ALMUERZO|CENA|BEBIDAS|OTROS") String franja,
        LocalTime horaInicio,
        LocalTime horaFin
) {}
