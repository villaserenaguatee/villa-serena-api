package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.villaserena.api.comun.Responsable;
import com.villaserena.api.huespedes.HuespedDatos;

/**
 * Datos para crear una reserva desde cualquier canal.
 *
 * @param huespedId            huésped ya registrado (Recepción); si es nulo se usa {@code huesped}
 * @param habitacionId         habitación opcional elegida en Recepción (reglas de HU-REC-07)
 * @param identificadorExterno solo canales externos (BOOKING, EXPEDIA)
 * @param montoCanal           solo canales externos: el alojamiento es el monto enviado por el canal (RN-TAR-010)
 * @param responsable          EMPLEADO (Recepción), CLIENTE (web) o CANAL
 */
public record SolicitudReserva(long tipoHabitacionId, LocalDate entrada, LocalDate salida, int numeroHuespedes,
        Long huespedId, HuespedDatos huesped, Long habitacionId, CanalReserva canal, String identificadorExterno,
        BigDecimal montoCanal, Responsable responsable) {

    public static SolicitudReserva web(long tipoHabitacionId, LocalDate entrada, LocalDate salida,
            int numeroHuespedes, HuespedDatos huesped) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, null, huesped, null,
                CanalReserva.DIRECTO_WEB, null, null, Responsable.CLIENTE);
    }

    public static SolicitudReserva recepcion(long huespedId, long tipoHabitacionId, LocalDate entrada,
            LocalDate salida, int numeroHuespedes, Long habitacionId, long empleadoId) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, huespedId, null,
                habitacionId, CanalReserva.RECEPCION, null, null, Responsable.empleado(empleadoId));
    }

    public static SolicitudReserva canal(CanalReserva canal, String identificadorExterno, BigDecimal monto,
            long tipoHabitacionId, LocalDate entrada, LocalDate salida, int numeroHuespedes, HuespedDatos huesped) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, null, huesped, null, canal,
                identificadorExterno, monto, Responsable.CANAL);
    }
}
