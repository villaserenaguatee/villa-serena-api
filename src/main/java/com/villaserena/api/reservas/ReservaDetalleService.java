package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.huespedes.HuespedRepository;
import com.villaserena.api.huespedes.TipoDocumento;
import com.villaserena.api.reservas.dto.HabitacionReferencia;
import com.villaserena.api.reservas.dto.HistorialEstadoVista;
import com.villaserena.api.reservas.dto.HuespedAdicionalVista;
import com.villaserena.api.reservas.dto.HuespedVista;
import com.villaserena.api.reservas.dto.ReservaDetalle;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * Arma el {@code ReservaDetalle} de Recepción. Lo usan crear, cancelar y el check-in,
 * y también sirve para GET /reservas/{codigo} (búsqueda de Josué).
 */
@Service
public class ReservaDetalleService {

    private final ReservaRepository reservas;
    private final HuespedRepository huespedes;
    private final CuentaRepository cuentas;
    private final CuentaService cuentaService;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public ReservaDetalleService(ReservaRepository reservas, HuespedRepository huespedes, CuentaRepository cuentas,
            CuentaService cuentaService, JdbcTemplate jdbc, Clock reloj) {
        this.reservas = reservas;
        this.huespedes = huespedes;
        this.cuentas = cuentas;
        this.cuentaService = cuentaService;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ReservaDetalle porCodigo(String codigo) {
        return de(reservas.findByCodigo(codigo).orElseThrow(ApiException::noEncontrado));
    }

    @Transactional(readOnly = true)
    public ReservaDetalle de(Reserva r) {
        String tipo = jdbc.queryForObject("SELECT nombre FROM tipos_habitacion WHERE id = ?", String.class,
                r.getTipoHabitacionId());
        HabitacionReferencia habitacion = r.getHabitacionId() == null ? null
                : jdbc.queryForObject("SELECT id, numero, piso FROM habitaciones WHERE id = ?",
                        (rs, i) -> new HabitacionReferencia(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                        r.getHabitacionId());
        HuespedVista huesped = huespedes.findById(r.getHuespedId()).map(HuespedVista::de).orElseThrow();
        List<HuespedAdicionalVista> adicionales = jdbc.query("""
                SELECT id, nombre_completo, tipo_documento, numero_documento, nacionalidad
                FROM huespedes_adicionales WHERE reserva_id = ? ORDER BY id""",
                (rs, i) -> new HuespedAdicionalVista(rs.getLong(1), rs.getString(2),
                        TipoDocumento.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5)),
                r.getId());
        BigDecimal saldo = cuentas.findByReservaId(r.getId()).map(c -> cuentaService.saldo(c.getId()))
                .orElse(BigDecimal.ZERO.setScale(2));
        return new ReservaDetalle(r.getCodigo(), r.getEstado(), r.getCanal(), r.getIdentificadorExterno(),
                r.getFechaEntrada(), r.getFechaSalida(), r.noches(), r.getNumeroHuespedes(),
                new TipoHabitacionReferencia(r.getTipoHabitacionId(), tipo), habitacion, huesped, adicionales,
                r.getTotal(), saldo, OffsetDateTime.ofInstant(r.getCreadaEn(), reloj.getZone()), historial(r));
    }

    /** Responsable: nombre del empleado, "Cliente web", el código del canal o "SISTEMA". */
    private List<HistorialEstadoVista> historial(Reserva r) {
        return jdbc.query("""
                SELECT h.estado_anterior, h.estado_nuevo, h.tipo_responsable, e.nombre_completo, h.fecha, h.motivo
                FROM historial_estados h
                LEFT JOIN empleados e ON e.id = h.id_responsable
                WHERE h.tipo_entidad = 'RESERVA' AND h.id_entidad = ?
                ORDER BY h.fecha, h.id""",
                (rs, i) -> new HistorialEstadoVista(
                        rs.getString(1) == null ? null : EstadoReserva.valueOf(rs.getString(1)),
                        EstadoReserva.valueOf(rs.getString(2)),
                        responsable(rs.getString(3), rs.getString(4), r.getCanal()),
                        OffsetDateTime.ofInstant(rs.getObject(5, Timestamp.class).toInstant(), reloj.getZone()),
                        rs.getString(6)),
                r.getId());
    }

    private static String responsable(String tipo, String empleado, CanalReserva canal) {
        return switch (tipo) {
            case "EMPLEADO" -> empleado;
            case "CLIENTE" -> "Cliente web";
            case "CANAL" -> canal.name();
            case "STRIPE" -> "Stripe";
            case "HUESPED" -> "Huésped";
            default -> "SISTEMA";
        };
    }
}
