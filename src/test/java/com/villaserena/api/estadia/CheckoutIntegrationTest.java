package com.villaserena.api.estadia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.facturacion.EmisorFactura;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/** Pruebas de OBJ-4A: cuenta, cargos, check-out de Recepción y de la app, y pago del saldo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class CheckoutIntegrationTest {

    /** Día de salida, 10:00 en Guatemala: dentro de la ventana de la app. */
    static final Instant SALIDA_10AM = Instant.parse("2026-10-07T16:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    @MockitoBean
    EmisorFactura emisorFactura;

    String recepcion;
    long recepcionistaId;
    long huespedId;
    long habitacionId;
    String codigo;

    @BeforeEach
    void preparar() {
        recepcionistaId = empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow().getId();
        recepcion = tokenEmpleado();
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Carla Salida', 'carla@correo.test', '+502 5555 4444', 'Guatemalteca', 'DPI', '4444444440101')
                RETURNING id""", Long.class);
        habitacionId = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '101'", Long.class);
        long tipo = jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = 'Estándar Jardín'", Long.class);
        // Lunes 5 a miércoles 7 de octubre: 650 + 650 = 1300.00.
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo, LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-07"), 1, habitacionId, recepcionistaId));
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionistaId);
        codigo = reserva.getCodigo();
        when(emisorFactura.emitir(anyLong(), anyString(), anyString()))
                .thenReturn(new EmisorFactura.FacturaEmitida(1, Map.of("serie", "VS-A", "numero", 1)));
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // -------------------------------------------------------------- cuenta

    @Test
    void saldoNoCuentaLosCargosAnulados() throws Exception {
        agregarCargo("Lavandería", 2, "100.00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(200.00))
                .andExpect(jsonPath("$.responsable").value("Ana Pérez"));
        String error = JsonPath.read(agregarCargo("Minibar por error", 1, "50.00")
                .andReturn().getResponse().getContentAsString(), "$.id").toString();

        mvc.perform(post("/api/v1/cuentas/{c}/cargos/{id}/anular", codigo, error).header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Registrado por error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.anuladoPor").value("Ana Pérez"));

        mvc.perform(get("/api/v1/cuentas/{c}", codigo).header("Authorization", recepcion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCargosVigentes").value(1500.00))
                .andExpect(jsonPath("$.saldo").value(1500.00))
                .andExpect(jsonPath("$.detalleNoches.length()").value(2))
                .andExpect(jsonPath("$.cargos.length()").value(3));
    }

    @Test
    void elAlojamientoNoSeAnula() throws Exception {
        Long alojamiento = jdbc.queryForObject("""
                SELECT c.id FROM cargos c JOIN cuentas cu ON cu.id = c.cuenta_id
                JOIN reservas r ON r.id = cu.reserva_id WHERE r.codigo = ? AND c.tipo = 'ALOJAMIENTO'""",
                Long.class, codigo);
        mvc.perform(post("/api/v1/cuentas/{c}/cargos/{id}/anular", codigo, alojamiento)
                        .header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CARGO_NO_ANULABLE"));
    }

    @Test
    void elHuespedSoloVeSuCuenta() throws Exception {
        String huesped = tokenHuesped(huespedId);
        mvc.perform(get("/api/v1/app/reservas/{c}/cuenta", codigo).header("Authorization", huesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(1300.00))
                .andExpect(jsonPath("$.cargos[0].responsable").doesNotExist());

        long otro = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Otro', 'otro@correo.test', '+502 1', 'Guatemalteca', 'DPI', '1') RETURNING id""", Long.class);
        mvc.perform(get("/api/v1/app/reservas/{c}/cuenta", codigo).header("Authorization", tokenHuesped(otro)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------- check-out Recepción

    @Test
    void checkoutDeRecepcionConEfectivo() throws Exception {
        long pedido = jdbc.queryForObject(
                "INSERT INTO pedidos (reserva_id, estado) SELECT id, 'NUEVO' FROM reservas WHERE codigo = ? RETURNING id",
                Long.class, codigo);
        jdbc.update("INSERT INTO dispositivos_push (huesped_id, token_expo) VALUES (?, 'ExponentPushToken[x]')",
                huespedId);

        checkoutRecepcion("CF", "{\"metodo\": \"EFECTIVO\", \"referencia\": \"Caja 1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("FINALIZADA"))
                .andExpect(jsonPath("$.estadoCuenta").value("CERRADA"))
                .andExpect(jsonPath("$.saldo").value(0.00))
                .andExpect(jsonPath("$.factura.serie").value("VS-A"));

        assertThat(jdbc.queryForMap("SELECT ocupacion, condicion FROM habitaciones WHERE id = ?", habitacionId))
                .containsEntry("ocupacion", "LIBRE").containsEntry("condicion", "SUCIA");
        assertThat(jdbc.queryForMap("""
                SELECT p.metodo, p.monto, p.referencia FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id
                JOIN reservas r ON r.id = c.reserva_id WHERE r.codigo = ?""", codigo))
                .containsEntry("metodo", "EFECTIVO").containsEntry("referencia", "Caja 1")
                .satisfies(m -> assertThat((BigDecimal) m.get("monto")).isEqualByComparingTo("1300.00"));
        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?", String.class, pedido))
                .isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivos_push WHERE huesped_id = ?", Integer.class,
                huespedId)).isZero();
    }

    @Test
    void conIncidenciaQueImpideElUsoQuedaFueraDeServicio() throws Exception {
        long empleado = empleados.findByCorreoIgnoreCase("marta.xicara@villaserena.test").orElseThrow().getId();
        jdbc.update("""
                INSERT INTO incidencias (habitacion_id, descripcion, impide_uso, reportada_por_empleado_id)
                VALUES (?, 'Fuga en el baño', TRUE, ?)""", habitacionId, empleado);
        checkoutRecepcion("CF", "{\"metodo\": \"TARJETA\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT condicion FROM habitaciones WHERE id = ?", String.class, habitacionId))
                .isEqualTo("FUERA_DE_SERVICIO");
    }

    @Test
    void noSeHaceCheckoutConUnPedidoEnCamino() throws Exception {
        jdbc.update("INSERT INTO pedidos (reserva_id, estado) SELECT id, 'EN_CAMINO' FROM reservas WHERE codigo = ?",
                codigo);
        mvc.perform(get("/api/v1/checkout/{c}", codigo).header("Authorization", recepcion))
                .andExpect(jsonPath("$.pedidoEnCamino").value(true))
                .andExpect(jsonPath("$.puedeConfirmar").value(false));
        checkoutRecepcion("CF", "{\"metodo\": \"EFECTIVO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_EN_CAMINO"));
    }

    @Test
    void nitInvalidoYPagoObligatorio() throws Exception {
        checkoutRecepcion("4817205-8", "{\"metodo\": \"EFECTIVO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NIT_INVALIDO"));
        checkoutRecepcion("4817205-7", null).andExpect(status().isBadRequest());
        checkoutRecepcion("4817205-7", "{\"metodo\": \"EFECTIVO\"}").andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- app

    @Test
    void checkoutDesdeLaAppConPagoDelSaldo() throws Exception {
        String huesped = tokenHuesped(huespedId);

        // Fuera de la ventana (hoy es el día de entrada).
        mvc.perform(post("/api/v1/app/reservas/{c}/pago-saldo", codigo).header("Authorization", huesped))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_HORARIO"));

        reloj.fijar(SALIDA_10AM);
        when(stripe.crearSesion(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PasarelaStripe.Sesion("cs_saldo", "https://checkout.stripe.com/c/pay/cs_saldo",
                        "open", false, null, SALIDA_10AM.plusSeconds(1860)));
        mvc.perform(post("/api/v1/app/reservas/{c}/pago-saldo", codigo).header("Authorization", huesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.com/c/pay/cs_saldo"));

        // Con saldo pendiente no se confirma.
        checkoutApp(huesped).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_PENDIENTE"));

        // Llega el webhook: el saldo queda en 0 y el check-out se confirma.
        when(stripe.verificarEvento(anyString(), anyString())).thenReturn(new PasarelaStripe.Evento("evt_saldo",
                "checkout.session.completed",
                new PasarelaStripe.Sesion("cs_saldo", null, "complete", true, "pi_saldo", null)));
        mvc.perform(post("/api/v1/pagos/stripe/webhook").header("Stripe-Signature", "x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/app/reservas/{c}/pago-saldo", codigo).header("Authorization", huesped))
                .andExpect(status().isNoContent());

        checkoutApp(huesped)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReserva").value("FINALIZADA"));
    }

    @Test
    void laAppNoHaceCheckoutDespuesDeLasDoce() throws Exception {
        String huesped = tokenHuesped(huespedId);
        reloj.fijar(Instant.parse("2026-10-07T18:00:00Z")); // 12:00 en Guatemala
        mvc.perform(get("/api/v1/app/reservas/{c}/checkout", codigo).header("Authorization", huesped))
                .andExpect(jsonPath("$.puedeConfirmar").value(false));
        checkoutApp(huesped).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_HORARIO"));
    }

    // -------------------------------------------------------------- apoyo

    private ResultActions agregarCargo(String concepto, int cantidad, String precio) throws Exception {
        return mvc.perform(post("/api/v1/cuentas/{c}/cargos", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"concepto\": \"%s\", \"cantidad\": %d, \"precioUnitario\": %s}"
                        .formatted(concepto, cantidad, precio)));
    }

    private ResultActions checkoutRecepcion(String nit, String pago) throws Exception {
        String json = "{\"comprador\": {\"nit\": \"%s\", \"nombreComprador\": \"Carla Salida\"}%s}"
                .formatted(nit, pago == null ? "" : ", \"pago\": " + pago);
        return mvc.perform(post("/api/v1/checkout/{c}", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions checkoutApp(String huesped) throws Exception {
        return mvc.perform(post("/api/v1/app/reservas/{c}/checkout", codigo).header("Authorization", huesped)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comprador\": {\"nit\": \"CF\", \"nombreComprador\": \"Carla\"}, \"aceptaCancelarPedidos\": true}"));
    }

    /** El validador de JWT usa la hora real: el token se emite con ella. */
    private String tokenEmpleado() {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + tokens.emitirParaEmpleado(empleados.findById(recepcionistaId).orElseThrow())
                    .accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }

    private String tokenHuesped(long id) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + tokens.emitirParaHuesped(id).accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }
}
