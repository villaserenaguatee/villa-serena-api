package com.villaserena.api.reservas.stripe;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de Stripe (ruta pública protegida por la firma). En local lo reenvía
 * Stripe CLI: {@code stripe listen --forward-to localhost:8080/api/v1/pagos/stripe/webhook}.
 */
@RestController
public class StripeWebhookController {

    private final PasarelaStripe stripe;
    private final PagoStripeService pagos;

    public StripeWebhookController(PasarelaStripe stripe, PagoStripeService pagos) {
        this.stripe = stripe;
        this.pagos = pagos;
    }

    @PostMapping("/api/v1/pagos/stripe/webhook")
    public ResponseEntity<Void> recibir(@RequestBody String cuerpo,
            @RequestHeader(name = "Stripe-Signature", required = false) String firma) {
        pagos.procesarEvento(stripe.verificarEvento(cuerpo, firma == null ? "" : firma));
        return ResponseEntity.ok().build();
    }
}
