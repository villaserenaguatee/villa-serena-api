package com.villaserena.api.estadia.dto;

import com.villaserena.api.reservas.MetodoPago;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pago del saldo en Recepción: EFECTIVO, TARJETA u OTRO; el monto es siempre el saldo completo. */
public record PagoRecepcionPeticion(@NotNull(message = "El método de pago es obligatorio.") MetodoPago metodo,
        @Size(max = 100) String referencia) {
}
