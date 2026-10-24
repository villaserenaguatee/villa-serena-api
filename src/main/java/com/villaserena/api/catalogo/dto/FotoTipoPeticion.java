package com.villaserena.api.catalogo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FotoTipoPeticion(
        @NotBlank String clave,
        boolean principal,
        @Min(0) int orden
) {}
