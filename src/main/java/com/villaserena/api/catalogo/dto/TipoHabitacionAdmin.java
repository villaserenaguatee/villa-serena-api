package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

public record TipoHabitacionAdmin(
        long id,
        String nombre,
        String descripcion,
        int capacidad,
        BigDecimal precioBase,
        BigDecimal ajusteFinSemanaPct,
        String estado,
        List<FotoCatalogo> fotos
) {}
