package com.villaserena.api.reservas.stripe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Lo que el sistema necesita de Stripe (modo prueba). La implementación real usa el
 * SDK de Java; en las pruebas se reemplaza por un doble.
 */
public interface PasarelaStripe {

    /** Sesión de Stripe Checkout. {@code pagada} = payment_status "paid". */
    record Sesion(String id, String url, String estado, boolean pagada, String paymentIntentId, Instant expiraEn) {

        public boolean abierta() {
            return "open".equals(estado);
        }
    }

    /** Evento del webhook ya verificado: solo interesan su id, su tipo y la sesión. */
    record Evento(String id, String tipo, Sesion sesion) {
    }

    Sesion crearSesion(String descripcion, BigDecimal monto, String urlRetorno, Instant expiraEn,
            Map<String, String> metadata);

    Sesion obtenerSesion(String sesionId);

    /** Vence una sesión abierta para que ya no se pueda pagar. Devuelve la sesión actualizada. */
    Sesion expirarSesion(String sesionId);

    /** Reembolso total del pago (RN-CAN-008). Lanza ErrorStripe si Stripe lo rechaza. */
    void reembolsar(String paymentIntentId);

    /** Verifica la firma del webhook (STRIPE_WEBHOOK_SECRET). Lanza ApiException FIRMA_INVALIDA si no es válida. */
    Evento verificarEvento(String cuerpo, String firma);
}
