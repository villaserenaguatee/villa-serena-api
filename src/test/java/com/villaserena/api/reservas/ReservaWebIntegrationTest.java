package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.reservas.stripe.PasarelaStripe;
import com.villaserena.api.reservas.stripe.VencimientoReservasJob;

/** Pruebas de OBJ-1B: disponibilidad, precio, reserva web, webhook y vencimiento a los 30 minutos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class ReservaWebIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaRepository reservas;

    @Autowired
    VencimientoReservasJob vencimiento;

    @MockitoBean
    PasarelaStripe stripe;

    @Test
    void precioConFinDeSemana() throws Exception {
        // Jueves 15 y viernes 16 de octubre: 850.00 + 850 × 1.25 = 1912.50 (ejemplo del contrato).
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-10-15").param("salida", "2026-10-17").param("huespedes", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].total").value(1912.50))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].desglose[1].finDeSemana")
                        .value(true));
    }

    @Test
    void precioConTemporadaYFinDeSemana() throws Exception {
        // Temporada alta (+20 %) desde el 15 de diciembre. Jueves 17: 850 × 1.2 = 1020.00;
        // viernes 18: 850 × 1.2 × 1.25 = 1275.00.
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-12-17").param("salida", "2026-12-19").param("huespedes", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].desglose[0].precio")
                        .value(1020.00))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].desglose[1].precio")
                        .value(1275.00))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].desglose[1].temporada")
                        .value("Temporada alta"))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Doble Superior')].total").value(2295.00));
    }

    @Test
    void fechasYCapacidadSeValidan() throws Exception {
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-10-01").param("salida", "2026-10-03").param("huespedes", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-10-15").param("salida", "2026-11-20").param("huespedes", "2"))
                .andExpect(status().isBadRequest());
        // 5 huéspedes: solo la Suite Familiar tiene capacidad.
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-10-15").param("salida", "2026-10-17").param("huespedes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoHabitacion.nombre").value("Suite Familiar"));
    }

    @Test
    void reservasQueSeCruzanAgotanElTipo() throws Exception {
        long suite = tipoId("Suite Volcán"); // 3 habitaciones: 201, 202 y 303
        crearReserva(suite, "2026-11-10", "2026-11-13", "uno@correo.test", 201);
        crearReserva(suite, "2026-11-12", "2026-11-14", "dos@correo.test", 201);
        crearReserva(suite, "2026-11-12", "2026-11-13", "tres@correo.test", 201);

        // La noche del 12 ya no tiene cupo; la del 10 sí.
        crearReserva(suite, "2026-11-11", "2026-11-13", "cuatro@correo.test", 409);
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-11-12").param("salida", "2026-11-13").param("huespedes", "1"))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Suite Volcán')]").isEmpty());
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-11-10").param("salida", "2026-11-11").param("huespedes", "1"))
                .andExpect(jsonPath("$[?(@.tipoHabitacion.nombre == 'Suite Volcán')]").isNotEmpty());
    }

    @Test
    void reservaWebNacePendienteConCuentaYCargo() throws Exception {
        String cuerpo = crearReserva(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", "maria@correo.test", 201);
        assertThat((String) JsonPath.read(cuerpo, "$.estado")).isEqualTo("PENDIENTE_PAGO");
        assertThat((String) JsonPath.read(cuerpo, "$.codigo")).matches("^VS-[A-Z0-9]{6}$");
        assertThat((String) JsonPath.read(cuerpo, "$.pagoVenceEn")).startsWith("2026-10-05T12:30:00-06:00");

        Reserva reserva = reservas.findByCodigo(JsonPath.read(cuerpo, "$.codigo")).orElseThrow();
        assertThat(jdbc.queryForObject("SELECT estado FROM cuentas WHERE reserva_id = ?", String.class,
                reserva.getId())).isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("""
                SELECT total FROM cargos c JOIN cuentas cu ON cu.id = c.cuenta_id
                WHERE cu.reserva_id = ? AND c.tipo = 'ALOJAMIENTO'""", BigDecimal.class, reserva.getId()))
                .isEqualByComparingTo("1912.50");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_noches WHERE reserva_id = ?", Integer.class,
                reserva.getId())).isEqualTo(2);

        // Mismo correo: se reutiliza el perfil del huésped sin cambiar sus datos.
        crearReserva(tipoId("Doble Superior"), "2026-10-20", "2026-10-21", "MARIA@correo.test", 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM huespedes WHERE lower(correo) = 'maria@correo.test'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void webhookRepetidoRegistraUnSoloPago() throws Exception {
        String codigo = iniciarPago("cs_test_1");

        PasarelaStripe.Evento evento = new PasarelaStripe.Evento("evt_1", "checkout.session.completed",
                new PasarelaStripe.Sesion("cs_test_1", null, "complete", true, "pi_1", null));
        when(stripe.verificarEvento(anyString(), anyString())).thenReturn(evento);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/pagos/stripe/webhook").header("Stripe-Signature", "t=1,v1=x")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.puedeReintentar").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos WHERE estado = 'APROBADO' AND stripe_session_id = 'cs_test_1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM historial_estados h JOIN reservas r ON r.id = h.id_entidad
                WHERE h.tipo_entidad = 'RESERVA' AND r.codigo = ? AND h.estado_nuevo = 'CONFIRMADA'""",
                Integer.class, codigo)).isEqualTo(1);
    }

    @Test
    void firmaInvalidaResponde400() throws Exception {
        when(stripe.verificarEvento(anyString(), anyString()))
                .thenThrow(new com.villaserena.api.comun.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "FIRMA_INVALIDA", "La firma del aviso de Stripe no es válida."));
        mvc.perform(post("/api/v1/pagos/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
    }

    @Test
    void reservaSinPagarSeCancelaALos30Minutos() throws Exception {
        String codigo = iniciarPago("cs_test_2");
        when(stripe.expirarSesion("cs_test_2"))
                .thenReturn(new PasarelaStripe.Sesion("cs_test_2", null, "expired", false, null, null));
        envejecer(codigo, 31);

        vencimiento.cancelarVencidas();

        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CANCELADA"))
                .andExpect(jsonPath("$.estadoPago").value("FALLIDO"));
        assertThat(jdbc.queryForObject("""
                SELECT c.estado FROM cuentas c JOIN reservas r ON r.id = c.reserva_id WHERE r.codigo = ?""",
                String.class, codigo)).isEqualTo("CERRADA");
    }

    @Test
    void siYaEstabaPagadaSeConfirmaEnLugarDeCancelarse() throws Exception {
        String codigo = iniciarPago("cs_test_3");
        when(stripe.expirarSesion("cs_test_3"))
                .thenReturn(new PasarelaStripe.Sesion("cs_test_3", null, "complete", true, "pi_3", null));
        envejecer(codigo, 31);

        vencimiento.cancelarVencidas();

        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"));
    }

    @Test
    void reservaDentroDelPlazoNoSeCancela() throws Exception {
        String codigo = iniciarPago("cs_test_4");
        envejecer(codigo, 10);

        vencimiento.cancelarVencidas();

        assertThat(reservas.findByCodigo(codigo).orElseThrow().getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    private String iniciarPago(String sesionId) throws Exception {
        String cuerpo = crearReserva(tipoId("Estándar Jardín"), "2026-10-20", "2026-10-22",
                sesionId + "@correo.test", 201);
        String codigo = JsonPath.read(cuerpo, "$.codigo");
        when(stripe.crearSesion(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PasarelaStripe.Sesion(sesionId, "https://checkout.stripe.com/c/pay/" + sesionId,
                        "open", false, null, Instant.now().plusSeconds(1800)));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.com/c/pay/" + sesionId));
        return codigo;
    }

    private void envejecer(String codigo, int minutos) {
        jdbc.update("UPDATE reservas SET creada_en = ? WHERE codigo = ?",
                java.sql.Timestamp.from(RelojFijoConfig.AHORA.minusSeconds(minutos * 60L)), codigo);
    }

    private long tipoId(String nombre) {
        return jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = ?", Long.class, nombre);
    }

    private String crearReserva(long tipoId, String entrada, String salida, String correo, int estado)
            throws Exception {
        String json = """
                {"tipoHabitacionId": %d, "entrada": "%s", "salida": "%s", "numeroHuespedes": 1,
                 "huesped": {"nombreCompleto": "Cliente Prueba", "correo": "%s", "telefono": "+502 5555 0000",
                             "nacionalidad": "Guatemalteca", "tipoDocumento": "DPI", "numeroDocumento": "1234567890101"}}"""
                .formatted(tipoId, LocalDate.parse(entrada), LocalDate.parse(salida), correo);
        return mvc.perform(post("/api/v1/publico/reservas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString();
    }
}
