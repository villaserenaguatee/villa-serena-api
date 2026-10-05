package com.villaserena.api.auth.dto;

/** Par de tokens común a personal y huésped (esquema {@code Tokens}). */
public record Tokens(String accessToken, String refreshToken, String tipoToken, long expiraEn) {
}
