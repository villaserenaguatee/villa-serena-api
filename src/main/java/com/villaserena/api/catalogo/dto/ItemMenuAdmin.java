package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

/**
 * Representación administrativa de un ítem del menú (esquema {@code ItemMenuAdmin}).
 */
public record ItemMenuAdmin(
        Long id,
        String nombre,
        String descripcion,
        Long categoriaId,
        BigDecimal precio,
        String fotoClave,
        String disponibilidad,
        String estado,
        String fotoUrl
) {}
