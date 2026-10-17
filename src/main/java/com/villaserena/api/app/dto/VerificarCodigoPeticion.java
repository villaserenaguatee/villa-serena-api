package com.villaserena.api.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificarCodigoPeticion(
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 150) String correo,
        @NotBlank(message = "El código es obligatorio.") @Pattern(regexp = "^[0-9]{6}$", message = "El código tiene 6 dígitos.") String codigo) {
}
