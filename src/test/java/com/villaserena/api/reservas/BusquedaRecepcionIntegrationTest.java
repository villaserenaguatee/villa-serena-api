package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

import jakarta.persistence.EntityManager;

/**
 * Pruebas de OBJ-2A: huéspedes, huéspedes adicionales, búsqueda de reservas, detalle y
 * datos del Gantt. "Hoy" es el lunes 5 de octubre de 2026 (RelojFijoConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class BusquedaRecepcionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    PasarelaStripe stripe;

    String recepcion;

    @BeforeEach
    void preparar() {
        recepcion = token("ana.perez@villaserena.test");
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ------------------------------------------------- huéspedes (HU-REC-01)

    @Test
    void registrarUnHuespedNuevoYLuegoConElMismoCorreoDevuelveElExistente() throws Exception {
        registrar("María José López", "Maria.Lopez@correo.test", "2456789010101")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yaExistia").value(false))
                .andExpect(jsonPath("$.huesped.id").isNumber())
                .andExpect(jsonPath("$.huesped.nombreCompleto").value("María José López"))
                .andExpect(jsonPath("$.huesped.tipoDocumento").value("DPI"));
        registrar("Otro Nombre", "maria.lopez@CORREO.test", "9999999990101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaExistia").value(true))
                .andExpect(jsonPath("$.huesped.nombreCompleto").value("María José López"))
                .andExpect(jsonPath("$.huesped.numeroDocumento").value("2456789010101"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM huespedes WHERE lower(correo) = 'maria.lopez@correo.test'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void registrarExigeLosSeisDatos() throws Exception {
        mvc.perform(post("/api/v1/huespedes").header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCompleto\": \"Sin correo\", \"telefono\": \"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("correo")));
    }

    @Test
    void buscaHuespedesPorNombreDocumentoOCorreo() throws Exception {
        registrar("María José López", "maria.lopez@correo.test", "2456789010101");
        registrar("Pedro López", "pedro@correo.test", "1111111110101");
        registrar("Ana Gómez", "ana.gomez@correo.test", "5550001110101");
        buscarHuesped("LÓPEZ").andExpect(jsonPath("$[*].nombreCompleto",
                contains("María José López", "Pedro López")));
        buscarHuesped("555000").andExpect(jsonPath("$[*].nombreCompleto", contains("Ana Gómez")));
        buscarHuesped("ana.gomez@").andExpect(jsonPath("$", hasSize(1)));
        buscarHuesped("%").andExpect(status().isBadRequest());
        buscarHuesped("%%").andExpect(jsonPath("$", hasSize(0)));
        buscarHuesped("zzz").andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------- huéspedes adicionales (HU-REC-02)

    @Test
    void agregaAdicionalesHastaElNumeroDeHuespedes() throws Exception {
        String codigo = crearReserva(huesped("titular@correo.test"), "Doble Superior", "2026-10-15", "2026-10-17",
                2, null);
        adicional(codigo, "Luis Pérez")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombreCompleto").value("Luis Pérez"))
                .andExpect(jsonPath("$.tipoDocumento").value("PASAPORTE"));
        adicional(codigo, "Tercera Persona")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HUESPEDES_EXCEDIDOS"))
                .andExpect(jsonPath("$.mensaje").value("La reserva es para 2 huéspedes y ya están todos registrados."));
        detalle(codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huespedesAdicionales[*].nombreCompleto", contains("Luis Pérez")));
    }

    @Test
    void adicionalesSoloEnReservasConfirmadasOEnEstadia() throws Exception {
        String codigo = crearReserva(huesped("titular@correo.test"), "Doble Superior", "2026-10-15", "2026-10-17",
                3, null);
        jdbc.update("UPDATE reservas SET estado = 'CANCELADA' WHERE codigo = ?", codigo);
        adicional(codigo, "Luis Pérez")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        adicional("VS-NOEXIS", "Luis Pérez").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/reservas/{c}/huespedes-adicionales", codigo).header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\": \"Solo nombre\"}"))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------- búsqueda y detalle (HU-REC-06)

    @Test
    void buscaPorTextoCodigoEstadoCanalYFechas() throws Exception {
        String lopez = crearReserva(registrarId("María López", "maria@correo.test", "2456789010101"),
                "Doble Superior", "2026-10-15", "2026-10-17", 2, null);
        String gomez = crearReserva(registrarId("Ana Gómez", "ana@correo.test", "5550001110101"),
                "Suite Volcán", "2026-10-20", "2026-10-22", 2, null);
        String canal = reservaDeCanal("Hans Müller", "BK-555", "2026-11-02", "2026-11-04");
        jdbc.update("UPDATE reservas SET estado = 'CANCELADA' WHERE codigo = ?", gomez);

        buscar("texto=lópez").andExpect(codigos(lopez));
        buscar("texto=555000111").andExpect(codigos(gomez));
        buscar("codigo=" + canal.toLowerCase())
                .andExpect(codigos(canal))
                .andExpect(jsonPath("$.contenido[0].canal").value("BOOKING"))
                .andExpect(jsonPath("$.contenido[0].identificadorExterno").value("BK-555"));
        buscar("estado=CANCELADA").andExpect(codigos(gomez));
        buscar("canal=RECEPCION").andExpect(codigos(lopez, gomez));
        buscar("desde=2026-10-17&hasta=2026-10-20").andExpect(codigos(gomez));
        buscar("desde=2026-10-16&hasta=2026-10-16").andExpect(codigos(lopez));
        buscar("canal=RECEPCION&estado=CONFIRMADA&texto=maría").andExpect(codigos(lopez));

        buscar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].codigo").value(lopez))
                .andExpect(jsonPath("$.contenido[0].huespedPrincipal.nombreCompleto").value("María López"))
                .andExpect(jsonPath("$.contenido[0].tipoHabitacion.nombre").value("Doble Superior"))
                .andExpect(jsonPath("$.contenido[0].habitacion").value(is((Object) null)))
                .andExpect(jsonPath("$.contenido[0].noches").value(2))
                .andExpect(jsonPath("$.contenido[0].saldoPendiente").value(1912.50))
                .andExpect(jsonPath("$.totalElementos").value(3));
    }

    @Test
    void filtrosRapidosDeHoyYSaldo() throws Exception {
        String llega = crearReserva(huesped("llega@correo.test"), "Doble Superior", "2026-10-05", "2026-10-07", 1,
                null);
        String sale = crearReserva(huesped("sale@correo.test"), "Suite Volcán", "2026-10-06", "2026-10-08", 1, null);
        jdbc.update("""
                UPDATE reservas SET estado = 'EN_ESTADIA', fecha_entrada = '2026-10-03', fecha_salida = '2026-10-05'
                WHERE codigo = ?""", sale);
        crearReserva(huesped("manana@correo.test"), "Doble Superior", "2026-10-06", "2026-10-07", 1, null);

        buscar("rapido=LLEGAN_HOY").andExpect(codigos(llega));
        buscar("rapido=SALEN_HOY")
                .andExpect(codigos(sale))
                .andExpect(jsonPath("$.contenido[0].estado").value("EN_ESTADIA"))
                .andExpect(jsonPath("$.contenido[0].saldoPendiente").value(2900.00));
        buscar("rapido=LLEGAN_HOY&canal=BOOKING").andExpect(jsonPath("$.contenido", hasSize(0)));

        reloj.fijar(Instant.parse("2026-10-06T18:00:00Z"));
        buscar("rapido=LLEGAN_HOY").andExpect(jsonPath("$.contenido", hasSize(1)));
        buscar("rapido=SALEN_HOY").andExpect(jsonPath("$.contenido", hasSize(0)));
    }

    @Test
    void paginaLosResultados() throws Exception {
        long titular = huesped("pagina@correo.test");
        for (int i = 0; i < 5; i++) {
            crearReserva(titular, "Doble Superior", "2026-10-%02d".formatted(10 + i * 2),
                    "2026-10-%02d".formatted(11 + i * 2), 1, null);
        }
        buscar("page=0&size=2")
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido[0].entrada").value("2026-10-10"));
        buscar("page=2&size=2")
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].entrada").value("2026-10-18"));
        buscar("page=5&size=2").andExpect(jsonPath("$.contenido", hasSize(0)));
        buscar("size=0").andExpect(status().isBadRequest());
        buscar("size=101").andExpect(status().isBadRequest());
        buscar("page=-1").andExpect(status().isBadRequest());
        buscar("desde=2026-10-20&hasta=2026-10-10").andExpect(status().isBadRequest());
        buscar("codigo=malo").andExpect(status().isBadRequest());
        buscar("estado=PERDIDA").andExpect(status().isBadRequest());
    }

    @Test
    void detalleDeUnaReserva() throws Exception {
        String codigo = crearReserva(huesped("detalle@correo.test"), "Doble Superior", "2026-10-15", "2026-10-17",
                2, habitacionId("103"));
        detalle(codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.canal").value("RECEPCION"))
                .andExpect(jsonPath("$.habitacion.numero").value("103"))
                .andExpect(jsonPath("$.huesped.correo").value("detalle@correo.test"))
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("CONFIRMADA"))
                .andExpect(jsonPath("$.historial[0].responsable").value("Ana Pérez"));
        detalle("VS-NOEXIS").andExpect(status().isNotFound());
        detalle("malo").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------- Gantt (HU-REC-08)

    @Test
    void calendarioAgrupaPorTipoYTraeLasSinAsignar() throws Exception {
        String asignada = crearReserva(huesped("asignada@correo.test"), "Doble Superior", "2026-10-10", "2026-10-12",
                1, habitacionId("103"));
        String sinAsignar = crearReserva(huesped("sin@correo.test"), "Suite Volcán", "2026-10-11", "2026-10-13", 1,
                null);
        String cancelada = crearReserva(huesped("cancelada@correo.test"), "Doble Superior", "2026-10-11",
                "2026-10-12", 1, null);
        jdbc.update("UPDATE reservas SET estado = 'CANCELADA' WHERE codigo = ?", cancelada);
        String finalizada = crearReserva(huesped("fin@correo.test"), "Estándar Jardín", "2026-10-06", "2026-10-08",
                1, habitacionId("101"));
        jdbc.update("UPDATE reservas SET estado = 'FINALIZADA' WHERE codigo = ?", finalizada);
        String fuera = crearReserva(huesped("fuera@correo.test"), "Doble Superior", "2026-11-20", "2026-11-22", 1,
                null);
        jdbc.update("UPDATE habitaciones SET estado = 'INACTIVO' WHERE numero = '303'");

        mvc.perform(get("/api/v1/reservas/calendario").header("Authorization", recepcion)
                        .param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-01"))
                .andExpect(jsonPath("$.hasta").value("2026-10-31"))
                .andExpect(jsonPath("$.grupos", hasSize(4)))
                .andExpect(jsonPath("$.grupos[*].habitaciones[*].numero", hasSize(11)))
                .andExpect(jsonPath("$.grupos[*].habitaciones[*].numero", not(hasItem("303"))))
                .andExpect(jsonPath("$.grupos[?(@.tipoHabitacion.nombre == 'Doble Superior')].habitaciones[*].numero",
                        contains("103", "104", "105", "203")))
                .andExpect(jsonPath("$.reservas[*].codigo", containsInAnyOrder(asignada, sinAsignar, finalizada)))
                .andExpect(jsonPath("$.reservas[?(@.habitacionId == null)].codigo", contains(sinAsignar)))
                .andExpect(jsonPath("$.reservas[?(@.codigo == '" + asignada + "')].huespedPrincipal")
                        .value("Huésped asignada"))
                .andExpect(jsonPath("$.reservas[*].codigo", not(hasItem(fuera))));
        mvc.perform(get("/api/v1/reservas/calendario").header("Authorization", recepcion)
                        .param("desde", "2026-10-31").param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reservas/calendario").header("Authorization", recepcion))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ permisos

    @Test
    void soloRecepcionUsaEstasRutas() throws Exception {
        String codigo = crearReserva(huesped("permisos@correo.test"), "Doble Superior", "2026-10-15", "2026-10-17",
                2, null);
        for (String correo : new String[] {"sofia.morales@villaserena.test", "rodrigo.aju@villaserena.test",
                "luis.garcia@villaserena.test"}) {
            String otro = token(correo);
            mvc.perform(get("/api/v1/huespedes").param("q", "ana").header("Authorization", otro))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/huespedes").header("Authorization", otro)
                            .contentType(MediaType.APPLICATION_JSON).content(huespedJson("x", "x@correo.test", "1")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reservas").header("Authorization", otro)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reservas/{c}", codigo).header("Authorization", otro))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reservas/calendario").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                            .header("Authorization", otro))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/reservas/{c}/huespedes-adicionales", codigo).header("Authorization", otro)
                            .contentType(MediaType.APPLICATION_JSON).content(adicionalJson("Luis")))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM huespedes_adicionales", Integer.class)).isZero();
    }

    // ------------------------------------------------------------ apoyo

    private ResultActions registrar(String nombre, String correo, String documento) throws Exception {
        return mvc.perform(post("/api/v1/huespedes").header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(huespedJson(nombre, correo, documento)));
    }

    private long registrarId(String nombre, String correo, String documento) throws Exception {
        return ((Number) JsonPath.read(registrar(nombre, correo, documento).andReturn().getResponse()
                .getContentAsString(), "$.huesped.id")).longValue();
    }

    private long huesped(String correo) throws Exception {
        return registrarId("Huésped " + correo.substring(0, correo.indexOf('@')), correo, "1234567890101");
    }

    private static String huespedJson(String nombre, String correo, String documento) {
        return """
                {"nombreCompleto": "%s", "correo": "%s", "telefono": "+502 5555 0000",
                 "nacionalidad": "Guatemalteca", "tipoDocumento": "DPI", "numeroDocumento": "%s"}"""
                .formatted(nombre, correo, documento);
    }

    private ResultActions buscarHuesped(String q) throws Exception {
        return mvc.perform(get("/api/v1/huespedes").param("q", q).header("Authorization", recepcion));
    }

    private ResultActions adicional(String codigo, String nombre) throws Exception {
        return mvc.perform(post("/api/v1/reservas/{c}/huespedes-adicionales", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content(adicionalJson(nombre)));
    }

    private static String adicionalJson(String nombre) {
        return """
                {"nombreCompleto": "%s", "tipoDocumento": "PASAPORTE", "numeroDocumento": "X1234567",
                 "nacionalidad": "Mexicana"}""".formatted(nombre);
    }

    private ResultActions buscar(String filtros) throws Exception {
        return mvc.perform(get("/api/v1/reservas?" + filtros).header("Authorization", recepcion));
    }

    private static ResultMatcher codigos(String... codigos) {
        return jsonPath("$.contenido[*].codigo", contains(codigos));
    }

    private ResultActions detalle(String codigo) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mvc.perform(get("/api/v1/reservas/{c}", codigo).header("Authorization", recepcion));
    }

    private String crearReserva(long huesped, String tipo, String entrada, String salida, int personas,
            Long habitacion) throws Exception {
        String json = """
                {"huespedId": %d, "tipoHabitacionId": %d, "entrada": "%s", "salida": "%s",
                 "numeroHuespedes": %d, "habitacionId": %s}"""
                .formatted(huesped, tipoId(tipo), entrada, salida, personas, habitacion);
        String respuesta = mvc.perform(post("/api/v1/reservas").header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.codigo");
    }

    /** Reserva del canal externo insertada directamente (sin pasar por la API del canal). */
    private String reservaDeCanal(String nombre, String identificador, String entrada, String salida) {
        long huesped = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES (?, ?, '+1 555 0100', 'Alemana', 'PASAPORTE', 'C4R7T2X91') RETURNING id""",
                Long.class, nombre, identificador.toLowerCase() + "@canal.test");
        jdbc.update("""
                INSERT INTO reservas (codigo, huesped_id, tipo_habitacion_id, fecha_entrada, fecha_salida,
                                      numero_huespedes, estado, canal, identificador_externo, total)
                VALUES ('VS-CANAL1', ?, ?, ?::date, ?::date, 1, 'CONFIRMADA', 'BOOKING', ?, 1500.00)""",
                huesped, tipoId("Doble Superior"), entrada, salida, identificador);
        return "VS-CANAL1";
    }

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
