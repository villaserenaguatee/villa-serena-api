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
        PrimerAdmin primerAdmin,
        String webUrl,
        Reservas reservas,
        Stripe stripe,
        Archivos archivos) {

    public record Cors(List<String> origenes) {
    }

    public record Jwt(String secreto, int accesoMinutos, int refreshDias) {
    }

    public record Login(int intentosMaximos, int bloqueoMinutos) {
    }

    public record PrimerAdmin(String correo, String nombre, String contrasena) {
    }

    /** Plazo para pagar una reserva web antes de cancelarla (PAR-06). */
    public record Reservas(int minutosPago) {
    }

    /** {@code urlRetornoApp}: a dónde vuelve Stripe después de pagar el saldo en la app. */
    public record Stripe(String claveSecreta, String webhookSecreto, String urlRetornoApp) {
    }

    /** URL base del bucket público (fotos del hotel, tipos y menú). */
    public record Archivos(String urlPublica) {
    }
}
