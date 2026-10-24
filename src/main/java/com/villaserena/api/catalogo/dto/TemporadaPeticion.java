package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemporadaPeticion(
        @NotBlank(message = "El nombre de la temporada es obligatorio.")
        String nombre,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        LocalDate inicio,

        @NotNull(message = "La fecha de fin es obligatoria.")
        LocalDate fin,

        @NotNull(message = "El porcentaje de ajuste es obligatorio.")
        @DecimalMin(value = "-99.99", message = "El ajuste debe ser mayor a -100%.")
        BigDecimal ajustePct,

        List<Long> tipoHabitacionIds
) {}
