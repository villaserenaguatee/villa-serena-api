package com.villaserena.api.reservas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarReservaPeticion(
        @NotBlank(message = "El motivo es obligatorio.") @Size(max = 500, message = "Máximo 500 caracteres.") String motivo) {
}
