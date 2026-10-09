package com.villaserena.api.notificaciones;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 */
@Component
public class EnviadorCorreo implements EnviadorNotificacion {

    private final JavaMailSender correo;
    private final TemplateEngine plantillas;
    private final String remitente;

    public EnviadorCorreo(JavaMailSender correo, TemplateEngine plantillas, PropiedadesVillaSerena propiedades) {
        this.correo = correo;
        this.plantillas = plantillas;
        this.remitente = propiedades.notificaciones().remitente();
    }

    @Override
    public boolean soporta(TipoNotificacion tipo) {
        return tipo.esCorreo();
    }

    @Override
    public void enviar(Outbox aviso, Map<String, Object> datos) {
        Plantilla plantilla = Plantilla.de(aviso.getTipo());
        Context contexto = new Context();
        datos.forEach(contexto::setVariable);

        MimeMessage mensaje = correo.createMimeMessage();
        try {
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            ayuda.setFrom(remitente);
            ayuda.setTo(aviso.getDestinatario());
            ayuda.setSubject(plantilla.asunto(datos));
            ayuda.setText(plantillas.process(plantilla.archivo, contexto), true);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("No se pudo armar el correo: " + e.getMessage(), e);
        }
        correo.send(mensaje);
    }

    /** Qué plantilla y qué asunto lleva cada tipo de correo. */
    private enum Plantilla {

        CONFIRMACION("correo/confirmacion-reserva") {
            @Override
            String asunto(Map<String, Object> datos) {
                return "Reserva confirmada " + datos.get("codigo") + " — Hotel Villa Serena";
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
                // OBJ-3A-2 (OTP) y OBJ-4B (factura) agregan aquí su plantilla.
                default -> throw new IllegalStateException("Sin plantilla de correo para " + tipo);
            };
        }
    }
}
