package com.villaserena.api.piso;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
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
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.SolicitudEventos;
import com.villaserena.api.notificaciones.TipoNotificacion;
import com.villaserena.api.piso.dto.ResponsableEmpleado;
import com.villaserena.api.piso.dto.Solicitudes;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/**
 * Solicitudes del huésped (OBJ-3B-2): limpieza y artículos desde la app (HU-HUE-12 a
 * 14) y su atención por Limpieza (HU-MYL-04 y 05). El huésped solo usa su reserva
 * (una ajena responde 404) y solo crea solicitudes durante la estadía. Pedir limpieza
 * no cambia la condición de la habitación; pedir artículos no descuenta inventario.
 */
@Service
public class SolicitudService {

    private static final String SELECT = """
            SELECT s.id, s.tipo, s.creado_en, s.estado, s.comentario, h.id, h.numero, h.piso, e.id,
                   e.nombre_completo
            FROM solicitudes s
            JOIN habitaciones h ON h.id = s.habitacion_id
            LEFT JOIN empleados e ON e.id = s.empleado_id""";

    private final HistorialEstadoService historial;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<SolicitudEventos> eventos;
    private final Clock reloj;

    public SolicitudService(HistorialEstadoService historial, OutboxService outbox, JdbcTemplate jdbc,
            ObjectProvider<SolicitudEventos> eventos, Clock reloj) {
        this.historial = historial;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------ huésped

    /** Catálogo de artículos con su máximo (datos iniciales); solo durante la estadía. */
    @Transactional(readOnly = true)
    public List<Solicitudes.ArticuloSolicitable> articulos(String codigo, long huespedId) {
        enEstadia(reservaDelHuesped(codigo, huespedId));
        return jdbc.query("SELECT id, nombre, cantidad_maxima FROM articulos ORDER BY id",
                (rs, i) -> new Solicitudes.ArticuloSolicitable(rs.getLong(1), rs.getString(2), rs.getInt(3)));
    }

    /** Una sola solicitud de limpieza PENDIENTE o EN_PROCESO por habitación (RN-LIM-005). */
    @Transactional
    public Solicitudes.DelHuesped pedirLimpieza(String codigo, long huespedId, String comentario) {
        Reserva r = enEstadia(reservaDelHuesped(codigo, huespedId));
        String texto = comentario == null || comentario.isBlank() ? null : comentario.trim();
        if (jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM solicitudes WHERE habitacion_id = ? AND tipo = 'LIMPIEZA'
                               AND estado IN ('PENDIENTE', 'EN_PROCESO'))""", Boolean.class, r.habitacionId())) {
            throw limpiezaYaSolicitada();
        }
        long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO solicitudes (reserva_id, habitacion_id, tipo, comentario)
                    VALUES (?, ?, 'LIMPIEZA', ?) RETURNING id""", Long.class, r.id(), r.habitacionId(), texto);
        } catch (DuplicateKeyException e) {
            // Otra solicitud ganó al mismo tiempo: el índice único de la base lo impide.
            throw limpiezaYaSolicitada();
        }
        return creada(id);
    }

    private static ApiException limpiezaYaSolicitada() {
        return ApiException.conflicto("LIMPIEZA_YA_SOLICITADA",
                "Ya hay una solicitud de limpieza en curso para tu habitación.");
    }

    /** Artículos del catálogo sin pasar el máximo de cada uno; un artículo una sola vez por solicitud. */
    @Transactional
    public Solicitudes.DelHuesped pedirArticulos(String codigo, long huespedId,
            List<Solicitudes.ArticuloPedido> pedidos) {
        Reserva r = enEstadia(reservaDelHuesped(codigo, huespedId));
        Map<Long, Integer> maximos = new LinkedHashMap<>();
        jdbc.query("SELECT id, cantidad_maxima FROM articulos", rs -> {
            maximos.put(rs.getLong(1), rs.getInt(2));
        });
        List<DetalleError> errores = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < pedidos.size(); i++) {
            Solicitudes.ArticuloPedido p = pedidos.get(i);
            String campo = "articulos[" + i + "]";
            if (!maximos.containsKey(p.articuloId())) {
                errores.add(new DetalleError(campo + ".articuloId", "El artículo no existe."));
            } else if (!vistos.add(p.articuloId())) {
                errores.add(new DetalleError(campo + ".articuloId", "El artículo está repetido."));
            } else if (p.cantidad() > maximos.get(p.articuloId())) {
                errores.add(new DetalleError(campo + ".cantidad",
                        "Puedes pedir hasta " + maximos.get(p.articuloId()) + "."));
            }
        }
        if (!errores.isEmpty()) {
            throw ApiException.datosInvalidos("Revisa los artículos y sus cantidades.", errores);
        }
        long id = jdbc.queryForObject("""
                INSERT INTO solicitudes (reserva_id, habitacion_id, tipo) VALUES (?, ?, 'ARTICULOS') RETURNING id""",
                Long.class, r.id(), r.habitacionId());
        for (Solicitudes.ArticuloPedido p : pedidos) {
            jdbc.update("INSERT INTO solicitud_items (solicitud_id, articulo_id, cantidad) VALUES (?, ?, ?)",
                    id, p.articuloId(), p.cantidad());
        }
        return creada(id);
    }

    /** Las solicitudes de su reserva, en cualquier estado de la reserva (también después del check-out). */
    @Transactional(readOnly = true)
    public List<Solicitudes.DelHuesped> misSolicitudes(String codigo, long huespedId) {
        Reserva r = reservaDelHuesped(codigo, huespedId);
        List<Solicitudes.ParaLimpieza> filas = jdbc.query(
                SELECT + " WHERE s.reserva_id = ? ORDER BY s.creado_en DESC, s.id DESC", (rs, i) -> fila(rs), r.id());
        return conArticulos(filas).stream().map(SolicitudService::delHuesped).toList();
    }

    /** Solo PENDIENTE → CANCELADA; se conserva con su historial. */
    @Transactional
    public Solicitudes.DelHuesped cancelar(String codigo, long huespedId, long solicitudId) {
        Reserva r = reservaDelHuesped(codigo, huespedId);
        Bloqueada s = bloquear(solicitudId);
        if (s.reservaId() != r.id()) {
            throw ApiException.noEncontrado();
        }
        if (!"PENDIENTE".equals(s.estado())) {
            throw ApiException.conflicto("ESTADO_INVALIDO",
                    "Solo puedes cancelar una solicitud pendiente; esta ya está " + etiqueta(s.estado()) + ".");
        }
        cambiar(solicitudId, "PENDIENTE", "CANCELADA", null, Responsable.HUESPED);
        return delHuesped(una(solicitudId));
    }

    // ------------------------------------------------------------ Limpieza

    /** PENDIENTE y EN_PROCESO, las más antiguas primero. */
    @Transactional(readOnly = true)
    public List<Solicitudes.ParaLimpieza> cola() {
        return conArticulos(jdbc.query(
                SELECT + " WHERE s.estado IN ('PENDIENTE', 'EN_PROCESO') ORDER BY s.creado_en, s.id",
                (rs, i) -> fila(rs)));
    }

    /** PENDIENTE → EN_PROCESO a nombre del empleado; tomada o cancelada responde 409 con el motivo. */
    @Transactional
    public Solicitudes.ParaLimpieza tomar(long solicitudId, long empleadoId) {
        Bloqueada s = bloquear(solicitudId);
        switch (s.estado()) {
            case "PENDIENTE" -> {
            }
            case "EN_PROCESO" -> throw s.aCargo() == empleadoId
                    ? ApiException.conflicto("SOLICITUD_YA_TOMADA", "Ya tienes esta solicitud a tu cargo.")
                    : ApiException.conflicto("SOLICITUD_TOMADA",
                            "Esta solicitud ya la tomó " + nombreEmpleado(s.aCargo()) + ".");
            case "CANCELADA" -> throw ApiException.conflicto("SOLICITUD_CANCELADA",
                    "El huésped canceló esta solicitud.");
            default -> throw ApiException.conflicto("ESTADO_INVALIDO", "La solicitud ya fue atendida.");
        }
        cambiar(solicitudId, "PENDIENTE", "EN_PROCESO", empleadoId, Responsable.empleado(empleadoId));
        return una(solicitudId);
    }

    /**
     * Solo el empleado a cargo: EN_PROCESO → ATENDIDA. Encola el push "Tu solicitud
     * fue atendida" en el Outbox para cada dispositivo del huésped en estadía.
     */
    @Transactional
    public Solicitudes.ParaLimpieza atender(long solicitudId, long empleadoId) {
        Bloqueada s = bloquear(solicitudId);
        if ("CANCELADA".equals(s.estado())) {
            throw ApiException.conflicto("SOLICITUD_CANCELADA", "El huésped canceló esta solicitud.");
        }
        if (!"EN_PROCESO".equals(s.estado())) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "PENDIENTE".equals(s.estado())
                    ? "Toma la solicitud antes de atenderla."
                    : "La solicitud ya fue atendida.");
        }
        if (s.aCargo() != empleadoId) {
            throw ApiException.conflicto("NO_ESTA_A_TU_CARGO",
                    "Esta solicitud está a cargo de " + nombreEmpleado(s.aCargo()) + ".");
        }
        cambiar(solicitudId, "EN_PROCESO", "ATENDIDA", empleadoId, Responsable.empleado(empleadoId));
        avisarAtendida(solicitudId);
        return una(solicitudId);
    }

    // ------------------------------------------------------------ apoyo

    private Solicitudes.DelHuesped creada(long id) {
        historial.registrar(TipoEntidadHistorial.SOLICITUD, id, null, "PENDIENTE", Responsable.HUESPED, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventos.ifAvailable(e -> e.solicitudNueva(id));
            }
        });
        return delHuesped(una(id));
    }

    private void cambiar(long id, String anterior, String nuevo, Long empleadoId, Responsable responsable) {
        jdbc.update("UPDATE solicitudes SET estado = ?, empleado_id = coalesce(?, empleado_id) WHERE id = ?",
                nuevo, empleadoId, id);
        historial.registrar(TipoEntidadHistorial.SOLICITUD, id, anterior, nuevo, responsable, null);
    }

    /** Push sin datos personales (x-push del contrato); solo si el huésped sigue en estadía. */
    private void avisarAtendida(long solicitudId) {
        jdbc.query("""
                SELECT d.token_expo, r.codigo FROM solicitudes s
                JOIN reservas r ON r.id = s.reserva_id
                JOIN dispositivos_push d ON d.huesped_id = r.huesped_id
                WHERE s.id = ? AND r.estado = 'EN_ESTADIA'""", rs -> {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("title", "Villa Serena");
            datos.put("body", "Tu solicitud fue atendida");
            datos.put("data", Map.of("pantalla", "solicitudes", "codigoReserva", rs.getString(2),
                    "solicitudId", solicitudId));
            outbox.encolar(TipoNotificacion.PUSH_SOLICITUD_ATENDIDA, rs.getString(1), datos);
        }, solicitudId);
    }

    private Reserva reservaDelHuesped(String codigo, long huespedId) {
        return jdbc.query("SELECT id, estado, habitacion_id FROM reservas WHERE codigo = ? AND huesped_id = ?",
                (rs, i) -> new Reserva(rs.getLong(1), rs.getString(2), rs.getLong(3)), codigo, huespedId)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }

    private static Reserva enEstadia(Reserva r) {
        if (!"EN_ESTADIA".equals(r.estado())) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "Las solicitudes se hacen durante tu estadía.");
        }
        return r;
    }

    /** Bloquea la solicitud; el nombre de quien la tiene se lee después (ver LimpiezaService#bloquear). */
    private Bloqueada bloquear(long id) {
        return jdbc.query("SELECT id, reserva_id, estado, empleado_id FROM solicitudes WHERE id = ? FOR UPDATE",
                (rs, i) -> new Bloqueada(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4)), id)
                .stream().findFirst().orElseThrow(ApiException::noEncontrado);
    }

    private String nombreEmpleado(long id) {
        return jdbc.queryForObject("SELECT nombre_completo FROM empleados WHERE id = ?", String.class, id);
    }

    private Solicitudes.ParaLimpieza una(long id) {
        return conArticulos(jdbc.query(SELECT + " WHERE s.id = ?", (rs, i) -> fila(rs), id)).getFirst();
    }

    private Solicitudes.ParaLimpieza fila(ResultSet rs) throws SQLException {
        return new Solicitudes.ParaLimpieza(rs.getLong(1), rs.getString(2), fecha(rs.getTimestamp(3)),
                rs.getString(4), rs.getString(5), List.of(),
                new HabitacionReferencia(rs.getLong(6), rs.getString(7), rs.getInt(8)),
                rs.getObject(9) == null ? null : new ResponsableEmpleado(rs.getLong(9), rs.getString(10)));
    }

    /** Completa los artículos de las solicitudes de tipo ARTICULOS con una sola consulta. */
    private List<Solicitudes.ParaLimpieza> conArticulos(List<Solicitudes.ParaLimpieza> filas) {
        if (filas.isEmpty()) {
            return filas;
        }
        Map<Long, List<Solicitudes.Articulo>> porSolicitud = new LinkedHashMap<>();
        jdbc.query("""
                SELECT i.solicitud_id, a.id, a.nombre, i.cantidad FROM solicitud_items i
                JOIN articulos a ON a.id = i.articulo_id
                WHERE i.solicitud_id = ANY (?) ORDER BY i.id""", rs -> {
            porSolicitud.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Solicitudes.Articulo(rs.getLong(2), rs.getString(3), rs.getInt(4)));
        }, (Object) filas.stream().map(Solicitudes.ParaLimpieza::id).toArray(Long[]::new));
        return filas.stream().map(s -> new Solicitudes.ParaLimpieza(s.id(), s.tipo(), s.creadaEn(), s.estado(),
                s.comentario(), porSolicitud.getOrDefault(s.id(), List.of()), s.habitacion(), s.empleadoACargo()))
                .toList();
    }

    private static Solicitudes.DelHuesped delHuesped(Solicitudes.ParaLimpieza s) {
        return new Solicitudes.DelHuesped(s.id(), s.tipo(), s.creadaEn(), s.estado(), s.comentario(), s.articulos());
    }

    private static String etiqueta(String estado) {
        return switch (estado) {
            case "EN_PROCESO" -> "en proceso";
            case "ATENDIDA" -> "atendida";
            default -> "cancelada";
        };
    }

    private OffsetDateTime fecha(Timestamp t) {
        return OffsetDateTime.ofInstant(t.toInstant(), reloj.getZone());
    }

    /** {@code habitacionId} es 0 si la reserva no tiene habitación (no ocurre en estadía). */
    private record Reserva(long id, String estado, long habitacionId) {
    }

    /** {@code aCargo} es 0 si nadie la tomó. */
    private record Bloqueada(long id, long reservaId, String estado, long aCargo) {
    }
}
