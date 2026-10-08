package com.villaserena.api.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reloj del Outbox: cada 30 segundos le pide a {@link OutboxDespachador} que
 * envíe lo pendiente. Si Mailpit está apagado, los correos se quedan esperando y
 * salen solos en cuanto vuelve, sin que nadie tenga que hacer nada.
 * <p>
 * El recorrido vive aquí y no en el despachador: {@code pendientes()} y
 * {@code procesar()} tienen que llamarse desde fuera de ese bean para que su
 * {@code @Transactional} se aplique (la consulta bloquea filas y exige transacción).
 */
@Component
public class OutboxJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxJob.class);

    private final OutboxDespachador despachador;

    public OutboxJob(OutboxDespachador despachador) {
        this.despachador = despachador;
    }

    /** Envía los pendientes que ya toca intentar, cada uno en su transacción, y devuelve cuántos salieron. */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT10S")
    public int enviarPendientes() {
        int enviados = 0;
        for (Long id : despachador.pendientes()) {
            try {
                if (despachador.procesar(id)) {
                    enviados++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo procesar el aviso {} del outbox: {}", id, e.getMessage());
            }
        }
        return enviados;
    }
}
