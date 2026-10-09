package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.villaserena.api.comun.Responsable;
import com.villaserena.api.huespedes.HuespedDatos;

/**
 * Datos para crear una reserva desde cualquier canal.
 *
 * @param identificadorExterno solo canales externos (BOOKING, EXPEDIA)
 * @param montoCanal           solo canales externos: el alojamiento es el monto enviado por el canal (RN-TAR-010)
 * @param responsable          EMPLEADO (Recepción), CLIENTE (web) o CANAL
 */
public record SolicitudReserva(long tipoHabitacionId, LocalDate entrada, LocalDate salida, int numeroHuespedes,
        HuespedDatos huesped, CanalReserva canal, String identificadorExterno, BigDecimal montoCanal,
        Responsable responsable) {

    public static SolicitudReserva web(long tipoHabitacionId, LocalDate entrada, LocalDate salida,
            int numeroHuespedes, HuespedDatos huesped) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, huesped,
                CanalReserva.DIRECTO_WEB, null, null, Responsable.CLIENTE);
    }

    public static SolicitudReserva recepcion(long tipoHabitacionId, LocalDate entrada, LocalDate salida,
            int numeroHuespedes, HuespedDatos huesped, long empleadoId) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, huesped,
                CanalReserva.RECEPCION, null, null, Responsable.empleado(empleadoId));
    }

    public static SolicitudReserva canal(CanalReserva canal, String identificadorExterno, BigDecimal monto,
            long tipoHabitacionId, LocalDate entrada, LocalDate salida, int numeroHuespedes, HuespedDatos huesped) {
        return new SolicitudReserva(tipoHabitacionId, entrada, salida, numeroHuespedes, huesped, canal,
                identificadorExterno, monto, Responsable.CANAL);
    }
}
