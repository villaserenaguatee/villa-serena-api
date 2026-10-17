package com.villaserena.api.app.dto;

import java.time.LocalDate;

import com.villaserena.api.reservas.EstadoReserva;

/** Fila de "mis reservas" (esquema {@code ReservaAppResumen}). */
public record ReservaAppResumen(String codigo, LocalDate entrada, LocalDate salida, EstadoReserva estado) {
}
