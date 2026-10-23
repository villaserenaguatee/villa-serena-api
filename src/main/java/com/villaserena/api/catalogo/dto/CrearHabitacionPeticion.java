package com.villaserena.api.catalogo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearHabitacionPeticion(
        @NotBlank String numero,
        @Min(1) int piso,
        @NotNull Long tipoHabitacionId
) {}
