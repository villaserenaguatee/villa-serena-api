package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición para crear un ítem del menú (esquema {@code CrearItemMenuPeticion}).
 */
public record CrearItemMenuPeticion(
        @NotBlank @Size(min = 1, max = 100) String nombre,
        @NotBlank @Size(min = 1, max = 2000) String descripcion,
        @NotNull Long categoriaId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precio,
        @Size(max = 255) String fotoClave
) {}
