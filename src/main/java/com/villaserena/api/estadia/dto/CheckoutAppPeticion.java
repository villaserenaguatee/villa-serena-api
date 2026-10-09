package com.villaserena.api.estadia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutAppPeticion(@NotNull(message = "Los datos del comprador son obligatorios.") @Valid CompradorFactura comprador,
        @NotNull(message = "Indica si aceptas cancelar los pedidos pendientes.") Boolean aceptaCancelarPedidos) {
}
