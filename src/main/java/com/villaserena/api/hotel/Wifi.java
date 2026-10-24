package com.villaserena.api.hotel;

import jakarta.validation.constraints.NotBlank;

/** Credenciales de red Wi-Fi para huéspedes (esquema {@code Wifi}). */
public record Wifi(
        @NotBlank(message = "El nombre de la red es obligatorio.")
        String nombreRed,
        @NotBlank(message = "La contraseña es obligatoria.")
        String contrasena
) {}
