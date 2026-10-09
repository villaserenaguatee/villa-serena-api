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
        Archivos archivos,
        Notificaciones notificaciones) {

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

    /**
     * Archivos en MinIO (API S3). {@code urlPublica}: base del bucket público (fotos del
     * hotel, tipos y menú). El bucket privado guarda las fotos de incidencias, que se
     * entregan con una URL firmada de {@code minutosUrlFirmada}.
     */
    public record Archivos(String urlPublica, String endpoint, String usuario, String contrasena,
            String bucketPrivado, int minutosUrlFirmada) {
    }

    /**
     * Correos y Outbox (OBJ-1C). {@code remitente} es el From de los correos;
     * {@code appDescargaUrl} el enlace del APK que va en la confirmación.
     */
    public record Notificaciones(String remitente, String appDescargaUrl, String expoPushUrl, int intentosMaximos,
            int esperaMinutos, int lote) {
    }
}
