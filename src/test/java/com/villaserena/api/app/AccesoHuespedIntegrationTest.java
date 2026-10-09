package com.villaserena.api.app;

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
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pruebas de OBJ-3A-2 parte A y B (HU-HUE-08, HU-HUE-09): código de acceso con
 * hash, un solo uso, vencimiento, bloqueo tras 5 intentos y mis reservas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class AccesoHuespedIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    long huespedId;
    String codigoReserva;

    @BeforeEach
    void prepararHuespedConReserva() {
        reloj.reiniciar();
        long recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Ana Morales', 'acceso.ana@correo.test', '+502 5555 3333', 'Guatemalteca', 'DPI',
                        '3333333333333') RETURNING id""", Long.class);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, 1L,
                LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-22"), 2, null, recepcionista));
        codigoReserva = reserva.getCodigo();
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private ResultActions solicitar(String correo) throws Exception {
        return mvc.perform(post("/api/v1/app/acceso/solicitar-codigo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\": \"%s\"}".formatted(correo)));
    }

    private ResultActions verificar(String correo, String codigo) throws Exception {
        return mvc.perform(post("/api/v1/app/acceso/verificar-codigo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\": \"%s\", \"codigo\": \"%s\"}".formatted(correo, codigo)));
    }

    /** El código real solo existe en el correo encolado: la base guarda su hash. */
    private String codigoDelCorreo() {
        return jdbc.queryForObject("""
                SELECT payload ->> 'codigo' FROM outbox
                WHERE tipo = 'CORREO_OTP' AND destinatario = 'acceso.ana@correo.test'
                ORDER BY id DESC LIMIT 1""", String.class);
    }

    /**
     * El validador de JWT usa la hora real, así que para las pruebas que llevan
     * token el reloj se mueve a ahora antes de pedirlo.
     */
    private String tokenDeAcceso() throws Exception {
        reloj.fijar(Instant.now());
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        String respuesta = verificar("acceso.ana@correo.test", codigoDelCorreo())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(respuesta, "$.accessToken");
    }

    // ------------------------------------------------------------------
    // Parte A: el código
    // ------------------------------------------------------------------

    @Test
    void elCodigoSeEncolaYSoloSeGuardaSuHash() throws Exception {
        solicitar("acceso.ana@correo.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(AccesoHuespedService.MENSAJE_GENERICO));

        String codigo = codigoDelCorreo();
        assertThat(codigo).matches("^[0-9]{6}$");
        // En la base solo está el hash, nunca el código.
        assertThat(jdbc.queryForObject("SELECT codigo_hash FROM codigos_otp WHERE huesped_id = ?", String.class,
                huespedId)).isNotEqualTo(codigo).startsWith("$2");
    }

    @Test
    void laRespuestaEsIgualSinReservasOSinCorreo() throws Exception {
        // Correo que no existe.
        solicitar("desconocido@correo.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(AccesoHuespedService.MENSAJE_GENERICO));

        // Huésped que existe pero no tiene reservas.
        jdbc.update("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Sin Reservas', 'sin.reservas@correo.test', '+502 0', 'Guatemalteca', 'DPI', '8')""");
        solicitar("sin.reservas@correo.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(AccesoHuespedService.MENSAJE_GENERICO));

        // Ninguno de los dos genera código ni correo.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigos_otp", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE tipo = 'CORREO_OTP'", Integer.class))
                .isZero();
    }

    @Test
    void elCodigoCorrectoEntregaLosTokensDelHuesped() throws Exception {
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());

        verificar("acceso.ana@correo.test", codigoDelCorreo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tipoToken").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(900));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE huesped_id = ? AND revocado_en IS NULL", Integer.class,
                huespedId)).isEqualTo(1);
    }

    @Test
    void elCodigoSirveUnaSolaVez() throws Exception {
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        String codigo = codigoDelCorreo();

        verificar("acceso.ana@correo.test", codigo).andExpect(status().isOk());
        verificar("acceso.ana@correo.test", codigo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    void unCodigoVencidoNoSirve() throws Exception {
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        String codigo = codigoDelCorreo();

        reloj.fijar(reloj.instant().plus(AccesoHuespedService.VIGENCIA).plusSeconds(1));

        verificar("acceso.ana@correo.test", codigo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    void cincoIntentosFallidosBloqueanQuinceMinutos() throws Exception {
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        String correcto = codigoDelCorreo();
        String equivocado = correcto.equals("000000") ? "111111" : "000000";

        for (int intento = 1; intento <= 4; intento++) {
            verificar("acceso.ana@correo.test", equivocado)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        }
        // El quinto fallo deja el bloqueo puesto.
        verificar("acceso.ana@correo.test", equivocado).andExpect(status().isUnauthorized());

        // Ahora ni el código correcto entra.
        verificar("acceso.ana@correo.test", correcto)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ACCESO_BLOQUEADO"));

        // Pedir otro código no levanta el bloqueo ni reinicia los intentos.
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        verificar("acceso.ana@correo.test", correcto)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ACCESO_BLOQUEADO"));

        // Pasados los 15 minutos vuelve a funcionar.
        reloj.fijar(reloj.instant().plusSeconds(15 * 60 + 1));
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        verificar("acceso.ana@correo.test", codigoDelCorreo()).andExpect(status().isOk());
    }

    @Test
    void cerrarSesionRevocaElRefreshYBorraLosTelefonos() throws Exception {
        reloj.fijar(Instant.now());
        solicitar("acceso.ana@correo.test").andExpect(status().isOk());
        String respuesta = verificar("acceso.ana@correo.test", codigoDelCorreo())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String acceso = "Bearer " + JsonPath.read(respuesta, "$.accessToken");
        String refresh = JsonPath.read(respuesta, "$.refreshToken");
        jdbc.update("INSERT INTO dispositivos_push (huesped_id, token_expo) VALUES (?, ?)",
                huespedId, "ExponentPushToken[cerrar]");

        mvc.perform(post("/api/v1/app/cerrar-sesion").header("Authorization", acceso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"%s\"}".formatted(refresh)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE huesped_id = ? AND revocado_en IS NULL", Integer.class,
                huespedId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivos_push WHERE huesped_id = ?", Integer.class,
                huespedId)).isZero();
    }

    // ------------------------------------------------------------------
    // Parte B: mis reservas
    // ------------------------------------------------------------------

    @Test
    void misReservasSoloMuestraLasDelCorreoDelHuesped() throws Exception {
        String acceso = tokenDeAcceso();

        mvc.perform(get("/api/v1/app/reservas").header("Authorization", acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value(codigoReserva))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));

        mvc.perform(get("/api/v1/app/reservas/{c}", codigoReserva).header("Authorization", acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaCheckOut").value("12:00"))
                .andExpect(jsonPath("$.tipoHabitacion.nombre").value("Doble Superior"))
                .andExpect(jsonPath("$.numeroHuespedes").value(2))
                // Sin habitación asignada: la app lo muestra como "Por asignar".
                .andExpect(jsonPath("$.habitacion").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unaReservaAjenaResponde404() throws Exception {
        long recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        long otro = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Otro Huésped', 'otro.acceso@correo.test', '+502 1', 'Guatemalteca', 'DPI', '1')
                RETURNING id""", Long.class);
        Reserva ajena = reservaService.crear(SolicitudReserva.recepcion(otro, 1L,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 1, null, recepcionista));

        String acceso = tokenDeAcceso();
        mvc.perform(get("/api/v1/app/reservas/{c}", ajena.getCodigo()).header("Authorization", acceso))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ------------------------------------------------------------------
    // Parte C: teléfonos
    // ------------------------------------------------------------------

    @Test
    void registrarYBorrarElTelefono() throws Exception {
        String acceso = tokenDeAcceso();
        String cuerpo = "{\"tokenExpo\": \"ExponentPushToken[abc123]\"}";

        String primera = mvc.perform(post("/api/v1/app/dispositivos-push").header("Authorization", acceso)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(primera, "$.id")).longValue();

        // El mismo token otra vez: 200 con el mismo id, no un registro nuevo.
        mvc.perform(post("/api/v1/app/dispositivos-push").header("Authorization", acceso)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Un token con otro formato no se acepta.
        mvc.perform(post("/api/v1/app/dispositivos-push").header("Authorization", acceso)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tokenExpo\": \"abc\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/app/dispositivos-push/{id}", id).header("Authorization", acceso))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivos_push WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void sinTokenNoSeEntraALaApp() throws Exception {
        mvc.perform(get("/api/v1/app/reservas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/app/dispositivos-push").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenExpo\": \"ExponentPushToken[x]\"}"))
                .andExpect(status().isUnauthorized());
    }
}
