package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearTipoHabitacionPeticion(
        @NotBlank String nombre,
        @NotBlank String descripcion,
        @Min(1) int capacidad,
        @NotNull @DecimalMin("0.01") BigDecimal precioBase
) {}
