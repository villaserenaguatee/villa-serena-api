package com.villaserena.api.estadia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRecepcionPeticion(@NotNull(message = "Los datos del comprador son obligatorios.") @Valid CompradorFactura comprador,
        @Valid PagoRecepcionPeticion pago) {
}
