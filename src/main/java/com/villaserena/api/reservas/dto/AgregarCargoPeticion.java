package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgregarCargoPeticion(
        @NotBlank(message = "El concepto es obligatorio.") @Size(max = 150) String concepto,
        @NotNull(message = "La cantidad es obligatoria.") @Min(value = 1, message = "La cantidad debe ser mayor que 0.") Integer cantidad,
        @NotNull(message = "El precio unitario es obligatorio.") @DecimalMin(value = "0.01", message = "El precio debe ser mayor que 0.") BigDecimal precioUnitario) {
}
