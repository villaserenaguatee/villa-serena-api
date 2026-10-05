package com.villaserena.api.huespedes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Los 6 datos obligatorios del huésped principal (esquema {@code HuespedDatos}). */
public record HuespedDatos(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 150) String nombreCompleto,
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 150) String correo,
        @NotBlank(message = "El teléfono es obligatorio.") @Size(max = 30) String telefono,
        @NotBlank(message = "La nacionalidad es obligatoria.") @Size(max = 60) String nacionalidad,
        @NotNull(message = "El tipo de documento es obligatorio.") TipoDocumento tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio.") @Size(max = 30) String numeroDocumento) {
}
