package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.util.Base64;
import java.util.UUID;

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
import com.villaserena.api.reservas.pagos.PasarelaPagos;
import com.villaserena.api.reservas.pagos.VencimientoReservasJob;

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
    PasarelaPagos pasarela;

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
        // 5 huéspedes: solo la Suite Familiar (V6) y los dos tipos para 5 del catálogo (V17).
        mvc.perform(get("/api/v1/publico/disponibilidad")
                        .param("entrada", "2026-10-15").param("salida", "2026-10-17").param("huespedes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipoHabitacion.nombre",
                        containsInAnyOrder("Suite Familiar", "Familiar Cinco", "Premium Cinco")));
    }

    @Test
    void reservasQueSeCruzanAgotanElTipo() throws Exception {
        long suite = tipoId("Suite Volcán"); // 4 habitaciones: 201, 202, 303 (V6) y 304 (V24)
        crearReserva(suite, "2026-11-10", "2026-11-13", "uno@correo.test", 201);
        crearReserva(suite, "2026-11-12", "2026-11-14", "dos@correo.test", 201);
        crearReserva(suite, "2026-11-12", "2026-11-13", "tres@correo.test", 201);
        crearReserva(suite, "2026-11-12", "2026-11-13", "cuatro@correo.test", 201);

        // La noche del 12 ya no tiene cupo; la del 10 sí.
        crearReserva(suite, "2026-11-11", "2026-11-13", "cinco@correo.test", 409);
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
        assertThat(jdbc.queryForObject("SELECT status AS \"estado\" FROM accounts WHERE reservation_id = ?", String.class,
                reserva.getId())).isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("""
                SELECT total  FROM charges c JOIN accounts cu ON cu.id = c.account_id
                WHERE cu.reservation_id = ? AND c.type = 'ALOJAMIENTO'""", BigDecimal.class, reserva.getId()))
                .isEqualByComparingTo("1912.50");
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM reservation_nights WHERE reservation_id = ?", Integer.class,
                reserva.getId())).isEqualTo(2);

        // Mismo correo: se reutiliza el perfil del huésped sin cambiar sus datos.
        crearReserva(tipoId("Doble Superior"), "2026-10-20", "2026-10-21", "MARIA@correo.test", 201);
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM guests WHERE lower(email) = 'maria@correo.test'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void webhookRepetidoRegistraUnSoloPago() throws Exception {
        String codigo = iniciarPago("cs_test_1");

        PasarelaPagos.Evento evento = new PasarelaPagos.Evento("evt_1", "intent.succeeded",
                new PasarelaPagos.Sesion("cs_test_1", null, "paid", true, "pi_1", null));
        when(pasarela.verificarEvento(anyString(), any())).thenReturn(evento);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/pagos/recurrente/webhook").header("svix-id", "msg_test")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"))
                .andExpect(jsonPath("$.estadoPago").value("APROBADO"))
                .andExpect(jsonPath("$.puedeReintentar").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM payments WHERE status = 'APROBADO' AND checkout_id = 'cs_test_1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*)  FROM status_history h JOIN reservations r ON r.id = h.entity_id
                WHERE h.entity_type = 'RESERVA' AND r.code = ? AND h.new_status = 'CONFIRMADA'""",
                Integer.class, codigo)).isEqualTo(1);
    }

    @Test
    void firmaInvalidaResponde400() throws Exception {
        when(pasarela.verificarEvento(anyString(), any()))
                .thenThrow(new com.villaserena.api.comun.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "FIRMA_INVALIDA", "La firma del aviso de Recurrente no es válida."));
        mvc.perform(post("/api/v1/pagos/recurrente/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FIRMA_INVALIDA"));
    }

    @Test
    void reservaSinPagarSeCancelaALos30Minutos() throws Exception {
        String codigo = iniciarPago("cs_test_2");
        when(pasarela.expirarSesion("cs_test_2"))
                .thenReturn(new PasarelaPagos.Sesion("cs_test_2", null, "expired", false, null, null));
        envejecer(codigo, 31);

        vencimiento.cancelarVencidas();

        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CANCELADA"))
                .andExpect(jsonPath("$.estadoPago").value("FALLIDO"));
        assertThat(jdbc.queryForObject("""
                SELECT c.status AS "estado" FROM accounts c JOIN reservations r ON r.id = c.reservation_id WHERE r.code = ?""",
                String.class, codigo)).isEqualTo("CERRADA");
    }

    @Test
    void siYaEstabaPagadaSeConfirmaEnLugarDeCancelarse() throws Exception {
        String codigo = iniciarPago("cs_test_3");
        when(pasarela.expirarSesion("cs_test_3"))
                .thenReturn(new PasarelaPagos.Sesion("cs_test_3", null, "paid", true, "pi_3", null));
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

    @Test
    void creaReservaWebSinNacionalidadYConservaElPerfilHistorico() throws Exception {
        crearReserva(tipoId("Estándar Jardín"), "2026-10-20", "2026-10-22", "nuevo@correo.test", 201);
        assertThat(jdbc.queryForObject("SELECT nationality AS \"nacionalidad\" FROM guests WHERE email = 'nuevo@correo.test'",
                String.class)).isNull();
        jdbc.update("UPDATE guests SET nationality = 'Mexicana' WHERE email = 'nuevo@correo.test'");
        crearReserva(tipoId("Estándar Jardín"), "2026-10-23", "2026-10-24", "nuevo@correo.test", 201);
        assertThat(jdbc.queryForObject("SELECT nationality AS \"nacionalidad\" FROM guests WHERE email = 'nuevo@correo.test'",
                String.class)).isEqualTo("Mexicana");
    }

    @Test
    void checkoutVencidoMarcaPagoFallidoSinConfirmarReserva() throws Exception {
        String codigo = iniciarPago("ch_vencido");
        when(pasarela.verificarEvento(anyString(), any())).thenReturn(new PasarelaPagos.Evento(
                "msg_vencido", "checkout.expired",
                new PasarelaPagos.Sesion("ch_vencido", null, "expired", false, null, null)));
        mvc.perform(post("/api/v1/pagos/recurrente/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.estadoPago").value("FALLIDO"));
    }

    @Test
    void pagoEnProcesoImpideCrearOtroCheckout() throws Exception {
        String codigo = iniciarPago("ch_en_proceso");
        when(pasarela.obtenerSesion("ch_en_proceso")).thenReturn(new PasarelaPagos.Sesion(
                "ch_en_proceso", null, "payment_in_progress", false, null, null));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAGO_EN_VERIFICACION"));
        org.mockito.Mockito.verify(pasarela, org.mockito.Mockito.times(1))
                .crearSesion(anyString(), any(), anyString(), any(), any());
        assertThat(jdbc.queryForObject("SELECT status AS \"estado\" FROM payments WHERE checkout_id = 'ch_en_proceso'", String.class))
                .isEqualTo("PENDIENTE");
    }

    @Test
    void reservaFueraDePlazoNoPermiteIniciarPago() throws Exception {
        String codigo = iniciarPago("ch_fuera_plazo");
        envejecer(codigo, 31);
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PLAZO_PAGO_VENCIDO"));
    }

    @Test
    void rutaAnteriorDeStripeNoQuedaPublica() throws Exception {
        mvc.perform(post("/api/v1/pagos/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void montoDistintoNoConfirmaLaReserva() throws Exception {
        String codigo = iniciarPago("ch_monto_distinto");
        when(pasarela.verificarEvento(anyString(), any())).thenReturn(new PasarelaPagos.Evento(
                "msg_monto_distinto", "intent.succeeded", new PasarelaPagos.Sesion("ch_monto_distinto", null,
                "paid", true, "in_distinto", null, new BigDecimal("0.01"), "GTQ")));
        mvc.perform(post("/api/v1/pagos/recurrente/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAGO_NO_COINCIDE"));
        assertThat(reservas.findByCodigo(codigo).orElseThrow().getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    void consultaDistingueContinuarProcesarYEsperarWebhookSinMutaciones() throws Exception {
        String codigo = iniciarPago("ch_consulta");
        for (String estado : new String[]{"unpaid", "payment_in_progress", "paid", "expired"}) {
            when(pasarela.obtenerSesion("ch_consulta")).thenReturn(new PasarelaPagos.Sesion(
                    "ch_consulta", "https://pago.test", estado, "paid".equals(estado), null,
                    RelojFijoConfig.AHORA.plusSeconds(600)));
            String accion = switch (estado) {
                case "unpaid" -> "CONTINUAR";
                case "expired" -> "REINTENTAR";
                default -> "ESPERAR";
            };
            mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accionPago").value(accion))
                    .andExpect(jsonPath("$.puedeReintentar").value("expired".equals(estado)))
                    .andExpect(jsonPath("$.estadoPago").value("PENDIENTE"));
        }
        assertThat(reservas.findByCodigo(codigo).orElseThrow().getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM payments WHERE checkout_id='ch_consulta'", Integer.class)).isEqualTo(1);
    }

    @Test
    void consultaFueraDePlazoNoPermiteOtroIntentoAunqueElJobNoHayaCancelado() throws Exception {
        String codigo = iniciarPago("ch_tarde");
        envejecer(codigo, 31);
        when(pasarela.obtenerSesion("ch_tarde")).thenReturn(new PasarelaPagos.Sesion(
                "ch_tarde", null, "expired", false, null, null));
        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.estadoReserva").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.accionPago").value("NINGUNA"))
                .andExpect(jsonPath("$.puedeReintentar").value(false));
    }

    @Test
    void consultarErrorDePasarelaNoHabilitaPago() throws Exception {
        String codigo = iniciarPago("ch_error");
        when(pasarela.obtenerSesion("ch_error")).thenThrow(
                new com.villaserena.api.reservas.pagos.ErrorPasarela("No disponible"));
        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCheckout").value("NO_VERIFICADO"))
                .andExpect(jsonPath("$.accionPago").value("ESPERAR"))
                .andExpect(jsonPath("$.puedeReintentar").value(false))
                .andExpect(jsonPath("$.urlPago").doesNotExist());
    }

    @Test
    void checkoutVencidoPorFechaNoSeRetomaHastaConfirmarExpired() throws Exception {
        String codigo = iniciarPago("ch_vencimiento_incierto");
        when(pasarela.obtenerSesion("ch_vencimiento_incierto")).thenReturn(new PasarelaPagos.Sesion(
                "ch_vencimiento_incierto", "https://pago.test", "unpaid", false, null, RelojFijoConfig.AHORA));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EN_VERIFICACION"));
        org.mockito.Mockito.verify(pasarela, org.mockito.Mockito.times(1))
                .crearSesion(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void checkoutAbiertoSeReutilizaYExpiredPermiteOtroDentroDelPlazo() throws Exception {
        String codigo = iniciarPago("ch_anterior");
        when(pasarela.obtenerSesion("ch_anterior")).thenReturn(new PasarelaPagos.Sesion(
                "ch_anterior", "https://pago.test/anterior", "unpaid", false, null,
                RelojFijoConfig.AHORA.plusSeconds(600)));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.urlPago").value("https://pago.test/anterior"));
        org.mockito.Mockito.verify(pasarela, org.mockito.Mockito.times(1))
                .crearSesion(anyString(), any(), anyString(), any(), any());
        when(pasarela.obtenerSesion("ch_anterior")).thenReturn(new PasarelaPagos.Sesion(
                "ch_anterior", null, "expired", false, null, null));
        when(pasarela.crearSesion(anyString(), any(), anyString(), any(), any())).thenReturn(
                new PasarelaPagos.Sesion("ch_nuevo", "https://pago.test/nuevo", "unpaid", false, null,
                        RelojFijoConfig.AHORA.plusSeconds(1200)));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.urlPago").value("https://pago.test/nuevo"));
        when(pasarela.obtenerSesion("ch_nuevo")).thenReturn(new PasarelaPagos.Sesion(
                "ch_nuevo", "https://pago.test/nuevo", "unpaid", false, null,
                RelojFijoConfig.AHORA.plusSeconds(1200)));
        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.accionPago").value("CONTINUAR"))
                .andExpect(jsonPath("$.urlPago").value("https://pago.test/nuevo"));
        assertThat(jdbc.queryForObject("SELECT status AS \"estado\" FROM payments WHERE checkout_id='ch_anterior'", String.class))
                .isEqualTo("FALLIDO");
    }

    private String iniciarPago(String sesionId) throws Exception {
        String cuerpo = crearReserva(tipoId("Estándar Jardín"), "2026-10-20", "2026-10-22",
                sesionId + "@correo.test", 201);
        String codigo = JsonPath.read(cuerpo, "$.codigo");
        when(pasarela.crearSesion(anyString(), any(), anyString(), any(), any()))
                .thenReturn(new PasarelaPagos.Sesion(sesionId, "https://app.recurrente.com/checkout-session/" + sesionId,
                        "unpaid", false, null, Instant.now().plusSeconds(1800)));
        mvc.perform(post("/api/v1/publico/reservas/{codigo}/pago", codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago").value("https://app.recurrente.com/checkout-session/" + sesionId));
        org.mockito.Mockito.verify(pasarela).crearSesion(
                org.mockito.ArgumentMatchers.eq("Reserva Villa Serena · " + codigo),
                org.mockito.ArgumentMatchers.argThat(monto -> monto.compareTo(reservas.findByCodigo(codigo).orElseThrow().getTotal()) == 0),
                org.mockito.ArgumentMatchers.endsWith("/reserva/resultado?codigo=" + codigo),
                any(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("codigoReserva", codigo, "tipoPago", "RESERVA")));
        return codigo;
    }

    private void envejecer(String codigo, int minutos) {
        Reserva reserva = reservas.findByCodigo(codigo).orElseThrow();
        reserva.setCreadaEn(RelojFijoConfig.AHORA.minusSeconds(minutos * 60L));
        reservas.saveAndFlush(reserva);
    }

    private long tipoId(String nombre) {
        return jdbc.queryForObject("SELECT id  FROM room_types WHERE name = ?", Long.class, nombre);
    }

    private String crearReserva(long tipoId, String entrada, String salida, String correo, int estado)
            throws Exception {
        String json = """
                {"tipoHabitacionId": %d, "entrada": "%s", "salida": "%s", "numeroHuespedes": 1,
                 "huesped": {"nombreCompleto": "Cliente Prueba", "correo": "%s", "telefono": "+502 5555 0000",
                             "tipoDocumento": "DPI", "numeroDocumento": "1234567890101"}}"""
                .formatted(tipoId, LocalDate.parse(entrada), LocalDate.parse(salida), correo);
        UUID borrador = UUID.randomUUID();
        return mvc.perform(post("/api/v1/publico/reservas").contentType(MediaType.APPLICATION_JSON).content(json)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Booking-Draft", borrador.toString())
                        .header("X-Booking-Email-Verification", correoVerificado(correo, borrador)))
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * La web verifica el correo antes de reservar (VerificacionCorreoReserva). Aquí se deja el
     * comprobante ya verificado, como si el huésped hubiera escrito el código que le llegó.
     */
    private String correoVerificado(String correo, UUID borrador) {
        byte[] azar = new byte[32];
        new java.security.SecureRandom().nextBytes(azar);
        String comprobante = Base64.getUrlEncoder().withoutPadding().encodeToString(azar);
        jdbc.update("""
                INSERT INTO reservation_email_verifications (id, email, draft, code_hash, created_at, expires_at,
                                                           verified_at, proof_hash, proof_expires_at)
                VALUES (?, lower(?), ?, 'NO_APLICA', ?, ?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), ?)""",
                UUID.randomUUID(), correo, borrador, java.sql.Timestamp.from(RelojFijoConfig.AHORA),
                java.sql.Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(600)),
                java.sql.Timestamp.from(RelojFijoConfig.AHORA), comprobante,
                java.sql.Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(1800)));
        return comprobante;
    }
}
