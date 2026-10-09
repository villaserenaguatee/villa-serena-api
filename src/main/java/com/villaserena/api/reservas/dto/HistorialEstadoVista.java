package com.villaserena.api.reservas.dto;

import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoReserva;

/** Un cambio de estado de la reserva (esquema {@code HistorialEstado}). */
public record HistorialEstadoVista(EstadoReserva estadoAnterior, EstadoReserva estadoNuevo, String responsable,
        OffsetDateTime fechaHora, String motivo) {
}
