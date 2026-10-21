package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;

/** Detalle completo de la reserva para Recepción (esquema {@code ReservaDetalle}). */
public record ReservaDetalle(String codigo, EstadoReserva estado, CanalReserva canal, String identificadorExterno,
        LocalDate entrada, LocalDate salida, int noches, int numeroHuespedes, TipoHabitacionReferencia tipoHabitacion,
        HabitacionReferencia habitacion, HuespedVista huesped, List<HuespedAdicionalVista> huespedesAdicionales,
        BigDecimal total, BigDecimal saldoPendiente, OffsetDateTime creadaEn, List<HistorialEstadoVista> historial,
        @Schema(description = "Hora estimada local de llegada (America/Guatemala), formato HH:mm; "
                + "null si no se indicó, también en reservas históricas. No modifica el horario de check-in.",
                example = "18:30", nullable = true)
        String horaEstimadaLlegada) {
}
