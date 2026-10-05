package com.villaserena.api.reservas.stripe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;

/** Implementación con el SDK oficial de Stripe para Java. Las claves vienen del .env. */
@Component
public class PasarelaStripeSdk implements PasarelaStripe {

    private static final Logger log = LoggerFactory.getLogger(PasarelaStripeSdk.class);

    private final PropiedadesVillaSerena.Stripe config;

    public PasarelaStripeSdk(PropiedadesVillaSerena propiedades) {
        this.config = propiedades.stripe();
    }

    @Override
    public Sesion crearSesion(String descripcion, BigDecimal monto, String urlRetorno, Instant expiraEn,
            Map<String, String> metadata) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(urlRetorno)
                .setCancelUrl(urlRetorno)
                .setExpiresAt(expiraEn.getEpochSecond())
                .putAllMetadata(metadata)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().putAllMetadata(metadata).build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("gtq")
                                .setUnitAmount(centavos(monto))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(descripcion)
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            return mapear(Session.create(params, opciones()));
        } catch (StripeException e) {
            throw error("crear la sesión de pago", e);
        }
    }

    @Override
    public Sesion obtenerSesion(String sesionId) {
        try {
            return mapear(Session.retrieve(sesionId, opciones()));
        } catch (StripeException e) {
            throw error("consultar la sesión de pago", e);
        }
    }

    @Override
    public Sesion expirarSesion(String sesionId) {
        try {
            Session sesion = Session.retrieve(sesionId, opciones());
            if ("open".equals(sesion.getStatus())) {
                sesion = sesion.expire(opciones());
            }
            return mapear(sesion);
        } catch (StripeException e) {
            throw error("vencer la sesión de pago", e);
        }
    }

    @Override
    public void reembolsar(String paymentIntentId) {
        try {
            com.stripe.model.Refund.create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build(),
                    opciones());
        } catch (StripeException e) {
            throw error("reembolsar el pago", e);
        }
    }

    @Override
    public Evento verificarEvento(String cuerpo, String firma) {
        Event evento;
        try {
            evento = Webhook.constructEvent(cuerpo, firma, requerido(config.webhookSecreto(), "STRIPE_WEBHOOK_SECRET"));
        } catch (SignatureVerificationException | RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIRMA_INVALIDA",
                    "La firma del aviso de Stripe no es válida.", List.of());
        }
        Sesion sesion = null;
        if (evento.getType() != null && evento.getType().startsWith("checkout.session.")) {
            try {
                var deserializador = evento.getDataObjectDeserializer();
                StripeObject objeto = deserializador.getObject().isPresent()
                        ? deserializador.getObject().get()
                        : deserializador.deserializeUnsafe();
                if (objeto instanceof Session s) {
                    sesion = mapear(s);
                }
            } catch (EventDataObjectDeserializationException e) {
                log.warn("No se pudo leer la sesión del evento {} de Stripe", evento.getId());
            }
        }
        return new Evento(evento.getId(), evento.getType(), sesion);
    }

    private RequestOptions opciones() {
        return RequestOptions.builder().setApiKey(requerido(config.claveSecreta(), "STRIPE_SECRET_KEY")).build();
    }

    private static String requerido(String valor, String variable) {
        if (valor == null || valor.isBlank()) {
            throw new ErrorStripe("Falta " + variable + " en el .env del API.");
        }
        return valor;
    }

    private static Sesion mapear(Session s) {
        return new Sesion(s.getId(), s.getUrl(), s.getStatus(), "paid".equals(s.getPaymentStatus()),
                s.getPaymentIntent(), s.getExpiresAt() == null ? null : Instant.ofEpochSecond(s.getExpiresAt()));
    }

    /** GTQ usa 2 decimales: Stripe recibe el monto en centavos. */
    static long centavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact();
    }

    private static ErrorStripe error(String accion, StripeException e) {
        log.warn("Stripe rechazó {}: {}", accion, e.getMessage());
        return new ErrorStripe("No se pudo " + accion + " con Stripe. Intenta de nuevo.");
    }
}
