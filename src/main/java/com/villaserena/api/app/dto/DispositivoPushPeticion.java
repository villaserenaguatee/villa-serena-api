package com.villaserena.api.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Token de Expo del teléfono (esquema {@code DispositivoPushPeticion}). */
public record DispositivoPushPeticion(
        @NotBlank(message = "El token es obligatorio.") @Size(max = 255) @Pattern(
                regexp = "^(ExponentPushToken|ExpoPushToken)\\[[^\\]]+\\]$",
                message = "El token no tiene el formato de Expo.") String tokenExpo) {
}
