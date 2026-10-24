package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Petición para editar un ítem del menú (esquema {@code EditarItemMenuPeticion}).
 */
public record EditarItemMenuPeticion(
        @Size(min = 1, max = 100) String nombre,
        @Size(min = 1, max = 2000) String descripcion,
        Long categoriaId,
        @DecimalMin(value = "0.01") BigDecimal precio,
        @Size(max = 255) String fotoClave
) {}
