package com.villaserena.api.piso;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
import com.villaserena.api.estadia.CondicionHabitacion;
import com.villaserena.api.estadia.OcupacionHabitacion;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.piso.dto.HabitacionLimpieza;
import com.villaserena.api.piso.dto.ResponsableEmpleado;
import com.villaserena.api.piso.dto.ResultadoCondicionHabitacion;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/**
 * Limpieza de habitaciones (OBJ-3B-1, parte A): pendientes (HU-MYL-01), iniciar o
 * interrumpir (HU-MYL-02) y terminar (HU-MYL-03). La fila de la habitación se bloquea
 * en cada cambio, así dos empleados no toman la misma habitación a la vez.
 */
@Service
public class LimpiezaService {

    private final HistorialEstadoService historial;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<HabitacionEventos> eventos;
    private final Clock reloj;

    public LimpiezaService(HistorialEstadoService historial, JdbcTemplate jdbc,
            ObjectProvider<HabitacionEventos> eventos, Clock reloj) {
        this.historial = historial;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * LIBRE con condición SUCIA o EN_LIMPIEZA (ni OCUPADA ni FUERA_DE_SERVICIO).
     * Primero las que tienen una llegada hoy; luego las que llevan más tiempo sucias.
     */
    @Transactional(readOnly = true)
    public List<HabitacionLimpieza> pendientes() {
        return jdbc.query("""
                SELECT h.id, h.numero, h.piso, h.condicion, e.id, e.nombre_completo,
                       EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                               AND r.estado = 'CONFIRMADA' AND r.fecha_entrada = ?) AS llegada_hoy,
                       coalesce((SELECT max(he.fecha) FROM historial_estados he
                                 WHERE he.tipo_entidad = 'HABITACION_CONDICION' AND he.id_entidad = h.id
                                   AND he.estado_nuevo = 'SUCIA'), now()) AS sucia_desde
                FROM habitaciones h
                LEFT JOIN empleados e ON e.id = h.limpieza_empleado_id
                WHERE h.estado = 'ACTIVO' AND h.ocupacion = 'LIBRE' AND h.condicion IN ('SUCIA', 'EN_LIMPIEZA')
                ORDER BY llegada_hoy DESC, sucia_desde, h.numero""",
                (rs, i) -> new HabitacionLimpieza(new HabitacionReferencia(rs.getLong(1), rs.getString(2),
                        rs.getInt(3)), CondicionHabitacion.valueOf(rs.getString(4)), rs.getBoolean(7),
                        fecha(rs.getTimestamp(8)),
                        rs.getObject(5) == null ? null : new ResponsableEmpleado(rs.getLong(5), rs.getString(6))),
                LocalDate.now(reloj));
    }

    /** LIBRE + SUCIA → EN_LIMPIEZA a nombre del empleado. Si otro ya la inició, 409 con su nombre. */
    @Transactional
    public ResultadoCondicionHabitacion iniciar(long habitacionId, long empleadoId) {
        Habitacion h = bloquear(habitacionId);
        if ("EN_LIMPIEZA".equals(h.condicion())) {
            throw h.aCargo() == empleadoId
                    ? ApiException.conflicto("LIMPIEZA_YA_INICIADA",
                            "Ya iniciaste la limpieza de la habitación " + h.numero() + ".")
                    : ApiException.conflicto("LIMPIEZA_EN_CURSO",
                            "La habitación " + h.numero() + " ya la está limpiando " + h.nombreACargo() + ".");
        }
        if (!"LIBRE".equals(h.ocupacion()) || !"SUCIA".equals(h.condicion())) {
            throw ApiException.conflicto("ESTADO_INVALIDO",
                    "Solo se limpia una habitación libre y sucia. La habitación " + h.numero() + " no lo está.");
        }
        return cambiar(h, "EN_LIMPIEZA", empleadoId, empleadoId);
    }

    /** Solo el empleado a cargo: EN_LIMPIEZA → SUCIA y la habitación queda sin responsable. */
    @Transactional
    public ResultadoCondicionHabitacion interrumpir(long habitacionId, long empleadoId) {
        return cambiar(aMiCargo(habitacionId, empleadoId), "SUCIA", null, empleadoId);
    }

    /** Solo el empleado a cargo: EN_LIMPIEZA → LIMPIA; sale de pendientes. */
    @Transactional
    public ResultadoCondicionHabitacion terminar(long habitacionId, long empleadoId) {
        return cambiar(aMiCargo(habitacionId, empleadoId), "LIMPIA", null, empleadoId);
    }

    private Habitacion aMiCargo(long habitacionId, long empleadoId) {
        Habitacion h = bloquear(habitacionId);
        if (!"EN_LIMPIEZA".equals(h.condicion())) {
            throw ApiException.conflicto("ESTADO_INVALIDO",
                    "La habitación " + h.numero() + " no está en limpieza.");
        }
        if (h.aCargo() != empleadoId) {
            throw ApiException.conflicto("NO_ESTA_A_TU_CARGO",
                    "La limpieza de la habitación " + h.numero() + " está a cargo de " + h.nombreACargo() + ".");
        }
        return h;
    }

    private ResultadoCondicionHabitacion cambiar(Habitacion h, String condicion, Long aCargo, long empleadoId) {
        jdbc.update("UPDATE habitaciones SET condicion = ?, limpieza_empleado_id = ? WHERE id = ?",
                condicion, aCargo, h.id());
        historial.registrar(TipoEntidadHistorial.HABITACION_CONDICION, h.id(), h.condicion(), condicion,
                Responsable.empleado(empleadoId), null);
        publicarDespuesDeConfirmar(h.id());
        ResponsableEmpleado responsable = aCargo == null ? null : new ResponsableEmpleado(aCargo,
                jdbc.queryForObject("SELECT nombre_completo FROM empleados WHERE id = ?", String.class, aCargo));
        return new ResultadoCondicionHabitacion(new HabitacionReferencia(h.id(), h.numero(), h.piso()),
                OcupacionHabitacion.valueOf(h.ocupacion()), CondicionHabitacion.valueOf(condicion), responsable);
    }

    /**
     * Bloquea la habitación y luego lee el nombre de quien la tiene a cargo: si otro
     * empleado acaba de confirmar, la fila bloqueada ya trae su id (un JOIN en la misma
     * consulta devolvería el empleado anterior).
     */
    private Habitacion bloquear(long habitacionId) {
        Habitacion h = jdbc.query("""
                SELECT id, numero, piso, ocupacion, condicion, limpieza_empleado_id
                FROM habitaciones WHERE id = ? AND estado = 'ACTIVO' FOR UPDATE""",
                (rs, i) -> new Habitacion(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getLong(6), null),
                habitacionId).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        if (h.aCargo() == 0) {
            return h;
        }
        String nombre = jdbc.queryForObject("SELECT nombre_completo FROM empleados WHERE id = ?", String.class,
                h.aCargo());
        return new Habitacion(h.id(), h.numero(), h.piso(), h.ocupacion(), h.condicion(), h.aCargo(), nombre);
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

    private OffsetDateTime fecha(Timestamp t) {
        return OffsetDateTime.ofInstant(t.toInstant(), reloj.getZone());
    }

    /** {@code aCargo} es 0 si nadie la tiene a su nombre. */
    private record Habitacion(long id, String numero, int piso, String ocupacion, String condicion, long aCargo,
            String nombreACargo) {
    }
}
