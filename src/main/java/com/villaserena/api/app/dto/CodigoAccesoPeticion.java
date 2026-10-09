package com.villaserena.api.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodigoAccesoPeticion(
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 150) String correo) {
}
