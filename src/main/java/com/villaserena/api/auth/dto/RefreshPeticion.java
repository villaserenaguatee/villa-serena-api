package com.villaserena.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshPeticion(@NotBlank(message = "El refresh token es obligatorio.") String refreshToken) {
}
