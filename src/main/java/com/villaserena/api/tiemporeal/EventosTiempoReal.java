package com.villaserena.api.tiemporeal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.notificaciones.PedidoEventos;
import com.villaserena.api.roomservice.PedidoService;
import com.villaserena.api.tiemporeal.dto.EventoTiempoReal;
import com.villaserena.api.tiemporeal.dto.HabitacionEvento;

/**
 * Publica los cuatro eventos del documento 07, sección 12. Quien cambia algo llama a
 * estos métodos y la publicación ya ocurre después del commit (ver
 * {@code TrasCommit} en quien llama), así que nunca se anuncia algo que se deshizo.
 * <p>
 * Un fallo al publicar no debe deshacer nada: si el agente de mensajes falla, el
 * pedido ya está guardado y las pantallas se enteran al recargar. Por eso cada
 * publicación va en su propio try.
 * <p>
 * Cada método abre su propia transacción ({@code REQUIRES_NEW}) porque corre
 * justo después del commit del cambio: a esa altura la transacción original ya
 * terminó, y unirse a ella fallaría con "No active transaction".
 */
@Service
public class EventosTiempoReal implements PedidoEventos, HabitacionEventos {

    private static final Logger log = LoggerFactory.getLogger(EventosTiempoReal.class);

    private final SimpMessagingTemplate mensajes;
    private final PedidoService pedidos;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public EventosTiempoReal(SimpMessagingTemplate mensajes, PedidoService pedidos, JdbcTemplate jdbc, Clock reloj) {
        this.mensajes = mensajes;
        this.pedidos = pedidos;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Evento 1: pedido nuevo. Va a Room Service (HU-RS-07). */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void pedidoNuevo(long pedidoId) {
        publicar(Destinos.PEDIDOS, EventoTiempoReal.NUEVO_PEDIDO, pedidos.detalle(pedidoId));
    }

    /**
     * Evento 2: cambio de estado de un pedido. Va a Room Service con todos los datos
     * y al huésped dueño con su vista reducida, nunca con el nombre ni el historial.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void pedidoActualizado(long pedidoId) {
        publicar(Destinos.PEDIDOS, EventoTiempoReal.PEDIDO_ACTUALIZADO, pedidos.detalle(pedidoId));
        pedidos.paraSuDueno(pedidoId).ifPresent(dueno -> publicarA(String.valueOf(dueno.huespedId()),
                Destinos.PEDIDOS_HUESPED, EventoTiempoReal.PEDIDO_ACTUALIZADO, dueno.pedido()));
    }

    /**
     * Evento 3: nueva solicitud de limpieza o artículos. Lo llamará OBJ-3B-2 cuando
     * el huésped cree una solicitud; el destino y el formato ya están listos.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void nuevaSolicitud(Object solicitud) {
        publicar(Destinos.SOLICITUDES, EventoTiempoReal.NUEVA_SOLICITUD, solicitud);
    }

    /** Evento 4: cambio de estado de una habitación (HU-REC-10, HU-MYL-01). */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void habitacionCambio(long habitacionId) {
        LocalDate hoy = LocalDate.now(reloj);
        jdbc.query("""
                SELECT h.id, h.numero, h.piso, h.ocupacion, h.condicion,
                       EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                               AND r.fecha_entrada = ? AND r.estado = 'CONFIRMADA') AS llega_hoy,
                       EXISTS (SELECT 1 FROM reservas r WHERE r.habitacion_id = h.id
                               AND r.fecha_salida = ? AND r.estado = 'EN_ESTADIA') AS sale_hoy,
                       EXISTS (SELECT 1 FROM incidencias i WHERE i.habitacion_id = h.id
                               AND i.estado <> 'RESUELTA') AS incidencia
                FROM habitaciones h WHERE h.id = ?""",
                (rs, i) -> new HabitacionEvento(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8)),
                hoy, hoy, habitacionId).stream().findFirst()
                .ifPresent(evento -> publicar(Destinos.HABITACIONES, EventoTiempoReal.HABITACION_ACTUALIZADA,
                        evento));
    }

    private void publicar(String destino, String tipo, Object datos) {
        try {
            mensajes.convertAndSend(destino, sobre(tipo, datos));
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar {} en {}: {}", tipo, destino, e.getMessage());
        }
    }

    private void publicarA(String usuario, String destino, String tipo, Object datos) {
        try {
            mensajes.convertAndSendToUser(usuario, destino, sobre(tipo, datos));
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar {} a {}: {}", tipo, usuario, e.getMessage());
        }
    }

    private EventoTiempoReal sobre(String tipo, Object datos) {
        return new EventoTiempoReal(tipo, OffsetDateTime.ofInstant(reloj.instant(), reloj.getZone()), datos);
    }
}
