package com.villaserena.api.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.RelojFijoConfig.RelojPrueba;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;
import com.villaserena.api.roomservice.PedidoService;
import com.villaserena.api.roomservice.EstadoPedido;
import com.villaserena.api.roomservice.dto.LineaPedidoPeticion;
import com.villaserena.api.roomservice.dto.NuevoPedidoPeticion;
import com.villaserena.api.tiemporeal.dto.WsTicket;

/**
 * Pruebas de OBJ-3A-1 parte B con un cliente STOMP de verdad: Room Service recibe
 * el pedido nuevo, el huésped solo recibe los cambios de sus pedidos, un rol sin
 * permiso no se suscribe y un ticket vencido o reutilizado no conecta.
 * <p>
 * No es transaccional: el servidor corre en otro hilo y tiene que ver los datos
 * confirmados, así que la prueba limpia lo suyo al final.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    private static final int ESPERA_SEGUNDOS = 5;

    @LocalServerPort
    int puerto;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    PedidoService pedidos;

    @Autowired
    WsTicketService tickets;

    @Autowired
    TokenService tokens;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    RelojPrueba reloj;

    @Autowired
    EventosTiempoReal eventosTiempoReal;

    @MockitoBean
    PasarelaStripe stripe;

    WebSocketStompClient cliente;
    String codigo;
    Long reservaId;
    Long huespedId;
    Long habitacionId;
    long itemCafe;
    long empleadoRoomService;
    long recepcionista;

    @BeforeEach
    void prepararEstadia() {
        cliente = new WebSocketStompClient(new StandardWebSocketClient());
        // SimpleMessageConverter entrega el cuerpo tal cual (byte[]): StringMessageConverter
        // solo acepta text/plain y descartaría los eventos, que viajan como JSON.
        cliente.setMessageConverter(new SimpleMessageConverter());

        reloj.reiniciar();
        recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        empleadoRoomService = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE rol = 'ROOM_SERVICE' LIMIT 1", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Ana Morales', 'ws.ana@correo.test', '+502 5555 1212', 'Guatemalteca', 'DPI', '7777777777777')
                RETURNING id""", Long.class);
        habitacionId = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '203'", Long.class);
        long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                habitacionId);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-08"), 1, habitacionId, recepcionista));
        codigo = reserva.getCodigo();
        reservaId = reserva.getId();
        checkinService.hacerCheckin(codigo, recepcionista);
        itemCafe = jdbc.queryForObject("SELECT id FROM items_menu WHERE nombre = 'Café de Antigua'", Long.class);
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private StompSession conectarConTicket(long empleadoId, String rol, String area) throws Exception {
        WsTicket ticket = tickets.emitir(empleadoId, rol, area);
        return conectar(cabeceras -> cabeceras.add("ticket", ticket.ticket()));
    }

    private StompSession conectarComoHuesped() throws Exception {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        String jwt;
        try {
            jwt = tokens.emitirParaHuesped(huespedId).accessToken();
        } finally {
            reloj.fijar(antes);
        }
        return conectar(cabeceras -> cabeceras.add("Authorization", "Bearer " + jwt));
    }

    private StompSession conectar(java.util.function.Consumer<StompHeaders> credenciales) throws Exception {
        StompHeaders cabeceras = new StompHeaders();
        credenciales.accept(cabeceras);
        return cliente.connectAsync("ws://localhost:" + puerto + "/ws",
                new org.springframework.web.socket.WebSocketHttpHeaders(), cabeceras,
                new StompSessionHandlerAdapter() {
                }).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> suscribir(StompSession sesion, String destino) {
        BlockingQueue<String> recibidos = new ArrayBlockingQueue<>(10);
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders cabeceras) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                recibidos.add(new String((byte[]) cuerpo, java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        return recibidos;
    }

    private long crearPedido() {
        return pedidos.crear(codigo, huespedId,
                new NuevoPedidoPeticion(java.util.List.of(new LineaPedidoPeticion(itemCafe, 2)), "Sin azúcar")).id();
    }

    // ------------------------------------------------------------------
    // Pruebas
    // ------------------------------------------------------------------

    @Test
    void roomServiceRecibeElPedidoNuevo() throws Exception {
        StompSession sesion = conectarConTicket(empleadoRoomService, "ROOM_SERVICE", null);
        BlockingQueue<String> recibidos = suscribir(sesion, Destinos.PEDIDOS);
        // El SUBSCRIBE viaja aparte del CONNECT; sin esto el evento puede salir antes.
        Thread.sleep(300);

        long pedidoId = crearPedido();

        String evento = recibidos.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        assertThat(evento).isNotNull();
        assertThat(evento)
                .contains("\"tipo\":\"NUEVO_PEDIDO\"")
                .contains("\"id\":" + pedidoId)
                .contains("\"numero\":\"203\"")
                .contains("Ana Morales");
        sesion.disconnect();
    }

    @Test
    void elHuespedSoloRecibeLosCambiosDeSusPedidos() throws Exception {
        long pedidoId = crearPedido();
        StompSession sesion = conectarComoHuesped();
        BlockingQueue<String> recibidos = suscribir(sesion, "/user" + Destinos.PEDIDOS_HUESPED);
        Thread.sleep(300);

        pedidos.avanzar(pedidoId, EstadoPedido.NUEVO, EstadoPedido.EN_PREPARACION, empleadoRoomService);

        String evento = recibidos.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        assertThat(evento).isNotNull();
        assertThat(evento)
                .contains("\"tipo\":\"PEDIDO_ACTUALIZADO\"")
                .contains("\"estado\":\"EN_PREPARACION\"")
                .contains("\"codigoReserva\":\"" + codigo + "\"")
                // La vista del huésped no lleva nombre, habitación ni historial.
                .doesNotContain("Ana Morales")
                .doesNotContain("historial");
        sesion.disconnect();
    }

    @Test
    void unRolSinPermisoNoSeSuscribe() throws Exception {
        // Recepción no tiene nada que hacer en la cola de Room Service.
        StompSession sesion = conectarConTicket(recepcionista, "RECEPCION", null);
        BlockingQueue<String> recibidos = suscribir(sesion, Destinos.PEDIDOS);
        Thread.sleep(300);

        crearPedido();

        assertThat(recibidos.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void recepcionSiRecibeLosCambiosDeHabitaciones() throws Exception {
        StompSession sesion = conectarConTicket(recepcionista, "RECEPCION", null);
        BlockingQueue<String> recibidos = suscribir(sesion, Destinos.HABITACIONES);
        Thread.sleep(300);

        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE id = ?", habitacionId);
        // El evento 4 lo dispara quien cambia la habitación (el check-in, el
        // check-out o la limpieza de OBJ-2C); aquí se llama directo.
        eventosTiempoReal.habitacionCambio(habitacionId);

        String evento = recibidos.poll(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        assertThat(evento).isNotNull();
        assertThat(evento)
                .contains("\"tipo\":\"HABITACION_ACTUALIZADA\"")
                .contains("\"numero\":\"203\"")
                .contains("\"condicion\":\"SUCIA\"")
                .contains("\"ocupacion\":\"OCUPADA\"");
        sesion.disconnect();
    }

    @Test
    void elTicketEsDeUnSoloUso() throws Exception {
        WsTicket ticket = tickets.emitir(empleadoRoomService, "ROOM_SERVICE", null);
        StompSession primera = conectar(c -> c.add("ticket", ticket.ticket()));
        assertThat(primera.isConnected()).isTrue();
        primera.disconnect();

        assertThatThrownBy(() -> conectar(c -> c.add("ticket", ticket.ticket())))
                .hasMessageContaining("Connection closed");
    }

    @Test
    void unTicketVencidoNoConecta() throws Exception {
        WsTicket ticket = tickets.emitir(empleadoRoomService, "ROOM_SERVICE", null);
        reloj.fijar(reloj.instant().plusSeconds(61));

        assertThatThrownBy(() -> conectar(c -> c.add("ticket", ticket.ticket())))
                .hasMessageContaining("Connection closed");
    }

    @Test
    void sinCredencialesNoConecta() {
        assertThatThrownBy(() -> conectar(c -> {
        })).hasMessageContaining("Connection closed");
    }

    @AfterEach
    void limpiar() {
        reloj.reiniciar();
        if (reservaId != null) {
            jdbc.update("""
                    DELETE FROM historial_estados WHERE tipo_entidad = 'PEDIDO' AND id_entidad IN
                        (SELECT id FROM pedidos WHERE reserva_id = ?)""", reservaId);
            jdbc.update("DELETE FROM outbox WHERE tipo = 'PUSH_PEDIDO_ENTREGADO'");
            jdbc.update("""
                    DELETE FROM cargos WHERE cuenta_id IN (SELECT id FROM cuentas WHERE reserva_id = ?)""", reservaId);
            jdbc.update("DELETE FROM pedido_items WHERE pedido_id IN (SELECT id FROM pedidos WHERE reserva_id = ?)",
                    reservaId);
            jdbc.update("DELETE FROM pedidos WHERE reserva_id = ?", reservaId);
            jdbc.update("DELETE FROM pagos WHERE cuenta_id IN (SELECT id FROM cuentas WHERE reserva_id = ?)",
                    reservaId);
            jdbc.update("DELETE FROM cuentas WHERE reserva_id = ?", reservaId);
            jdbc.update("DELETE FROM reserva_noches WHERE reserva_id = ?", reservaId);
            jdbc.update("DELETE FROM historial_estados WHERE tipo_entidad = 'RESERVA' AND id_entidad = ?", reservaId);
            jdbc.update("DELETE FROM reservas WHERE id = ?", reservaId);
        }
        if (habitacionId != null) {
            jdbc.update("DELETE FROM historial_estados WHERE tipo_entidad LIKE 'HABITACION%' AND id_entidad = ?",
                    habitacionId);
            jdbc.update("UPDATE habitaciones SET ocupacion = 'LIBRE', condicion = 'LIMPIA' WHERE id = ?",
                    habitacionId);
        }
        if (huespedId != null) {
            jdbc.update("DELETE FROM refresh_tokens WHERE huesped_id = ?", huespedId);
            jdbc.update("DELETE FROM huespedes WHERE id = ?", huespedId);
        }
    }
}
