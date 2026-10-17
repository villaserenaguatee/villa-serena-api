package com.villaserena.api.canal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/** Lo que el canal recibe de vuelta (esquema {@code ReservaCanalRespuesta}). */
public record ReservaCanalRespuesta(String codigo, EstadoReserva estado, CanalReserva canal,
        String identificadorExterno, TipoHabitacionReferencia tipoHabitacion, LocalDate entrada, LocalDate salida,
        int numeroHuespedes, BigDecimal montoTotal) {

    public static ReservaCanalRespuesta de(Reserva reserva, String nombreTipo) {
        return new ReservaCanalRespuesta(reserva.getCodigo(), reserva.getEstado(), reserva.getCanal(),
                reserva.getIdentificadorExterno(),
                new TipoHabitacionReferencia(reserva.getTipoHabitacionId(), nombreTipo), reserva.getFechaEntrada(),
                reserva.getFechaSalida(), reserva.getNumeroHuespedes(), reserva.getTotal());
    }
}
