package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import com.villaserena.api.huespedes.HuespedDatos;
import com.villaserena.api.huespedes.TipoDocumento;
import com.villaserena.api.reservas.stripe.ErrorStripe;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/** Pruebas de OBJ-2B: reservas de Recepción, cancelación con reembolso y check-in. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class RecepcionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    String recepcion;
    long huespedId;

    @BeforeEach
    void preparar() {
        recepcion = token("ana.perez@villaserena.test");
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Luisa Recepción', 'luisa@correo.test', '+502 5555 2222', 'Guatemalteca', 'DPI', '2222222220101')
                RETURNING id""", Long.class);
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ---------------------------------------------------------------- crear

    @Test
    void recepcionCreaReservaConfirmadaSinPago() throws Exception {
        long habitacion = habitacionId("103");
        crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", 2, habitacion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.canal").value("RECEPCION"))
                .andExpect(jsonPath("$.habitacion.numero").value("103"))
                .andExpect(jsonPath("$.huesped.correo").value("luisa@correo.test"))
                .andExpect(jsonPath("$.total").value(1912.50))
                .andExpect(jsonPath("$.saldoPendiente").value(1912.50))
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("CONFIRMADA"))
                .andExpect(jsonPath("$.historial[0].responsable").value("Ana Pérez"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos", Integer.class)).isZero();
    }

    @Test
    void habitacionDeOtroTipoOTraslapadaSeRechaza() throws Exception {
        crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", 1, habitacionId("101"))
                .andExpect(status().isBadRequest());
        crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", 1, habitacionId("103"))
                .andExpect(status().isCreated());
        crear(tipoId("Doble Superior"), "2026-10-16", "2026-10-18", 1, habitacionId("103"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_OCUPADA"));
    }

    @Test
    void disponibilidadDeRecepcionMuestraHabitacionesRestantes() throws Exception {
        mvc.perform(get("/api/v1/reservas/disponibilidad").header("Authorization", recepcion)
                        .param("entrada", "2026-10-15").param("salida", "2026-10-17").param("huespedes", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].habitacionesDisponibles")
                        .value(4));
    }

    @Test
    void otroRolNoUsaLasRutasDeRecepcion() throws Exception {
        mvc.perform(get("/api/v1/reservas/disponibilidad").header("Authorization", token("rodrigo.aju@villaserena.test"))
                        .param("entrada", "2026-10-15").param("salida", "2026-10-17").param("huespedes", "2"))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------- cancelar

    @Test
    void conCuarentaYOchoHorasExactasHayReembolsoTotal() throws Exception {
        String codigo = reservaWebPagada("2026-10-09");
        // 15:00 del 9 de octubre − 48 h = 15:00 del 7 de octubre (America/Guatemala).
        reloj.fijar(Instant.parse("2026-10-07T21:00:00Z"));

        mvc.perform(get("/api/v1/reservas/{c}/cancelacion", codigo).header("Authorization", recepcion))
                .andExpect(jsonPath("$.resultado").value("REEMBOLSO_TOTAL"))
                .andExpect(jsonPath("$.montoReembolso").value(2125.00));

        cancelar(codigo, "Cambio de planes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.historial[2].motivo").value("Cambio de planes"));
        verify(stripe).reembolsar("pi_prueba");
        assertThat(estadoPago(codigo)).isEqualTo("REEMBOLSADO");
        assertThat(estadoCuenta(codigo)).isEqualTo("CERRADA");
    }

    @Test
    void conMenosDeCuarentaYOchoHorasNoHayReembolso() throws Exception {
        String codigo = reservaWebPagada("2026-10-09");
        reloj.fijar(Instant.parse("2026-10-07T21:00:01Z"));

        mvc.perform(get("/api/v1/reservas/{c}/cancelacion", codigo).header("Authorization", recepcion))
                .andExpect(jsonPath("$.resultado").value("SIN_REEMBOLSO"))
                .andExpect(jsonPath("$.montoReembolso").value(0.00));
        cancelar(codigo, "No se presentó").andExpect(status().isOk());
        assertThat(estadoPago(codigo)).isEqualTo("APROBADO");
    }

    @Test
    void sinPagosNoHayNadaQueReembolsar() throws Exception {
        String codigo = JsonPath.read(crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", 1, null)
                .andReturn().getResponse().getContentAsString(), "$.codigo");
        mvc.perform(get("/api/v1/reservas/{c}/cancelacion", codigo).header("Authorization", recepcion))
                .andExpect(jsonPath("$.resultado").value("SIN_PAGOS"));
    }

    @Test
    void reservaDeCanalNoSeCancela() throws Exception {
        Reserva canal = reservaService.crear(SolicitudReserva.canal(CanalReserva.BOOKING, "BK-123",
                new BigDecimal("1500.00"), tipoId("Doble Superior"), LocalDate.parse("2026-10-15"),
                LocalDate.parse("2026-10-17"), 2, new HuespedDatos("Tom Canal", "tom@correo.test", "+1 555 0000",
                        "Estadounidense", TipoDocumento.PASAPORTE, "X1234567")));
        cancelar(canal.getCodigo(), "Prueba")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CANAL_NO_CANCELABLE"));
    }

    @Test
    void siStripeRechazaElReembolsoNoSeCancela() throws Exception {
        String codigo = reservaWebPagada("2026-10-20");
        doThrow(new ErrorStripe("rechazado")).when(stripe).reembolsar(anyString());

        cancelar(codigo, "Cambio de planes")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REEMBOLSO_RECHAZADO"));
        assertThat(jdbc.queryForObject("SELECT estado FROM reservas WHERE codigo = ?", String.class, codigo))
                .isEqualTo("CONFIRMADA");
    }

    @Test
    void elMotivoEsObligatorio() throws Exception {
        String codigo = reservaWebPagada("2026-10-20");
        cancelar(codigo, " ").andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------- check-in

    @Test
    void checkInDejaLaReservaEnEstadiaYLaHabitacionOcupada() throws Exception {
        long habitacion = habitacionId("104");
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-05", "2026-10-07", 1, habitacion));

        checkIn(codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ESTADIA"));
        assertThat(jdbc.queryForObject("SELECT ocupacion FROM habitaciones WHERE id = ?", String.class, habitacion))
                .isEqualTo("OCUPADA");
    }

    @Test
    void checkInFueraDeFecha() throws Exception {
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-06", "2026-10-08", 1, habitacionId("105")));
        checkIn(codigo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHECKIN_FUERA_DE_FECHA"));

        // El día de salida tampoco se permite.
        reloj.fijar(Instant.parse("2026-10-08T18:00:00Z"));
        checkIn(codigo).andExpect(jsonPath("$.codigo").value("CHECKIN_FUERA_DE_FECHA"));
    }

    @Test
    void checkInSinHabitacionAsignada() throws Exception {
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-05", "2026-10-07", 1, null));
        checkIn(codigo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_HABITACION_ASIGNADA"));
    }

    @Test
    void checkInConHabitacionSucia() throws Exception {
        long habitacion = habitacionId("203");
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE id = ?", habitacion);
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-05", "2026-10-07", 1, habitacion));
        checkIn(codigo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_NO_LISTA"))
                .andExpect(jsonPath("$.mensaje").value("La habitación 203 no está libre y limpia."));
    }

    // ------------------------------------------------------------ apoyo

    /** Reserva web confirmada con un pago de Stripe aprobado (como si ya hubiera llegado el webhook). */
    private String reservaWebPagada(String entrada) {
        Reserva r = reservaService.crear(SolicitudReserva.web(tipoId("Doble Superior"), LocalDate.parse(entrada),
                LocalDate.parse(entrada).plusDays(2), 2, new HuespedDatos("Web Pagada", "web.pagada@correo.test",
                        "+502 5555 3333", "Guatemalteca", TipoDocumento.DPI, "3333333330101")));
        reservaService.cambiarEstado(r, EstadoReserva.CONFIRMADA, com.villaserena.api.comun.Responsable.STRIPE, null);
        jdbc.update("""
                INSERT INTO pagos (cuenta_id, metodo, estado, monto, stripe_session_id, stripe_payment_intent_id, aprobado_en)
                SELECT id, 'STRIPE', 'APROBADO', ?, 'cs_prueba', 'pi_prueba', now() FROM cuentas WHERE reserva_id = ?""",
                r.getTotal(), r.getId());
        return r.getCodigo();
    }

    private ResultActions crear(long tipo, String entrada, String salida, int huespedes, Long habitacion)
            throws Exception {
        String json = """
                {"huespedId": %d, "tipoHabitacionId": %d, "entrada": "%s", "salida": "%s",
                 "numeroHuespedes": %d, "habitacionId": %s}"""
                .formatted(huespedId, tipo, entrada, salida, huespedes, habitacion);
        return mvc.perform(post("/api/v1/reservas").header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cancelar(String codigo, String motivo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{c}/cancelar", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"" + motivo + "\"}"));
    }

    private ResultActions checkIn(String codigo) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{c}/check-in", codigo).header("Authorization", recepcion));
    }

    private static String codigoDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.codigo");
    }

    private String estadoPago(String codigo) {
        return jdbc.queryForObject("""
                SELECT p.estado FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id
                JOIN reservas r ON r.id = c.reserva_id WHERE r.codigo = ?""", String.class, codigo);
    }

    private String estadoCuenta(String codigo) {
        return jdbc.queryForObject("""
                SELECT c.estado FROM cuentas c JOIN reservas r ON r.id = c.reserva_id WHERE r.codigo = ?""",
                String.class, codigo);
    }

    /** El validador de JWT usa la hora real: el token se emite con ella y luego se vuelve al reloj de prueba. */
    private String token(String correo) {
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow())
                    .accessToken();
        } finally {
            reloj.reiniciar();
        }
    }

    private long tipoId(String nombre) {
        return jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = ?", Long.class, nombre);
    }

    private long habitacionId(String numero) {
        return jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = ?", Long.class, numero);
    }
}
