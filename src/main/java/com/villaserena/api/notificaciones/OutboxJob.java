package com.villaserena.api.notificaciones;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reloj del Outbox: cada 30 segundos le pide a {@link OutboxDespachador} que
 * envíe lo pendiente. Si Mailpit está apagado, los correos se quedan esperando y
 * salen solos en cuanto vuelve, sin que nadie tenga que hacer nada.
 */
@Component
public class OutboxJob {

    private final OutboxDespachador despachador;

    public OutboxJob(OutboxDespachador despachador) {
        this.despachador = despachador;
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    public void enviarPendientes() {
        despachador.enviarPendientes();
    }
}
