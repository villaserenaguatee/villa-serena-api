package com.villaserena.api.tiemporeal.dto;

import java.time.OffsetDateTime;

/** Ticket de un uso para abrir el WebSocket (esquema {@code WsTicket}). */
public record WsTicket(String ticket, OffsetDateTime expiraEn) {
}
