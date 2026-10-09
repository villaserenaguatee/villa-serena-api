package com.villaserena.api.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.notificaciones.OutboxJob;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Criterio 3 de OBJ-4B: después del check-out, el Outbox envía el correo de la factura
 * con el PDF adjunto y llega a Mailpit (la misma imagen del Docker local). Contexto y
 * base propios: el check-out se confirma de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
class FacturaCorreoMailpitTest {

    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.31")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registro) {
        registro.add("spring.mail.host", MAILPIT::getHost);
        registro.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @AfterAll
    static void detener() {
        MAILPIT.stop();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    OutboxJob outboxJob;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    @Test
    void elCorreoConElPdfLlegaAMailpit() throws Exception {
        long recepcionista = empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow().getId();
        String codigo = transacciones.execute(t -> {
            long huesped = jdbc.queryForObject("""
                    INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento,
                                           numero_documento)
                    VALUES ('Carla Salida', 'carla@correo.test', '+502 5555 4444', 'Guatemalteca', 'DPI', '1')
                    RETURNING id""", Long.class);
            long habitacion = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '101'", Long.class);
            long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                    habitacion);
            Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huesped, tipo,
                    LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacion, recepcionista));
            checkinService.hacerCheckin(reserva.getCodigo(), recepcionista);
            return reserva.getCodigo();
        });
        // Solo interesa el correo de la factura: se descartan los de confirmación pendientes.
        jdbc.update("DELETE FROM outbox");

        mvc.perform(post("/api/v1/checkout/{c}", codigo).header("Authorization", tokenRecepcion(recepcionista))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comprador\": {\"nit\": \"CF\", \"nombreComprador\": \"Carla Salida\"}, "
                                + "\"pago\": {\"metodo\": \"EFECTIVO\"}}"))
                .andExpect(status().isOk());
        // Lo mismo que hace el job programado cada 30 segundos.
        assertThat(outboxJob.enviarPendientes()).isEqualTo(1);

        HttpClient http = HttpClient.newHttpClient();
        String api = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1";
        String lista = http.send(HttpRequest.newBuilder(URI.create(api + "/messages")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        List<Map<String, Object>> mensajes = JsonPath.read(lista, "$.messages");
        assertThat(mensajes).hasSize(1);
        assertThat((String) mensajes.getFirst().get("Subject")).isEqualTo("Tu factura VS-A 1 — Hotel Villa Serena");
        assertThat(JsonPath.<List<String>>read(lista, "$.messages[0].To[*].Address")).containsExactly(
                "carla@correo.test");

        String id = (String) mensajes.getFirst().get("ID");
        String mensaje = http.send(HttpRequest.newBuilder(URI.create(api + "/message/" + id)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertThat(JsonPath.<List<String>>read(mensaje, "$.Attachments[*].FileName"))
                .containsExactly("factura-VS-A-1.pdf");
        assertThat(JsonPath.<List<String>>read(mensaje, "$.Attachments[*].ContentType"))
                .containsExactly("application/pdf");
        assertThat(JsonPath.<String>read(mensaje, "$.HTML")).contains(codigo).contains("Q 1300.00");
    }

    private String tokenRecepcion(long id) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + tokens.emitirParaEmpleado(empleados.findById(id).orElseThrow()).accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }
}
