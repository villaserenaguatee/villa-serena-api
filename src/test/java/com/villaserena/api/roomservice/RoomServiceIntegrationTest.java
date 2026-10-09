package com.villaserena.api.roomservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
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

/**
 * Pruebas de OBJ-3A-1 parte A: menú, creación del pedido, cola, avance en orden,
 * cancelación sin cargo y un solo cargo al entregar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class RoomServiceIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    TokenService tokens;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    String codigo;
    long huespedId;
    long reservaId;
    long cuentaId;
    long itemCafe;
    String huesped;
    String roomService;

    @BeforeEach
    void prepararEstadia() {
        long recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        long empleadoRs = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE rol = 'ROOM_SERVICE' LIMIT 1", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Ana Morales', 'ana.morales@correo.test', '+502 5555 7777', 'Guatemalteca', 'DPI',
                        '1234567890123') RETURNING id""", Long.class);
        long habitacion = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '201'", Long.class);
        long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                habitacion);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-08"), 2, habitacion, recepcionista));
        codigo = reserva.getCodigo();
        reservaId = reserva.getId();
        checkinService.hacerCheckin(codigo, recepcionista);
        cuentaId = jdbc.queryForObject("SELECT id FROM cuentas WHERE reserva_id = ?", Long.class, reservaId);
        itemCafe = jdbc.queryForObject("SELECT id FROM items_menu WHERE nombre = 'Café de Antigua'", Long.class);
        huesped = token(() -> tokens.emitirParaHuesped(huespedId).accessToken());
        roomService = token(() -> tokens.emitirParaEmpleado(empleados.findById(empleadoRs).orElseThrow())
                .accessToken());
    }

    /** El validador de JWT usa la hora real: el token se emite con ella. */
    private String token(java.util.function.Supplier<String> emisor) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + emisor.get();
        } finally {
            reloj.fijar(antes);
        }
    }

    private ResultActions pedir(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/app/reservas/{c}/pedidos", codigo).header("Authorization", huesped)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private long crearPedido() throws Exception {
        String respuesta = pedir("""
                {"items": [{"itemId": %d, "cantidad": 2}], "notas": "Sin azúcar"}""".formatted(itemCafe))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions avanzar(long pedidoId, String esperado, String nuevo) throws Exception {
        return mvc.perform(post("/api/v1/room-service/pedidos/{id}/avanzar", pedidoId)
                .header("Authorization", roomService)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estadoEsperado\": \"%s\", \"nuevoEstado\": \"%s\"}".formatted(esperado, nuevo)));
    }

    @Test
    void elMenuAgrupaPorCategoriaYMarcaAgotados() throws Exception {
        mvc.perform(get("/api/v1/room-service/menu").header("Authorization", roomService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[*].nombre")
                        .value(org.hamcrest.Matchers.hasItems("Desayunos", "Platos fuertes", "Bebidas")))
                .andExpect(jsonPath("$.items[?(@.nombre == 'Café de Antigua')].disponibilidad")
                        .value("DISPONIBLE"));

        mvc.perform(post("/api/v1/room-service/menu/items/{id}/agotar", itemCafe)
                        .header("Authorization", roomService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponibilidad").value("AGOTADO"));

        // Solo DISPONIBLE -> AGOTADO; repetirlo es conflicto y reactivar no es de este rol.
        mvc.perform(post("/api/v1/room-service/menu/items/{id}/agotar", itemCafe)
                        .header("Authorization", roomService))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEM_YA_AGOTADO"));
    }

    @Test
    void elPedidoAparaceEnLaColaConLaHabitacionYElNombre() throws Exception {
        long pedidoId = crearPedido();

        mvc.perform(get("/api/v1/room-service/pedidos").header("Authorization", roomService))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].estado".formatted(pedidoId)).value("NUEVO"))
                .andExpect(jsonPath("$[?(@.id == %d)].habitacion.numero".formatted(pedidoId)).value("201"))
                .andExpect(jsonPath("$[?(@.id == %d)].nombreHuesped".formatted(pedidoId)).value("Ana Morales"))
                // Café de Antigua 25.00 × 2.
                .andExpect(jsonPath("$[?(@.id == %d)].total".formatted(pedidoId)).value(50.00))
                .andExpect(jsonPath("$[?(@.id == %d)].items[0].subtotal".formatted(pedidoId)).value(50.00));
    }

    @Test
    void avanzarEnOrdenFuncionaYSaltarUnEstadoDa409() throws Exception {
        long pedidoId = crearPedido();

        // Saltar de NUEVO a EN_CAMINO no se permite.
        avanzar(pedidoId, "NUEVO", "EN_CAMINO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        avanzar(pedidoId, "NUEVO", "EN_PREPARACION").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));

        // Otro empleado con la pantalla vieja: su estadoEsperado ya no corresponde.
        avanzar(pedidoId, "NUEVO", "EN_PREPARACION")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_DESACTUALIZADO"));

        avanzar(pedidoId, "EN_PREPARACION", "EN_CAMINO").andExpect(status().isOk());
        avanzar(pedidoId, "EN_CAMINO", "ENTREGADO").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADO"));

        // Entregado ya no se modifica, y sale de la cola.
        avanzar(pedidoId, "EN_CAMINO", "ENTREGADO").andExpect(status().isConflict());
        mvc.perform(get("/api/v1/room-service/pedidos").header("Authorization", roomService))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(pedidoId)).isEmpty());
    }

    @Test
    void alEntregarHayUnSoloCargoAunqueSeRepitaLaPeticion() throws Exception {
        long pedidoId = crearPedido();
        avanzar(pedidoId, "NUEVO", "EN_PREPARACION").andExpect(status().isOk());
        avanzar(pedidoId, "EN_PREPARACION", "EN_CAMINO").andExpect(status().isOk());
        avanzar(pedidoId, "EN_CAMINO", "ENTREGADO").andExpect(status().isOk());

        // Repetir la entrega no agrega otro cargo (RN-RS-007).
        avanzar(pedidoId, "EN_CAMINO", "ENTREGADO").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cargos WHERE pedido_id = ?", Integer.class, pedidoId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT tipo || ':' || concepto || ':' || total FROM cargos WHERE pedido_id = ?", String.class,
                pedidoId)).isEqualTo("ROOM_SERVICE:Room Service — Pedido #" + pedidoId + ":50.00");
    }

    @Test
    void cancelarPideMotivoYNoGeneraCargo() throws Exception {
        long pedidoId = crearPedido();

        mvc.perform(post("/api/v1/room-service/pedidos/{id}/cancelar", pedidoId)
                        .header("Authorization", roomService)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/room-service/pedidos/{id}/cancelar", pedidoId)
                        .header("Authorization", roomService)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Producto no disponible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Producto no disponible"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cargos WHERE pedido_id = ?", Integer.class, pedidoId))
                .isZero();
        // Un pedido cancelado ya no se avanza.
        avanzar(pedidoId, "NUEVO", "EN_PREPARACION").andExpect(status().isConflict());
    }

    @Test
    void noSePuedePedirUnItemAgotado() throws Exception {
        jdbc.update("UPDATE items_menu SET disponibilidad = 'AGOTADO' WHERE id = ?", itemCafe);

        pedir("""
                {"items": [{"itemId": %d, "cantidad": 1}]}""".formatted(itemCafe))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEMS_NO_DISPONIBLES"))
                .andExpect(jsonPath("$.detalles[0].mensaje")
                        .value(org.hamcrest.Matchers.containsString("Café de Antigua")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedidos WHERE reserva_id = ?", Integer.class, reservaId))
                .isZero();
    }

    @Test
    void soloSePuedePedirDuranteLaEstadiaYSoloEnLaReservaPropia() throws Exception {
        // Reserva de otro huésped: responde 404, no 403, para no revelar que existe.
        long otro = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Otro Huésped', 'otro@correo.test', '+502 0', 'Guatemalteca', 'DPI', '9')
                RETURNING id""", Long.class);
        String ajeno = token(() -> tokens.emitirParaHuesped(otro).accessToken());
        mvc.perform(post("/api/v1/app/reservas/{c}/pedidos", codigo).header("Authorization", ajeno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"itemId\": %d, \"cantidad\": 1}]}".formatted(itemCafe)))
                .andExpect(status().isNotFound());

        // Sin check-in (CONFIRMADA) tampoco se puede pedir.
        long recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        Reserva sinCheckin = reservaService.crear(SolicitudReserva.recepcion(otro, 1L,
                LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-22"), 1, null, recepcionista));
        mvc.perform(post("/api/v1/app/reservas/{c}/pedidos", sinCheckin.getCodigo())
                        .header("Authorization", ajeno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"itemId\": %d, \"cantidad\": 1}]}".formatted(itemCafe)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RESERVA_NO_EN_ESTADIA"));
    }

    @Test
    void elHuespedVeSusPedidosSinDatosDeOtros() throws Exception {
        long pedidoId = crearPedido();

        mvc.perform(get("/api/v1/app/reservas/{c}/pedidos", codigo).header("Authorization", huesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pedidoId))
                .andExpect(jsonPath("$[0].codigoReserva").value(codigo))
                .andExpect(jsonPath("$[0].notas").value("Sin azúcar"))
                // El DTO del huésped no lleva habitación, nombre ni historial.
                .andExpect(jsonPath("$[0].nombreHuesped").doesNotExist())
                .andExpect(jsonPath("$[0].historial").doesNotExist());
    }

    @Test
    void laColaYElMenuSonSoloDeRoomService() throws Exception {
        mvc.perform(get("/api/v1/room-service/pedidos").header("Authorization", huesped))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/room-service/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alEntregarSeEncolaLaPushSinDatosPersonales() throws Exception {
        jdbc.update("INSERT INTO dispositivos_push (huesped_id, token_expo) VALUES (?, ?)",
                huespedId, "ExponentPushToken[pruebas]");
        long pedidoId = crearPedido();
        avanzar(pedidoId, "NUEVO", "EN_PREPARACION").andExpect(status().isOk());
        avanzar(pedidoId, "EN_PREPARACION", "EN_CAMINO").andExpect(status().isOk());
        avanzar(pedidoId, "EN_CAMINO", "ENTREGADO").andExpect(status().isOk());

        String payload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox
                WHERE tipo = 'PUSH_PEDIDO_ENTREGADO' AND destinatario = 'ExponentPushToken[pruebas]'""",
                String.class);
        assertThat(payload).contains("\"pedidoId\": " + pedidoId).contains(codigo)
                .doesNotContain("Ana Morales").doesNotContain("50.00");
    }
}
