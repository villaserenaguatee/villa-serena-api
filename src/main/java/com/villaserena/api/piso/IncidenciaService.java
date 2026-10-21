package com.villaserena.api.piso;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.archivos.ImagenesService;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.piso.dto.Incidencias;
import com.villaserena.api.piso.dto.ReportarIncidenciaPeticion;
import com.villaserena.api.piso.dto.ResponsableEmpleado;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/**
 * Incidencias de mantenimiento (OBJ-3B-1, parte B): reportar (HU-MYL-06, HU-REC-17),
 * consultar y tomar (HU-MYL-07, HU-ADM-10) y resolver (HU-MYL-08). Una incidencia que
 * impide el uso deja la habitación LIBRE en FUERA_DE_SERVICIO; si está OCUPADA solo
 * queda el indicador "Incidencia pendiente" y el check-out la pasa a FUERA_DE_SERVICIO.
 */
@Service
public class IncidenciaService {

    /** Quién consulta: el Administrador ve todo; Mantenimiento solo su cola activa. */
    public enum Vista {
        ADMIN, MANTENIMIENTO
    }

    private static final String SELECT = """
            SELECT i.id, h.id, h.number AS "numero", h.floor AS "piso", i.description AS "descripcion", i.blocks_use AS "impide_uso", h.occupancy = 'OCUPADA',
                   i.status AS "estado", rep.id, rep.full_name AS "nombre_completo", i.created_at AS "creado_en", tec.id, tec.full_name AS "nombre_completo",
                   (SELECT max(he.date) FROM status_history he WHERE he.entity_type = 'INCIDENCIA'
                      AND he.entity_id = i.id AND he.new_status = 'RESUELTA') AS resuelta_en,
                   i.photo_key AS "clave_foto", i.solution AS "solucion" FROM incidents i
            JOIN rooms h ON h.id = i.room_id
            JOIN employees rep ON rep.id = i.reported_by_employee_id
            LEFT JOIN employees tec ON tec.id = i.technician_id""";

    private final HistorialEstadoService historial;
    private final AlmacenArchivos almacen;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<HabitacionEventos> eventos;
    private final Clock reloj;

    public IncidenciaService(HistorialEstadoService historial, AlmacenArchivos almacen, JdbcTemplate jdbc,
            ObjectProvider<HabitacionEventos> eventos, Clock reloj) {
        this.historial = historial;
        this.almacen = almacen;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Queda REPORTADA. Si impide el uso y la habitación está LIBRE pasa a
     * FUERA_DE_SERVICIO (si alguien la estaba limpiando, deja de estar a su cargo).
     */
    @Transactional
    public Incidencias.Creada reportar(ReportarIncidenciaPeticion p, long empleadoId) {
        Habitacion h = jdbc.query("""
                SELECT id, number AS "numero", occupancy AS "ocupacion", condition AS "condicion" FROM rooms WHERE id = ? AND status = 'ACTIVO'
                FOR UPDATE""",
                (rs, i) -> new Habitacion(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                p.habitacionId()).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO",
                        "No se encontró la habitación indicada."));
        String foto = validarFoto(p.fotoClave());
        Responsable responsable = Responsable.empleado(empleadoId);

        Creada creada = jdbc.queryForObject("""
                INSERT INTO incidents (room_id, description, blocks_use, photo_key, reported_by_employee_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id, created_at""",
                (rs, i) -> new Creada(rs.getLong(1), rs.getTimestamp(2)),
                h.id(), p.descripcion().trim(), p.impideUso(), foto, empleadoId);
        historial.registrar(TipoEntidadHistorial.INCIDENCIA, creada.id(), null, "REPORTADA", responsable, null);

        if (p.impideUso()) {
            if ("LIBRE".equals(h.ocupacion()) && !"FUERA_DE_SERVICIO".equals(h.condicion())) {
                jdbc.update("""
                        UPDATE rooms SET condition = 'FUERA_DE_SERVICIO', cleaning_employee_id = NULL
                        WHERE id = ?""", h.id());
                historial.registrar(TipoEntidadHistorial.HABITACION_CONDICION, h.id(), h.condicion(),
                        "FUERA_DE_SERVICIO", responsable, null);
            }
            // LIBRE: cambia la condición; OCUPADA: aparece "Incidencia pendiente".
            publicarDespuesDeConfirmar(h.id());
        }
        return new Incidencias.Creada(creada.id(), h.id(), "REPORTADA", fecha(creada.creadoEn()));
    }

    /**
     * Administrador: todas, primero las activas y luego las más recientes, con filtros.
     * Mantenimiento: solo REPORTADA y EN_PROCESO, las más antiguas primero; pedir
     * RESUELTA no es parte de su cola (403).
     */
    @Transactional(readOnly = true)
    public List<Incidencias.Resumen> listar(Vista vista, String estado, Long habitacionId, Long tecnicoId) {
        if (vista == Vista.MANTENIMIENTO && "RESUELTA".equals(estado)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                    "Las incidencias resueltas solo las consulta el Administrador.");
        }
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE TRUE");
        List<Object> parametros = new ArrayList<>();
        if (vista == Vista.MANTENIMIENTO) {
            sql.append(" AND i.status IN ('REPORTADA', 'EN_PROCESO')");
        }
        if (estado != null) {
            sql.append(" AND i.status = ?");
            parametros.add(estado);
        }
        if (habitacionId != null) {
            sql.append(" AND i.room_id = ?");
            parametros.add(habitacionId);
        }
        if (tecnicoId != null) {
            sql.append(" AND i.technician_id = ?");
            parametros.add(tecnicoId);
        }
        sql.append(vista == Vista.MANTENIMIENTO
                ? " ORDER BY i.created_at, i.id"
                : " ORDER BY (i.status = 'RESUELTA'), i.created_at DESC, i.id DESC");
        return jdbc.query(sql.toString(), (rs, i) -> resumen(rs), parametros.toArray());
    }

    @Transactional(readOnly = true)
    public Incidencias.Detalle detalle(long id) {
        Incidencias.Resumen r = jdbc.query(SELECT + " WHERE i.id = ?", (rs, i) -> resumen(rs), id).stream()
                .findFirst().orElseThrow(ApiException::noEncontrado);
        String solucion = jdbc.queryForObject("SELECT solution AS \"solucion\" FROM incidents WHERE id = ?", String.class, id);
        List<Incidencias.HistorialOperacion> cambios = jdbc.query("""
                SELECT h.previous_status AS "estado_anterior", h.new_status AS "estado_nuevo", coalesce(e.full_name, 'SISTEMA'), h.date AS "fecha", h.reason AS "motivo" FROM status_history h LEFT JOIN employees e ON e.id = h.responsible_id
                WHERE h.entity_type = 'INCIDENCIA' AND h.entity_id = ?
                ORDER BY h.date, h.id""",
                (rs, i) -> new Incidencias.HistorialOperacion(rs.getString(1), rs.getString(2), rs.getString(3),
                        fecha(rs.getTimestamp(4)), rs.getString(5)),
                id);
        return new Incidencias.Detalle(r.id(), r.habitacion(), r.descripcion(), r.impideUso(), r.habitacionOcupada(),
                r.estado(), r.reportadaPor(), r.reportadaEn(), r.tecnicoACargo(), r.resueltaEn(), r.fotoUrl(),
                solucion, cambios);
    }

    /** REPORTADA → EN_PROCESO a nombre del técnico. Si otro ya la tomó, 409 con su nombre. */
    @Transactional
    public Incidencias.Detalle tomar(long id, long tecnicoId) {
        Bloqueada inc = bloquear(id);
        if ("EN_PROCESO".equals(inc.estado())) {
            throw inc.tecnico() == tecnicoId
                    ? ApiException.conflicto("INCIDENCIA_YA_TOMADA", "Ya tienes esta incidencia a tu cargo.")
                    : ApiException.conflicto("INCIDENCIA_TOMADA",
                            "Esta incidencia ya la tomó " + inc.nombreTecnico() + ".");
        }
        if (!"REPORTADA".equals(inc.estado())) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "La incidencia ya está resuelta.");
        }
        jdbc.update("UPDATE incidents SET status = 'EN_PROCESO', technician_id = ? WHERE id = ?", tecnicoId, id);
        historial.registrar(TipoEntidadHistorial.INCIDENCIA, id, "REPORTADA", "EN_PROCESO",
                Responsable.empleado(tecnicoId), null);
        return detalle(id);
    }

    /**
     * Solo el técnico a cargo: EN_PROCESO → RESUELTA con la solución (ya no se modifica).
     * Si era la última incidencia que impedía el uso: FUERA_DE_SERVICIO → SUCIA; si la
     * habitación está OCUPADA, desaparece el indicador.
     */
    @Transactional
    public Incidencias.Detalle resolver(long id, long tecnicoId, String solucion) {
        Bloqueada inc = bloquear(id);
        if (!"EN_PROCESO".equals(inc.estado())) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "REPORTADA".equals(inc.estado())
                    ? "Toma la incidencia antes de resolverla."
                    : "La incidencia ya está resuelta y no se puede modificar.");
        }
        if (inc.tecnico() != tecnicoId) {
            throw ApiException.conflicto("NO_ESTA_A_TU_CARGO",
                    "Esta incidencia está a cargo de " + inc.nombreTecnico() + ".");
        }
        Responsable responsable = Responsable.empleado(tecnicoId);
        jdbc.update("UPDATE incidents SET status = 'RESUELTA', solution = ? WHERE id = ?", solucion.trim(), id);
        historial.registrar(TipoEntidadHistorial.INCIDENCIA, id, "EN_PROCESO", "RESUELTA", responsable, null);

        if (inc.impideUso() && !tieneIncidenciaQueImpideUso(inc.habitacionId())) {
            String condicion = jdbc.queryForObject("SELECT condition AS \"condicion\" FROM rooms WHERE id = ? FOR UPDATE",
                    String.class, inc.habitacionId());
            if ("FUERA_DE_SERVICIO".equals(condicion)) {
                jdbc.update("UPDATE rooms SET condition = 'SUCIA' WHERE id = ?", inc.habitacionId());
                historial.registrar(TipoEntidadHistorial.HABITACION_CONDICION, inc.habitacionId(),
                        "FUERA_DE_SERVICIO", "SUCIA", responsable, null);
            }
            publicarDespuesDeConfirmar(inc.habitacionId());
        }
        return detalle(id);
    }

    /**
     * ¿La habitación tiene una incidencia sin resolver que impide su uso? Lo consulta
     * el check-out (OBJ-4A) para dejarla FUERA_DE_SERVICIO al liberarla.
     */
    @Transactional(readOnly = true)
    public boolean tieneIncidenciaQueImpideUso(long habitacionId) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM incidents
                               WHERE room_id = ? AND blocks_use AND status <> 'RESUELTA')""",
                Boolean.class, habitacionId);
    }

    /** La foto debe ser una imagen de incidencia ya subida y no usada en otra incidencia. */
    private String validarFoto(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String c = clave.trim();
        boolean valida = c.startsWith(ImagenesService.PREFIJO_INCIDENCIAS) && !c.contains("..")
                && jdbc.queryForObject("SELECT count(*)  FROM incidents WHERE photo_key = ?", Integer.class, c) == 0
                && almacen.existePrivado(c);
        if (!valida) {
            throw ApiException.datosInvalidos("La foto no es válida. Súbela de nuevo.",
                    List.of(new DetalleError("fotoClave", "Usa la clave que devolvió la subida de la imagen.")));
        }
        return c;
    }

    /** Bloquea la incidencia y luego lee el nombre del técnico (ver LimpiezaService#bloquear). */
    private Bloqueada bloquear(long id) {
        Bloqueada inc = jdbc.query("""
                SELECT id, room_id AS "habitacion_id", blocks_use AS "impide_uso", status AS "estado", technician_id AS "tecnico_id" FROM incidents WHERE id = ? FOR UPDATE""",
                (rs, i) -> new Bloqueada(rs.getLong(1), rs.getLong(2), rs.getBoolean(3), rs.getString(4),
                        rs.getLong(5), null),
                id).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        if (inc.tecnico() == 0) {
            return inc;
        }
        String nombre = jdbc.queryForObject("SELECT full_name AS \"nombre_completo\" FROM employees WHERE id = ?", String.class,
                inc.tecnico());
        return new Bloqueada(inc.id(), inc.habitacionId(), inc.impideUso(), inc.estado(), inc.tecnico(), nombre);
    }

    private Incidencias.Resumen resumen(ResultSet rs) throws SQLException {
        String clave = rs.getString(15);
        return new Incidencias.Resumen(rs.getLong(1), new HabitacionReferencia(rs.getLong(2), rs.getString(3),
                rs.getInt(4)), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7), rs.getString(8),
                new ResponsableEmpleado(rs.getLong(9), rs.getString(10)), fecha(rs.getTimestamp(11)),
                rs.getObject(12) == null ? null : new ResponsableEmpleado(rs.getLong(12), rs.getString(13)),
                rs.getTimestamp(14) == null ? null : fecha(rs.getTimestamp(14)),
                clave == null ? null : almacen.urlFirmada(clave));
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

    private record Habitacion(long id, String numero, String ocupacion, String condicion) {
    }

    private record Creada(long id, Timestamp creadoEn) {
    }

    /** {@code tecnico} es 0 si nadie la tomó. */
    private record Bloqueada(long id, long habitacionId, boolean impideUso, String estado, long tecnico,
            String nombreTecnico) {
    }
}
