package com.villaserena.api.auth.dto;

/** Tokens más los datos del empleado (esquema {@code SesionEmpleado}). */
public record SesionEmpleado(String accessToken, String refreshToken, String tipoToken, long expiraEn,
        EmpleadoSesion empleado) {

    public static SesionEmpleado de(Tokens t, EmpleadoSesion empleado) {
        return new SesionEmpleado(t.accessToken(), t.refreshToken(), t.tipoToken(), t.expiraEn(), empleado);
    }
}
