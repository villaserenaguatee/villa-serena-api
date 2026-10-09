package com.villaserena.api.estadia;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;

/**
 * Check-in (HU-REC-12, RN-RES-014): reserva CONFIRMADA, hoy entre la fecha de entrada
 * y el día anterior a la salida (America/Guatemala), con habitación asignada LIBRE +
 * LIMPIA. Reserva EN_ESTADIA y habitación OCUPADA en una sola transacción.
 */
@Service
public class CheckinService {

    private final ReservaService reservaService;
    private final HistorialEstadoService historial;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<HabitacionEventos> eventos;
    private final Clock reloj;

    public CheckinService(ReservaService reservaService, HistorialEstadoService historial, JdbcTemplate jdbc,
            ObjectProvider<HabitacionEventos> eventos, Clock reloj) {
        this.reservaService = reservaService;
        this.historial = historial;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public Reserva hacerCheckin(String codigo, long empleadoId) {
        Reserva reserva = reservaService.porCodigo(codigo);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "Solo se puede hacer check-in de reservas confirmadas.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (hoy.isBefore(reserva.getFechaEntrada()) || !hoy.isBefore(reserva.getFechaSalida())) {
            throw ApiException.conflicto("CHECKIN_FUERA_DE_FECHA",
                    "El check-in solo se puede hacer desde la fecha de entrada hasta el día anterior a la salida.");
        }
        if (reserva.getHabitacionId() == null) {
            throw ApiException.conflicto("SIN_HABITACION_ASIGNADA", "Asigna una habitación antes de hacer el check-in.");
        }
        long habitacionId = reserva.getHabitacionId();
        Habitacion habitacion = jdbc.query(
                "SELECT numero, ocupacion, condicion FROM habitaciones WHERE id = ? FOR UPDATE",
                (rs, i) -> new Habitacion(rs.getString(1), rs.getString(2), rs.getString(3)), habitacionId)
                .getFirst();
        if (!"LIBRE".equals(habitacion.ocupacion()) || !"LIMPIA".equals(habitacion.condicion())) {
            throw ApiException.conflicto("HABITACION_NO_LISTA",
                    "La habitación " + habitacion.numero() + " no está libre y limpia.");
        }

        Responsable recepcionista = Responsable.empleado(empleadoId);
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE id = ?", habitacionId);
        historial.registrar(TipoEntidadHistorial.HABITACION_OCUPACION, habitacionId, "LIBRE", "OCUPADA",
                recepcionista, null);
        reservaService.cambiarEstado(reserva, EstadoReserva.EN_ESTADIA, recepcionista, null);
        publicarDespuesDeConfirmar(habitacionId);
        return reserva;
    }

    /** Evento 4 (cambio de habitación) solo si la transacción se confirma. */
    private void publicarDespuesDeConfirmar(long habitacionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventos.ifAvailable(e -> e.habitacionCambio(habitacionId));
            }
        });
    }

    private record Habitacion(String numero, String ocupacion, String condicion) {
    }
}
