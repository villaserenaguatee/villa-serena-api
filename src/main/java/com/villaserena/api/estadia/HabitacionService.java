package com.villaserena.api.estadia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.estadia.dto.HabitacionEstado;
import com.villaserena.api.estadia.dto.IncidenciaBloqueante;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.dto.HabitacionReferencia;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * Habitaciones para Recepción (OBJ-2C): estado con indicadores (HU-REC-10), marcar
 * sucia (HU-REC-11) y asignar o cambiar la habitación de una reserva (HU-REC-07).
 * "Hoy" se calcula en America/Guatemala con el reloj del API.
 */
@Service
public class HabitacionService {

    /** Violación de la restricción EXCLUDE de reservas traslapadas en PostgreSQL. */
    private static final String SQL_EXCLUSION = "23P01";

    private static final String CONSULTA_ESTADO = """
            SELECT h.id, h.numero, h.piso, t.id, t.nombre, h.ocupacion, h.condicion,
                   EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                           AND r.estado = 'CONFIRMADA' AND r.fecha_entrada = ?) AS llega_hoy,
                   EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                           AND r.estado = 'EN_ESTADIA' AND r.fecha_salida = ?) AS sale_hoy,
                   h.ocupacion = 'OCUPADA' AND EXISTS (SELECT 1 FROM incidencias i WHERE i.habitacion_id = h.id
                           AND i.impide_uso AND i.estado <> 'RESUELTA') AS incidencia_pendiente,
                   b.id, b.descripcion, b.estado, b.creado_en
            FROM habitaciones h
            JOIN tipos_habitacion t ON t.id = h.tipo_habitacion_id
            LEFT JOIN LATERAL (
                SELECT i.id, i.descripcion, i.estado, i.creado_en FROM incidencias i
                WHERE i.habitacion_id = h.id AND i.impide_uso AND i.estado <> 'RESUELTA'
                ORDER BY i.creado_en, i.id LIMIT 1
            ) b ON h.condicion = 'FUERA_DE_SERVICIO'
            WHERE h.estado = 'ACTIVO'""";

    private final ReservaService reservaService;
    private final HistorialEstadoService historial;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<HabitacionEventos> eventos;
    private final Clock reloj;

    public HabitacionService(ReservaService reservaService, HistorialEstadoService historial, JdbcTemplate jdbc,
            ObjectProvider<HabitacionEventos> eventos, Clock reloj) {
        this.reservaService = reservaService;
        this.historial = historial;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** Habitaciones activas con su estado e indicadores; los filtros nulos no se aplican. */
    @Transactional(readOnly = true)
    public List<HabitacionEstado> listar(OcupacionHabitacion ocupacion, CondicionHabitacion condicion,
            Long tipoHabitacionId, Integer piso) {
        LocalDate hoy = LocalDate.now(reloj);
        StringBuilder sql = new StringBuilder(CONSULTA_ESTADO);
        List<Object> parametros = new ArrayList<>(List.of(hoy, hoy));
        if (ocupacion != null) {
            sql.append(" AND h.ocupacion = ?");
            parametros.add(ocupacion.name());
        }
        if (condicion != null) {
            sql.append(" AND h.condicion = ?");
            parametros.add(condicion.name());
        }
        if (tipoHabitacionId != null) {
            sql.append(" AND h.tipo_habitacion_id = ?");
            parametros.add(tipoHabitacionId);
        }
        if (piso != null) {
            sql.append(" AND h.piso = ?");
            parametros.add(piso);
        }
        sql.append(" ORDER BY h.piso, h.numero");
        return jdbc.query(sql.toString(), (rs, i) -> estado(rs), parametros.toArray());
    }

    /**
     * Habitaciones activas del tipo que se pueden asignar en esas fechas: no están
     * FUERA_DE_SERVICIO ni tienen otra reserva activa traslapada. No exige que estén limpias.
     */
    @Transactional(readOnly = true)
    public List<HabitacionReferencia> asignables(long tipoHabitacionId, LocalDate entrada, LocalDate salida) {
        if (!salida.isAfter(entrada)) {
            throw ApiException.datosInvalidos("La fecha de salida debe ser posterior a la de entrada.",
                    List.of(new DetalleError("salida", "Debe ser posterior a la entrada.")));
        }
        return jdbc.query("""
                SELECT h.id, h.numero, h.piso FROM habitaciones h
                WHERE h.tipo_habitacion_id = ? AND h.estado = 'ACTIVO' AND h.condicion <> 'FUERA_DE_SERVICIO'
                  AND NOT EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                      AND r.estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'EN_ESTADIA')
                      AND daterange(r.fecha_entrada, r.fecha_salida, '[)') && daterange(?, ?, '[)'))
                ORDER BY h.piso, h.numero""",
                (rs, i) -> new HabitacionReferencia(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                tipoHabitacionId, entrada, salida);
    }

    /** Solo LIBRE + LIMPIA → SUCIA (RN-HAB-004); cualquier otro caso es 409. */
    @Transactional
    public HabitacionEstado marcarSucia(long habitacionId, long empleadoId) {
        Habitacion habitacion = jdbc.query(
                "SELECT numero, ocupacion, condicion, estado FROM habitaciones WHERE id = ? FOR UPDATE",
                (rs, i) -> new Habitacion(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                habitacionId).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        if (!"ACTIVO".equals(habitacion.estado())) {
            throw ApiException.conflicto("HABITACION_INACTIVA",
                    "La habitación " + habitacion.numero() + " está inactiva.");
        }
        if (!"LIBRE".equals(habitacion.ocupacion()) || !"LIMPIA".equals(habitacion.condicion())) {
            throw ApiException.conflicto("ESTADO_INVALIDO",
                    "Solo se puede marcar como sucia una habitación libre y limpia. La habitación "
                            + habitacion.numero() + " está " + etiqueta(habitacion) + ".");
        }
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE id = ?", habitacionId);
        historial.registrar(TipoEntidadHistorial.HABITACION_CONDICION, habitacionId, "LIMPIA", "SUCIA",
                Responsable.empleado(empleadoId), null);
        publicarDespuesDeConfirmar(Set.of(habitacionId));
        return estadoDe(habitacionId);
    }

    /**
     * Asigna o cambia la habitación antes del check-in (HU-REC-07) con las reglas de
     * {@link ReservaService#asignarHabitacion}. Si otra reserva gana la habitación al
     * mismo tiempo, la restricción EXCLUDE de la base lo impide y responde 409.
     */
    @Transactional
    public Reserva asignar(String codigo, long habitacionId, long empleadoId) {
        Reserva reserva = reservaService.porCodigo(codigo);
        Long anterior = reserva.getHabitacionId();
        try {
            reservaService.asignarHabitacion(reserva, habitacionId, Responsable.empleado(empleadoId));
        } catch (DataIntegrityViolationException e) {
            if (esExclusion(e)) {
                throw ApiException.conflicto("HABITACION_OCUPADA",
                        "La habitación ya está reservada en esas fechas.");
            }
            throw e;
        }
        // Cambian los indicadores ("Llega hoy") de la habitación nueva y de la anterior.
        publicarDespuesDeConfirmar(anterior == null || anterior == habitacionId
                ? Set.of(habitacionId)
                : Set.of(habitacionId, anterior));
        return reserva;
    }

    private HabitacionEstado estadoDe(long habitacionId) {
        LocalDate hoy = LocalDate.now(reloj);
        return jdbc.query(CONSULTA_ESTADO + " AND h.id = ?", (rs, i) -> estado(rs), hoy, hoy, habitacionId)
                .getFirst();
    }

    /** Fila de {@link #CONSULTA_ESTADO} a {@link HabitacionEstado}. */
    private HabitacionEstado estado(ResultSet rs) throws SQLException {
        IncidenciaBloqueante bloqueante = rs.getObject(11) == null ? null
                : new IncidenciaBloqueante(rs.getLong(11), rs.getString(12), rs.getString(13),
                        fecha(rs.getTimestamp(14)));
        return new HabitacionEstado(rs.getLong(1), rs.getString(2), rs.getInt(3),
                new TipoHabitacionReferencia(rs.getLong(4), rs.getString(5)),
                OcupacionHabitacion.valueOf(rs.getString(6)), CondicionHabitacion.valueOf(rs.getString(7)),
                rs.getBoolean(8), rs.getBoolean(9), rs.getBoolean(10), bloqueante);
    }

    /** Evento 4 (cambio de habitación) solo si la transacción se confirma. */
    private void publicarDespuesDeConfirmar(Set<Long> habitaciones) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventos.ifAvailable(e -> habitaciones.forEach(e::habitacionCambio));
            }
        });
    }

    private OffsetDateTime fecha(Timestamp t) {
        return OffsetDateTime.ofInstant(t.toInstant(), reloj.getZone());
    }

    private static boolean esExclusion(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && SQL_EXCLUSION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static String etiqueta(Habitacion h) {
        String ocupacion = "LIBRE".equals(h.ocupacion()) ? "libre" : "ocupada";
        String condicion = switch (h.condicion()) {
            case "LIMPIA" -> "limpia";
            case "SUCIA" -> "sucia";
            case "EN_LIMPIEZA" -> "en limpieza";
            default -> "fuera de servicio";
        };
        return ocupacion + " y " + condicion;
    }

    private record Habitacion(String numero, String ocupacion, String condicion, String estado) {
    }
}
