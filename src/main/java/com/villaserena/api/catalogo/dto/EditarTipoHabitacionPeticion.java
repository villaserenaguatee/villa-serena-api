package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

public record EditarTipoHabitacionPeticion(
        String nombre,
        String descripcion,
        @Min(1) Integer capacidad,
        @DecimalMin("0.01") BigDecimal precioBase
) {}
