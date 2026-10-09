package com.villaserena.api.roomservice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.HistorialConsulta;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.comun.TrasCommit;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.PedidoEventos;
import com.villaserena.api.notificaciones.TipoNotificacion;
import com.villaserena.api.reservas.CargoService;
import com.villaserena.api.reservas.Cuenta;
import com.villaserena.api.reservas.CuentaRepository;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.NuevoCargo;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.roomservice.dto.HabitacionOperativa;
import com.villaserena.api.roomservice.dto.ItemPedido;
import com.villaserena.api.roomservice.dto.LineaPedidoPeticion;
import com.villaserena.api.roomservice.dto.NuevoPedidoPeticion;
import com.villaserena.api.roomservice.dto.PedidoHuesped;
import com.villaserena.api.roomservice.dto.PedidoRoomService;

/**
 * Pedidos de Room Service (HU-HUE-10, HU-RS-01 a HU-RS-06).
 * <ul>
 * <li>El huésped pide solo con su reserva EN_ESTADIA; una reserva ajena responde 404
 * para no revelar que existe.</li>
 * <li>Los precios se congelan al pedir (RN-RS-006).</li>
 * <li>El avance es en orden y bajo bloqueo: si otro empleado ya lo cambió, 409 con
 * el estado actual (RN-RS-004).</li>
 * <li>El cargo se genera solo al ENTREGADO, una vez por pedido (RN-RS-007).</li>
 * </ul>
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidos;
    private final PedidoItemRepository lineas;
    private final ItemMenuRepository itemsMenu;
    private final ReservaRepository reservas;
    private final CuentaRepository cuentas;
    private final CargoService cargos;
    private final HistorialEstadoService historial;
    private final HistorialConsulta historialConsulta;
    private final OutboxService outbox;
    private final ObjectProvider<PedidoEventos> eventos;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public PedidoService(PedidoRepository pedidos, PedidoItemRepository lineas, ItemMenuRepository itemsMenu,
            ReservaRepository reservas, CuentaRepository cuentas, CargoService cargos,
            HistorialEstadoService historial, HistorialConsulta historialConsulta, OutboxService outbox,
            ObjectProvider<PedidoEventos> eventos, JdbcTemplate jdbc, Clock reloj) {
        this.pedidos = pedidos;
        this.lineas = lineas;
        this.itemsMenu = itemsMenu;
        this.reservas = reservas;
        this.cuentas = cuentas;
        this.cargos = cargos;
        this.historial = historial;
        this.historialConsulta = historialConsulta;
        this.outbox = outbox;
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Huésped (app)
    // ------------------------------------------------------------------

    /**
     * Crea un pedido del huésped (HU-HUE-10). Exige reserva EN_ESTADIA propia y
     * cuenta abierta. Si algún ítem está agotado o inactivo, no crea nada y dice
     * cuáles quitar.
     */
    @Transactional
    public PedidoHuesped crear(String codigo, long huespedId, NuevoPedidoPeticion peticion) {
        Reserva reserva = reservaDelHuesped(codigo, huespedId);
        if (reserva.getEstado() != EstadoReserva.EN_ESTADIA) {
            throw ApiException.conflicto("RESERVA_NO_EN_ESTADIA",
                    "Solo se puede pedir durante la estadía, después del check-in.");
        }
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        if (!cuenta.estaAbierta()) {
            throw ApiException.conflicto("CUENTA_NO_ABIERTA", "La cuenta de la estadía ya está cerrada.");
        }

        Map<Long, ItemMenu> menu = itemsMenu.findByIdIn(peticion.items().stream().map(LineaPedidoPeticion::itemId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(ItemMenu::getId, Function.identity()));
        List<DetalleError> problemas = new ArrayList<>();
        for (LineaPedidoPeticion linea : peticion.items()) {
            ItemMenu item = menu.get(linea.itemId());
            if (item == null || !item.estaActivo()) {
                problemas.add(new DetalleError("items", "El ítem " + linea.itemId() + " ya no está en el menú."));
            } else if (item.getDisponibilidad() == DisponibilidadMenu.AGOTADO) {
                problemas.add(new DetalleError("items", item.getNombre() + " está agotado; quítalo del pedido."));
            }
        }
        if (!problemas.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ITEMS_NO_DISPONIBLES",
                    "Algunos ítems ya no se pueden pedir.", problemas);
        }

        Pedido pedido = new Pedido();
        pedido.setReservaId(reserva.getId());
        pedido.setEstado(EstadoPedido.NUEVO);
        pedido.setNotas(peticion.notas() == null || peticion.notas().isBlank() ? null : peticion.notas().trim());
        pedido.setCreadoEn(reloj.instant());
        pedidos.saveAndFlush(pedido);

        for (LineaPedidoPeticion linea : peticion.items()) {
            ItemMenu item = menu.get(linea.itemId());
            PedidoItem fila = new PedidoItem();
            fila.setPedidoId(pedido.getId());
            fila.setItemMenuId(item.getId());
            fila.setCantidad(linea.cantidad());
            // Precio congelado: si el menú cambia después, este pedido no cambia.
            fila.setPrecioUnitario(item.getPrecio());
            lineas.save(fila);
        }

        historial.registrar(TipoEntidadHistorial.PEDIDO, pedido.getId(), null, EstadoPedido.NUEVO,
                Responsable.HUESPED, null);
        long pedidoId = pedido.getId();
        TrasCommit.ejecutar(() -> eventos.ifAvailable(e -> e.pedidoNuevo(pedidoId)));
        return paraHuesped(pedido, reserva.getCodigo());
    }

    /** Los pedidos de su reserva, el más reciente primero (HU-HUE-11). */
    @Transactional(readOnly = true)
    public List<PedidoHuesped> mios(String codigo, long huespedId) {
        Reserva reserva = reservaDelHuesped(codigo, huespedId);
        return pedidos.findByReservaIdOrderByCreadoEnDescIdDesc(reserva.getId()).stream()
                .map(p -> paraHuesped(p, reserva.getCodigo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoHuesped mio(String codigo, long huespedId, long pedidoId) {
        Reserva reserva = reservaDelHuesped(codigo, huespedId);
        Pedido pedido = pedidos.findById(pedidoId)
                .filter(p -> p.getReservaId().equals(reserva.getId()))
                .orElseThrow(ApiException::noEncontrado);
        return paraHuesped(pedido, reserva.getCodigo());
    }

    // ------------------------------------------------------------------
    // Room Service
    // ------------------------------------------------------------------

    /** Cola de pedidos activos, el más antiguo primero (HU-RS-01). */
    @Transactional(readOnly = true)
    public List<PedidoRoomService> cola() {
        return pedidos.enEstados(EstadoPedido.ACTIVOS).stream().map(this::paraRoomService).toList();
    }

    @Transactional(readOnly = true)
    public PedidoRoomService detalle(long pedidoId) {
        return paraRoomService(pedidos.findById(pedidoId).orElseThrow(ApiException::noEncontrado));
    }

    /**
     * Avanza un pedido al estado siguiente (HU-RS-03). Compara el estado que el
     * empleado tenía en pantalla con el de la base, bajo bloqueo: si otro ya lo
     * movió, responde 409 y dice el estado actual. Al ENTREGADO genera el cargo y
     * encola la push.
     */
    @Transactional
    public PedidoRoomService avanzar(long pedidoId, EstadoPedido estadoEsperado, EstadoPedido nuevoEstado,
            long empleadoId) {
        Pedido pedido = pedidos.bloquear(pedidoId).orElseThrow(ApiException::noEncontrado);
        if (pedido.getEstado() != estadoEsperado) {
            throw ApiException.conflicto("ESTADO_DESACTUALIZADO",
                    "El pedido ya está en " + pedido.getEstado() + "; recarga la cola.");
        }
        EstadoPedido siguiente = pedido.getEstado().siguiente();
        if (siguiente == null) {
            throw ApiException.conflicto("ESTADO_FINAL", "El pedido ya está en un estado final y no se modifica.");
        }
        if (siguiente != nuevoEstado) {
            throw ApiException.conflicto("TRANSICION_INVALIDA",
                    "Desde " + pedido.getEstado() + " solo se puede pasar a " + siguiente + ".");
        }

        Responsable responsable = Responsable.empleado(empleadoId);
        EstadoPedido anterior = pedido.getEstado();
        pedido.setEstado(nuevoEstado);
        pedidos.saveAndFlush(pedido);
        historial.registrar(TipoEntidadHistorial.PEDIDO, pedido.getId(), anterior, nuevoEstado, responsable, null);

        if (nuevoEstado == EstadoPedido.ENTREGADO) {
            cobrar(pedido);
            encolarPush(pedido);
        }
        TrasCommit.ejecutar(() -> eventos.ifAvailable(e -> e.pedidoActualizado(pedidoId)));
        return paraRoomService(pedido);
    }

    /** Cancela un pedido activo con motivo obligatorio (HU-RS-04). No genera cargo. */
    @Transactional
    public PedidoRoomService cancelar(long pedidoId, String motivo, Responsable responsable) {
        Pedido pedido = pedidos.bloquear(pedidoId).orElseThrow(ApiException::noEncontrado);
        if (!pedido.getEstado().esActivo()) {
            throw ApiException.conflicto("ESTADO_FINAL",
                    "El pedido ya está en " + pedido.getEstado() + " y no se puede cancelar.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.datosInvalidos("El motivo de la cancelación es obligatorio.",
                    List.of(new DetalleError("motivo", "Escribe el motivo.")));
        }
        EstadoPedido anterior = pedido.getEstado();
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidos.saveAndFlush(pedido);
        historial.registrar(TipoEntidadHistorial.PEDIDO, pedido.getId(), anterior, EstadoPedido.CANCELADO,
                responsable, motivo.trim());
        TrasCommit.ejecutar(() -> eventos.ifAvailable(e -> e.pedidoActualizado(pedidoId)));
        return paraRoomService(pedido);
    }

    /** Pedidos activos de una reserva: el check-out los tiene que resolver (RN-RS-009). */
    @Transactional(readOnly = true)
    public List<PedidoRoomService> activosDeReserva(long reservaId) {
        return pedidos.activosDeReserva(reservaId, EstadoPedido.ACTIVOS).stream()
                .map(this::paraRoomService)
                .toList();
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    /**
     * Un solo cargo por pedido (RN-RS-007). La tabla tiene un índice único por
     * pedido; si la petición se repite y el cargo ya existe, se toma como hecho.
     */
    private void cobrar(Pedido pedido) {
        Cuenta cuenta = cuentas.findByReservaId(pedido.getReservaId()).orElseThrow(ApiException::noEncontrado);
        BigDecimal total = total(pedido.getId());
        try {
            cargos.registrarCargo(cuenta.getId(),
                    NuevoCargo.roomService(pedido.getId(), "Room Service — Pedido #" + pedido.getId(), total),
                    Responsable.SISTEMA);
        } catch (DataIntegrityViolationException e) {
            log.info("El pedido {} ya tenía su cargo; no se duplica", pedido.getId());
        }
    }

    /**
     * Encola la push "pedido entregado", una por dispositivo del huésped
     * (RN-NOT-001). Sin nombres ni montos (RN-NOT-004). El envío es de OBJ-3A-2.
     */
    private void encolarPush(Pedido pedido) {
        Reserva reserva = reservas.findById(pedido.getReservaId()).orElseThrow();
        List<String> tokens = jdbc.queryForList(
                "SELECT token_expo FROM dispositivos_push WHERE huesped_id = ?", String.class,
                reserva.getHuespedId());
        for (String token : tokens) {
            outbox.encolar(TipoNotificacion.PUSH_PEDIDO_ENTREGADO, token,
                    Map.of("pantalla", "pedidos", "codigoReserva", reserva.getCodigo(), "pedidoId", pedido.getId()));
        }
    }

    private Reserva reservaDelHuesped(String codigo, long huespedId) {
        return reservas.findByCodigo(codigo)
                .filter(r -> r.getHuespedId() == huespedId)
                .orElseThrow(ApiException::noEncontrado);
    }

    private List<ItemPedido> items(long pedidoId) {
        return jdbc.query("""
                SELECT pi.item_menu_id, im.nombre, pi.cantidad, pi.precio_unitario
                FROM pedido_items pi JOIN items_menu im ON im.id = pi.item_menu_id
                WHERE pi.pedido_id = ? ORDER BY pi.id""",
                (rs, i) -> linea(rs), pedidoId);
    }

    private static ItemPedido linea(ResultSet rs) throws SQLException {
        int cantidad = rs.getInt(3);
        BigDecimal precio = rs.getBigDecimal(4);
        return new ItemPedido(rs.getLong(1), rs.getString(2), cantidad, precio,
                precio.multiply(BigDecimal.valueOf(cantidad)));
    }

    private BigDecimal total(long pedidoId) {
        return items(pedidoId).stream().map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** El motivo de cancelación vive en el historial, no en la tabla de pedidos. */
    private String motivoCancelacion(long pedidoId) {
        return historialConsulta.de(TipoEntidadHistorial.PEDIDO, pedidoId).stream()
                .filter(h -> EstadoPedido.CANCELADO.name().equals(h.estadoNuevo()))
                .map(com.villaserena.api.comun.HistorialOperacionVista::motivo)
                .reduce((primero, ultimo) -> ultimo)
                .orElse(null);
    }

    private PedidoHuesped paraHuesped(Pedido pedido, String codigoReserva) {
        List<ItemPedido> items = items(pedido.getId());
        return new PedidoHuesped(pedido.getId(), codigoReserva,
                OffsetDateTime.ofInstant(pedido.getCreadoEn(), reloj.getZone()), pedido.getEstado(), items,
                pedido.getNotas(), items.stream().map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                motivoCancelacion(pedido.getId()));
    }

    private PedidoRoomService paraRoomService(Pedido pedido) {
        List<ItemPedido> items = items(pedido.getId());
        Datos datos = jdbc.query("""
                SELECT hu.nombre_completo, ha.id, ha.numero, ha.piso
                FROM reservas r
                JOIN huespedes hu ON hu.id = r.huesped_id
                LEFT JOIN habitaciones ha ON ha.id = r.habitacion_id
                WHERE r.id = ?""",
                (rs, i) -> new Datos(rs.getString(1), (Long) rs.getObject(2), rs.getString(3),
                        (Integer) rs.getObject(4)),
                pedido.getReservaId()).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        return new PedidoRoomService(pedido.getId(),
                OffsetDateTime.ofInstant(pedido.getCreadoEn(), reloj.getZone()), pedido.getEstado(), items,
                pedido.getNotas(), items.stream().map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                motivoCancelacion(pedido.getId()),
                new HabitacionOperativa(datos.habitacionId(), datos.numero(), datos.piso()),
                datos.nombreHuesped(), historialConsulta.de(TipoEntidadHistorial.PEDIDO, pedido.getId()));
    }

    private record Datos(String nombreHuesped, Long habitacionId, String numero, Integer piso) {
    }

    /** El pedido con la vista de su dueño y el id del huésped, para la cola privada del WebSocket. */
    public record PedidoDeSuDueno(long huespedId, PedidoHuesped pedido) {
    }

    @Transactional(readOnly = true)
    public Optional<PedidoDeSuDueno> paraSuDueno(long pedidoId) {
        return pedidos.findById(pedidoId)
                .flatMap(pedido -> reservas.findById(pedido.getReservaId())
                        .map(reserva -> new PedidoDeSuDueno(reserva.getHuespedId(),
                                paraHuesped(pedido, reserva.getCodigo()))));
    }
}
