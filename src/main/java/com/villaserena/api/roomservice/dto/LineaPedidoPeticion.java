package com.villaserena.api.roomservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaPedidoPeticion(
        @NotNull(message = "El ítem es obligatorio.") Long itemId,
        @NotNull(message = "La cantidad es obligatoria.") @Min(value = 1, message = "La cantidad debe ser al menos 1.") Integer cantidad) {
}
