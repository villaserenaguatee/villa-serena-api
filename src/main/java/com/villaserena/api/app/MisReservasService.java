package com.villaserena.api.app;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.app.dto.ReservaAppDetalle;
import com.villaserena.api.app.dto.ReservaAppResumen;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.dto.HabitacionReferencia;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * "Mis reservas" (HU-HUE-09). Todas las consultas filtran por el huésped del token,
 * nunca por un id que mande el cliente; una reserva ajena responde 404 y no 403,
 * para no confirmar que ese código existe.
 */
@Service
public class MisReservasService {

    /** Hora fija de salida del hotel (PAR-02). */
    private static final String HORA_CHECKOUT = "12:00";

    private final JdbcTemplate jdbc;

    public MisReservasService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ReservaAppResumen> mias(long huespedId) {
        return jdbc.query("""
                SELECT code AS "codigo", checkin_date AS "fecha_entrada", checkout_date AS "fecha_salida", status AS "estado" FROM reservations WHERE guest_id = ?
                ORDER BY checkin_date DESC, id DESC""",
                (rs, i) -> new ReservaAppResumen(rs.getString(1), rs.getObject(2, java.time.LocalDate.class),
                        rs.getObject(3, java.time.LocalDate.class), EstadoReserva.valueOf(rs.getString(4))),
                huespedId);
    }

    @Transactional(readOnly = true)
    public ReservaAppDetalle mia(String codigo, long huespedId) {
        return jdbc.query("""
                SELECT r.code AS "codigo", r.checkin_date AS "fecha_entrada", r.checkout_date AS "fecha_salida", r.status AS "estado", r.guest_count AS "numero_huespedes",
                       t.id, t.name AS "nombre", h.id, h.number AS "numero", h.floor AS "piso" FROM reservations r
                JOIN room_types t ON t.id = r.room_type_id
                LEFT JOIN rooms h ON h.id = r.room_id
                WHERE r.code = ? AND r.guest_id = ?""",
                (rs, i) -> detalle(rs), codigo, huespedId).stream()
                .findFirst()
                .orElseThrow(ApiException::noEncontrado);
    }

    private static ReservaAppDetalle detalle(ResultSet rs) throws SQLException {
        Long habitacionId = (Long) rs.getObject(8);
        return new ReservaAppDetalle(rs.getString(1), rs.getObject(2, java.time.LocalDate.class),
                rs.getObject(3, java.time.LocalDate.class), EstadoReserva.valueOf(rs.getString(4)),
                HORA_CHECKOUT, new TipoHabitacionReferencia(rs.getLong(6), rs.getString(7)), rs.getInt(5),
                habitacionId == null ? null
                        : new HabitacionReferencia(habitacionId, rs.getString(9), rs.getInt(10)));
    }
}
