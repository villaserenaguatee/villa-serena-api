package com.villaserena.api.tiemporeal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.villaserena.api.tiemporeal.dto.WsTicket;

/**
 * Tickets para abrir el WebSocket desde la web (HU-EMP-01, x-websocket del contrato).
 * <p>
 * El navegador nunca recibe el JWT del empleado: el BFF lo guarda en una cookie
 * httpOnly y, cuando la pantalla necesita el tiempo real, pide un ticket opaco de
 * un solo uso que vence en 60 segundos. Aunque alguien lo copiara de la URL del
 * WebSocket, al minuto no sirve, y si ya se usó tampoco.
 * <p>
 * Los tickets viven en memoria. Para el hito local (una sola instancia del API) es
 * suficiente; con varias instancias habría que moverlos a la base o a Redis.
 */
@Service
public class WsTicketService {

    static final Duration DURACION = Duration.ofSeconds(60);

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Map<String, Emitido> tickets = new ConcurrentHashMap<>();
    private final Clock reloj;

    public WsTicketService(Clock reloj) {
        this.reloj = reloj;
    }

    /** Un ticket y el usuario al que pertenece, hasta que venza o se use. */
    record Emitido(long empleadoId, String rol, String area, Instant vence) {
    }

    public WsTicket emitir(long empleadoId, String rol, String area) {
        limpiarVencidos();
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant vence = reloj.instant().plus(DURACION);
        tickets.put(ticket, new Emitido(empleadoId, rol, area, vence));
        return new WsTicket(ticket, OffsetDateTime.ofInstant(vence, reloj.getZone()));
    }

    /** Consume el ticket: la segunda vez ya no existe, y uno vencido tampoco vale. */
    Optional<Emitido> consumir(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tickets.remove(ticket))
                .filter(emitido -> emitido.vence().isAfter(reloj.instant()));
    }

    private void limpiarVencidos() {
        Instant ahora = reloj.instant();
        tickets.values().removeIf(emitido -> emitido.vence().isBefore(ahora));
    }
}
