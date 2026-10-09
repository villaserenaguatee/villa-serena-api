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
    private final List<ProveedorAdjuntos> proveedoresAdjuntos;

    public EnviadorCorreo(JavaMailSender correo, TemplateEngine plantillas, PropiedadesVillaSerena propiedades,
            List<ProveedorAdjuntos> proveedoresAdjuntos) {
        this.correo = correo;
        this.plantillas = plantillas;
        this.remitente = propiedades.notificaciones().remitente();
        this.proveedoresAdjuntos = proveedoresAdjuntos;
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

        List<ProveedorAdjuntos.Adjunto> adjuntos = proveedoresAdjuntos.stream()
                .filter(p -> p.soporta(aviso.getTipo()))
                .flatMap(p -> p.adjuntos(datos).stream())
                .toList();

        MimeMessage mensaje = correo.createMimeMessage();
        try {
            MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, !adjuntos.isEmpty(),
                    StandardCharsets.UTF_8.name());
            ayuda.setFrom(remitente);
            ayuda.setTo(aviso.getDestinatario());
            ayuda.setSubject(plantilla.asunto(datos));
            ayuda.setText(plantillas.process(plantilla.archivo, contexto), true);
            for (ProveedorAdjuntos.Adjunto adjunto : adjuntos) {
                ayuda.addAttachment(adjunto.nombre(), new ByteArrayResource(adjunto.contenido()),
                        adjunto.tipoContenido());
            }
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
        },

        FACTURA("correo/factura") {
            @Override
            String asunto(Map<String, Object> datos) {
                return "Tu factura " + datos.get("serieNumero") + " — Hotel Villa Serena";
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
                // OBJ-3A-2 (OTP) agrega aquí su plantilla.
                default -> throw new IllegalStateException("Sin plantilla de correo para " + tipo);
            };
        }
    }
}
