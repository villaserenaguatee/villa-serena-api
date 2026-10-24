package com.villaserena.api.catalogo.dto;

import jakarta.validation.constraints.Min;

public record EditarHabitacionPeticion(
        String numero,
        @Min(1) Integer piso,
        Long tipoHabitacionId
) {}
