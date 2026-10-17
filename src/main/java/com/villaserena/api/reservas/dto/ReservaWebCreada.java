package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.villaserena.api.reservas.EstadoReserva;

public record ReservaWebCreada(String codigo, EstadoReserva estado, TipoHabitacionReferencia tipoHabitacion,
        LocalDate entrada, LocalDate salida, int noches, int numeroHuespedes, BigDecimal total,
        OffsetDateTime pagoVenceEn) {
}
