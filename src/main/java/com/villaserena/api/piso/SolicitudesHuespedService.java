package com.villaserena.api.piso;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Reglas compartidas por las rutas oficiales y los consumidores anteriores. */
@Service
public class SolicitudesHuespedService {
    public record Item(@Positive long articuloId, @Min(1) int cantidad) {}
    public record ArticuloSolicitud(long articuloId, String nombre, int cantidad) {}
    public record SolicitudHuesped(long id, String tipo, Instant creadaEn, String estado,
            String comentario, List<ArticuloSolicitud> articulos) {}

    private final JdbcTemplate jdbc;
    private final HistorialEstadoService historial;
    private final SolicitudesEventos eventos;

    public SolicitudesHuespedService(JdbcTemplate jdbc, HistorialEstadoService historial,
            SolicitudesEventos eventos) {
        this.jdbc = jdbc;
        this.historial = historial;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<SolicitudHuesped> listar(String codigo) {
        var reserva = reserva(codigo, false, false);
        return jdbc.queryForList("SELECT id FROM requests WHERE reservation_id=? ORDER BY created_at DESC,id DESC",
                Long.class, reserva.get("id")).stream().map(this::detalle).toList();
    }

    @Transactional
    public SolicitudHuesped limpieza(String codigo, String comentario) {
        var reserva = reserva(codigo, true, true);
        // El bloqueo de habitación también cubre dos reservas distintas sobre la misma habitación.
        jdbc.queryForObject("SELECT id FROM rooms WHERE id=? FOR UPDATE", Long.class, reserva.get("room_id"));
        if (jdbc.queryForObject("""
                SELECT count(*) FROM requests WHERE room_id=? AND type='LIMPIEZA'
                AND status IN ('PENDIENTE','EN_PROCESO')""", Integer.class, reserva.get("room_id")) > 0) {
            throw ApiException.conflicto("SOLICITUD_ACTIVA",
                    "Ya hay una solicitud de limpieza en curso. Consulta su estado antes de solicitar otra.");
        }
        return crear(reserva, "LIMPIEZA", comentario == null ? null : comentario.trim(), List.of());
    }

    @Transactional
    public SolicitudHuesped articulos(String codigo, List<Item> articulos, String comentario) {
        var reserva = reserva(codigo, true, true);
        var ids = new HashSet<Long>();
        for (var item : articulos) {
            var maximo = jdbc.queryForList("SELECT maximum_quantity FROM articles WHERE id=?",
                    Integer.class, item.articuloId());
            if (!ids.add(item.articuloId()) || maximo.isEmpty() || item.cantidad() > maximo.getFirst()) {
                throw ApiException.datosInvalidos("Revisa los artículos y sus cantidades máximas.", List.of());
            }
        }
        return crear(reserva, "ARTICULOS", comentario, articulos);
    }

    @Transactional
    public SolicitudHuesped cancelar(String codigo, long id) {
        if (id < 1) throw ApiException.datosInvalidos("Identificador de solicitud inválido.", List.of());
        var reserva = reserva(codigo, true, false);
        var estados = jdbc.queryForList("SELECT status FROM requests WHERE id=? AND reservation_id=? FOR UPDATE",
                String.class, id, reserva.get("id"));
        if (estados.isEmpty()) throw ApiException.noEncontrado();
        if (!"PENDIENTE".equals(estados.getFirst())) {
            throw ApiException.conflicto("SOLICITUD_CAMBIADA", "Solo puedes cancelar una solicitud pendiente.");
        }
        jdbc.update("UPDATE requests SET status='CANCELADA' WHERE id=?", id);
        historial.registrar(TipoEntidadHistorial.SOLICITUD, id, "PENDIENTE", "CANCELADA", Responsable.HUESPED, null);
        eventos.publicar(id);
        return detalle(id);
    }

    private Map<String, Object> reserva(String codigo, boolean bloquear, boolean enEstadia) {
        if (!codigo.matches("^VS-[A-Z0-9]{6}$")) {
            throw ApiException.datosInvalidos("Código de reserva inválido.", List.of());
        }
        boolean huesped = "HUESPED".equals(UsuarioActual.rol());
        var filas = jdbc.queryForList("SELECT id,room_id,status FROM reservations WHERE code=?"
                + (huesped ? " AND guest_id=?" : "") + (bloquear ? " FOR UPDATE" : ""),
                huesped ? new Object[] {codigo, UsuarioActual.id()} : new Object[] {codigo});
        if (filas.isEmpty()) throw ApiException.noEncontrado();
        var reserva = filas.getFirst();
        if (enEstadia && (!"EN_ESTADIA".equals(reserva.get("status")) || reserva.get("room_id") == null)) {
            throw ApiException.conflicto("ESTADIA_REQUERIDA", "La reserva debe estar en estadía y tener habitación.");
        }
        return reserva;
    }

    private SolicitudHuesped crear(Map<String, Object> reserva, String tipo, String comentario, List<Item> articulos) {
        long id = jdbc.queryForObject("""
                INSERT INTO requests(reservation_id,room_id,type,comment) VALUES (?,?,?,?) RETURNING id""",
                Long.class, reserva.get("id"), reserva.get("room_id"), tipo, comentario);
        for (var item : articulos) {
            jdbc.update("INSERT INTO request_items(request_id,article_id,quantity) VALUES(?,?,?)",
                    id, item.articuloId(), item.cantidad());
        }
        var responsable = "HUESPED".equals(UsuarioActual.rol())
                ? Responsable.HUESPED : Responsable.empleado(UsuarioActual.id());
        historial.registrar(TipoEntidadHistorial.SOLICITUD, id, (String) null, "PENDIENTE", responsable, null);
        eventos.publicar(id);
        return detalle(id);
    }

    private SolicitudHuesped detalle(long id) {
        var articulos = jdbc.query("""
                SELECT a.id,a.name,i.quantity FROM request_items i JOIN articles a ON a.id=i.article_id
                WHERE i.request_id=? ORDER BY i.id""",
                (r, n) -> new ArticuloSolicitud(r.getLong("id"), r.getString("name"), r.getInt("quantity")), id);
        return jdbc.queryForObject("SELECT id,type,created_at,status,comment FROM requests WHERE id=?",
                (r, n) -> new SolicitudHuesped(r.getLong("id"), r.getString("type"),
                        r.getTimestamp("created_at").toInstant(), r.getString("status"), r.getString("comment"), articulos), id);
    }
}
