package com.villaserena.api.notificaciones;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.villaserena.api.config.PropiedadesVillaSerena;

import jakarta.mail.internet.MimeMessage;

/**
 * Envía los avisos de tipo {@code CORREO_*} por SMTP (en local, el Mailpit del
 * Docker: http://localhost:8025). El cuerpo es una plantilla Thymeleaf en
 * {@code resources/templates/correo/}; los datos salen del payload del Outbox.
 * Los adjuntos (el PDF de la factura) los aporta un {@link ProveedorAdjuntos}.
 */
@Component
public class EnviadorCorreo implements EnviadorNotificacion {

    private final JavaMailSender correo;
    private final TemplateEngine plantillas;
    private final String remitente;
    private final String accesoUrl;
    private final List<ProveedorAdjuntos> proveedoresAdjuntos;

    public EnviadorCorreo(JavaMailSender correo, TemplateEngine plantillas, PropiedadesVillaSerena propiedades,
            List<ProveedorAdjuntos> proveedoresAdjuntos) {
        this.correo = correo;
        this.plantillas = plantillas;
        this.remitente = propiedades.notificaciones().remitente();
        this.accesoUrl = propiedades.webUrl().replaceAll("/+$", "") + "/login?acceso=huesped";
        this.proveedoresAdjuntos = proveedoresAdjuntos;
    }

    @Override
    public boolean soporta(TipoNotificacion tipo) {
        return tipo.esCorreo();
    }

    @Override
    public void enviar(Outbox aviso, Map<String, Object> datos) {
        Plantilla plantilla = "RECUPERAR_PERSONAL".equals(datos.get("proposito")) ? Plantilla.RECUPERACION_PERSONAL : aviso.getTipo() == TipoNotificacion.CORREO_OTP && "VERIFICAR_RESERVA".equals(datos.get("proposito")) ? Plantilla.VERIFICACION_RESERVA : Plantilla.de(aviso.getTipo());
        Context contexto = new Context();
        datos.forEach(contexto::setVariable);

        List<ProveedorAdjuntos.Adjunto> adjuntos = proveedoresAdjuntos.stream()
                .filter(p -> p.soporta(aviso.getTipo()))
                .flatMap(p -> p.adjuntos(datos).stream())
                .toList();

        contexto.setVariable("accesoUrl", accesoUrl);
        adjuntos.stream().filter(a -> "application/pdf".equals(a.tipoContenido())).findFirst()
                .ifPresent(a -> contexto.setVariable("nombrePdf", a.nombre()));

        MimeMessage mensaje = correo.createMimeMessage();
        try {
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, true,
                    StandardCharsets.UTF_8.name());
            ayuda.setFrom(remitente);
            ayuda.setTo(aviso.getDestinatario());
            ayuda.setSubject(plantilla.asunto(datos));
            ayuda.setText(plantillas.process(plantilla.archivo, contexto), true);
            ayuda.addInline("villa-serena-logo",
                    new org.springframework.core.io.ClassPathResource("correo/villa-serena-logo.png"), "image/png");
            for (ProveedorAdjuntos.Adjunto adjunto : adjuntos) {
                ayuda.addAttachment(adjunto.nombre(), new ByteArrayResource(adjunto.contenido()),
                        adjunto.tipoContenido());
            }
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("No se pudo armar el correo: " + e.getMessage(), e);
        }
        correo.send(mensaje);
        // Huella del adjunto realmente aceptado por SMTP, sin destinatario ni contenido privado.
        for (ProveedorAdjuntos.Adjunto adjunto : adjuntos) {
            if ("application/pdf".equals(adjunto.tipoContenido())) {
                try {
                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(adjunto.contenido()));
                    org.slf4j.LoggerFactory.getLogger(EnviadorCorreo.class).info("Factura SMTP aceptada: outbox={}, archivo={}, sha256={}", aviso.getId(), adjunto.nombre(), hash);
                } catch (java.security.NoSuchAlgorithmException imposible) {
                    throw new IllegalStateException("SHA-256 no disponible", imposible);
                }
            }
        }
    }

    /** Qué plantilla y qué asunto lleva cada tipo de correo. */
    private enum Plantilla {

        CONFIRMACION("correo/confirmacion-reserva") {
            @Override
            String asunto(Map<String, Object> datos) {
                return "Reserva confirmada " + datos.get("codigo") + " — Hotel Villa Serena";
            }
        },

        FACTURA("correo/factura") {
            @Override
            String asunto(Map<String, Object> datos) {
                return "Tu factura " + datos.get("serieNumero") + " — Hotel Villa Serena";
            }
        },

        VERIFICACION_RESERVA("correo/verificar-reserva") {
            @Override String asunto(Map<String, Object> datos) { return "Verifica tu correo para reservar — Villa Serena Hotel"; }
        },

        RECUPERACION_PERSONAL("correo/recuperacion-personal") {
            @Override String asunto(Map<String, Object> datos) { return "Restablezca su contraseña — Villa Serena"; }
        },

        CODIGO_ACCESO("correo/codigo-acceso") {
            @Override
            String asunto(Map<String, Object> datos) {
                // El código no va en el asunto: se ve en la notificación del
                // teléfono sin abrir el correo.
                return "Su código de acceso — Hotel Villa Serena";
            }
        };

        private final String archivo;

        Plantilla(String archivo) {
            this.archivo = archivo;
        }

        abstract String asunto(Map<String, Object> datos);

        static Plantilla de(TipoNotificacion tipo) {
            return switch (tipo) {
                case CORREO_CONFIRMACION -> CONFIRMACION;
                case CORREO_FACTURA -> FACTURA;
                case CORREO_OTP -> CODIGO_ACCESO;
                default -> throw new IllegalStateException("Sin plantilla de correo para " + tipo);
            };
        }
    }
}
