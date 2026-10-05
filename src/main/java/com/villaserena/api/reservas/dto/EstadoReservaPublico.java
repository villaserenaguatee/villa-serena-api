package com.villaserena.api.reservas.dto;

import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.EstadoReserva;

/** Solo el estado, sin datos personales (VIS-03). */
public record EstadoReservaPublico(String codigo, EstadoReserva estadoReserva, EstadoPago estadoPago,
        boolean puedeReintentar) {
}
