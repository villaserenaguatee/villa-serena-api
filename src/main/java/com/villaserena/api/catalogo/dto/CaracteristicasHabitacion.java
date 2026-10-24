package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.*;

public record CaracteristicasHabitacion(
        @Min(1) int capacidad,
        @NotNull @DecimalMin("0.01") BigDecimal superficieM2,
        @NotEmpty @Size(max=20) Map<@NotBlank @Size(max=40) String, @NotNull @Min(1) @Max(30) Integer> camas,
        @NotNull @Size(max=100) List<@NotBlank @Size(max=200) String> caracteristicas) {}
