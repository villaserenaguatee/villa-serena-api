package com.villaserena.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginPeticion(
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") String correo,
        @NotBlank(message = "La contraseña es obligatoria.") String contrasena) {
}
