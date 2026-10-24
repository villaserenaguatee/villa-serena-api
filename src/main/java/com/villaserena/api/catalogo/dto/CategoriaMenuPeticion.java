package com.villaserena.api.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición de creación o edición de categoría del menú (esquema {@code CategoriaMenuPeticion}).
 */
public record CategoriaMenuPeticion(
        @NotBlank @Size(min = 1, max = 60) String nombre
) {}
