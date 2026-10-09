package com.villaserena.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Valores de {@code villaserena.*} en application.yaml (los secretos vienen del .env). */
@ConfigurationProperties(prefix = "villaserena")
public record PropiedadesVillaSerena(
        String zonaHoraria,
        Cors cors,
        Jwt jwt,
        Login login,
        PrimerAdmin primerAdmin) {

    public record Cors(List<String> origenes) {
    }

    public record Jwt(String secreto, int accesoMinutos, int refreshDias) {
    }

    public record Login(int intentosMaximos, int bloqueoMinutos) {
    }

    public record PrimerAdmin(String correo, String nombre, String contrasena) {
    }
}
