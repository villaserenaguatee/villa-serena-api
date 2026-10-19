package com.villaserena.api.reservas.dto;

import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.EstadoReserva;
import java.time.OffsetDateTime;
import com.villaserena.api.reservas.dto.ProcesoPago.AccionPago;
import com.villaserena.api.reservas.dto.ProcesoPago.EstadoCheckout;

/** Solo el estado, sin datos personales (VIS-03). */
public record EstadoReservaPublico(String codigo, EstadoReserva estadoReserva, EstadoPago estadoPago,
        boolean puedeReintentar, EstadoCheckout estadoCheckout, AccionPago accionPago, String urlPago,
        OffsetDateTime expiraEn, OffsetDateTime pagoVenceEn) {
}
