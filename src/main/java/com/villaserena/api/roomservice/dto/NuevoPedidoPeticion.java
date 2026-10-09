package com.villaserena.api.roomservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Pedido que manda el huésped desde la app (HU-HUE-10). */
public record NuevoPedidoPeticion(
        @NotEmpty(message = "El pedido debe tener al menos un ítem.") @Valid List<LineaPedidoPeticion> items,
        @Size(max = 500, message = "Las notas no pueden pasar de 500 caracteres.") String notas) {
}
