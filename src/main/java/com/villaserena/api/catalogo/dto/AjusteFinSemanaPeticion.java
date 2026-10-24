package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AjusteFinSemanaPeticion(
        @NotNull @DecimalMin("-99.99") BigDecimal ajusteFinSemanaPct
) {}
