package com.villaserena.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Las reglas de la nueva contraseña se validan en el servicio para responder CONTRASENA_INVALIDA. */
public record CambiarContrasenaPeticion(
        @NotBlank(message = "La contraseña actual es obligatoria.") String contrasenaActual,
        @NotBlank(message = "La nueva contraseña es obligatoria.") String contrasenaNueva,
        @NotBlank(message = "La confirmación es obligatoria.") String confirmacion) {
}
