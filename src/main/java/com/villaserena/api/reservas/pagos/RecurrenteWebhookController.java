package com.villaserena.api.reservas.pagos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Webhook público protegido por firma Svix y ventana temporal. */
@RestController
public class RecurrenteWebhookController {

    private final PasarelaPagos pasarela;
    private final PagoOnlineService pagos;

    public RecurrenteWebhookController(PasarelaPagos pasarela, PagoOnlineService pagos) {
        this.pasarela = pasarela;
        this.pagos = pagos;
    }

    @PostMapping("/api/v1/pagos/recurrente/webhook")
    public ResponseEntity<Void> recibir(@RequestBody String cuerpo,
            @RequestHeader org.springframework.http.HttpHeaders cabeceras) {
        pagos.procesarEvento(pasarela.verificarEvento(cuerpo, java.util.Map.of(
                "svix-id", java.util.Objects.toString(cabeceras.getFirst("svix-id"), ""),
                "svix-timestamp", java.util.Objects.toString(cabeceras.getFirst("svix-timestamp"), ""),
                "svix-signature", java.util.Objects.toString(cabeceras.getFirst("svix-signature"), ""))));
        return ResponseEntity.ok().build();
    }
}
