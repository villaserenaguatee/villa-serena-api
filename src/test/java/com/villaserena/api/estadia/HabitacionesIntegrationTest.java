package com.villaserena.api.estadia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

import jakarta.persistence.EntityManager;

/**
 * Pruebas de OBJ-2C: estado de las habitaciones, marcar sucia y asignar habitación.
 * "Hoy" es el lunes 5 de octubre de 2026 (RelojFijoConfig).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class HabitacionesIntegrationTest {

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
    long huespedId;

    @BeforeEach
    void preparar() {
        recepcion = token("ana.perez@villaserena.test");
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Marta Habitación', 'marta.hab@correo.test', '+502 5555 3333', 'Guatemalteca', 'DPI', '3333333330101')
                RETURNING id""", Long.class);
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // -------------------------------------------------------- estado (HU-REC-10)

    @Test
    void listaLasHabitacionesActivasConSuEstado() throws Exception {
        jdbc.update("UPDATE habitaciones SET estado = 'INACTIVO' WHERE numero = '303'");
        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[0].numero").value("101"))
                .andExpect(jsonPath("$[0].piso").value(1))
                .andExpect(jsonPath("$[0].tipoHabitacion.nombre").value("Estándar Jardín"))
                .andExpect(jsonPath("$[0].ocupacion").value("LIBRE"))
                .andExpect(jsonPath("$[0].condicion").value("LIMPIA"))
                .andExpect(jsonPath("$[0].llegaHoy").value(false))
                .andExpect(jsonPath("$[0].saleHoy").value(false))
                .andExpect(jsonPath("$[0].incidenciaPendiente").value(false))
                .andExpect(jsonPath("$[0].incidenciaBloqueante").value(is((Object) null)))
                .andExpect(jsonPath("$[*].numero", not(hasItem("303"))));
    }

    @Test
    void losFiltrosSeCombinan() throws Exception {
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE numero IN ('201', '104')");
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '202'");
        listar("?piso=2").andExpect(jsonPath("$[*].numero", contains("201", "202", "203", "204")));
        listar("?condicion=SUCIA").andExpect(jsonPath("$[*].numero", contains("104", "201")));
        listar("?ocupacion=OCUPADA").andExpect(jsonPath("$[*].numero", contains("202")));
        listar("?tipoHabitacionId=" + tipoId("Suite Volcán") + "&piso=2&condicion=LIMPIA")
                .andExpect(jsonPath("$[*].numero", contains("202")));
        listar("?piso=9").andExpect(jsonPath("$", hasSize(0)));
        listar("?condicion=ROTA").andExpect(status().isBadRequest());
    }

    @Test
    void calculaLosIndicadoresConLaFechaDeHoy() throws Exception {
        // Llega hoy: reserva CONFIRMADA con entrada hoy y habitación asignada.
        crear(tipoId("Doble Superior"), "2026-10-05", "2026-10-07", habitacionId("103"));
        // Sale hoy: reserva EN_ESTADIA con salida hoy.
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-06", "2026-10-08", habitacionId("104")));
        jdbc.update("""
                UPDATE reservas SET estado = 'EN_ESTADIA', fecha_entrada = '2026-10-03', fecha_salida = '2026-10-05'
                WHERE codigo = ?""", codigo);
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '104'");
        // Incidencia pendiente: ocupada con una incidencia que impide su uso.
        incidencia("104", true, "Aire acondicionado no enfría.");
        // Incidencia sin impedir el uso: no cuenta.
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '105'");
        incidencia("105", false, "Foco fundido.");

        listar("?piso=1")
                .andExpect(jsonPath("$[?(@.numero == '103')].llegaHoy").value(true))
                .andExpect(jsonPath("$[?(@.numero == '103')].saleHoy").value(false))
                .andExpect(jsonPath("$[?(@.numero == '104')].saleHoy").value(true))
                .andExpect(jsonPath("$[?(@.numero == '104')].incidenciaPendiente").value(true))
                .andExpect(jsonPath("$[?(@.numero == '105')].incidenciaPendiente").value(false));

        // Al día siguiente ya no llega ni sale hoy.
        reloj.fijar(Instant.parse("2026-10-06T18:00:00Z"));
        listar("?piso=1")
                .andExpect(jsonPath("$[?(@.numero == '103')].llegaHoy").value(false))
                .andExpect(jsonPath("$[?(@.numero == '104')].saleHoy").value(false));
    }

    @Test
    void fueraDeServicioMuestraLaIncidenciaQueLaBloquea() throws Exception {
        jdbc.update("UPDATE habitaciones SET condicion = 'FUERA_DE_SERVICIO' WHERE numero = '201'");
        long id = incidencia("201", true, "Fuga de agua en el baño.");
        incidencia("201", false, "Cortina rota.");

        listar("?condicion=FUERA_DE_SERVICIO")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].incidenciaBloqueante.id").value(id))
                .andExpect(jsonPath("$[0].incidenciaBloqueante.descripcion").value("Fuga de agua en el baño."))
                .andExpect(jsonPath("$[0].incidenciaBloqueante.estado").value("REPORTADA"))
                .andExpect(jsonPath("$[0].incidenciaBloqueante.reportadaEn").exists());
        listar("?condicion=LIMPIA").andExpect(jsonPath("$[*].incidenciaBloqueante", everyItem(is((Object) null))));
    }

    // -------------------------------------------------- marcar sucia (HU-REC-11)

    @Test
    void marcarSuciaUnaLibreYLimpiaLaDejaSuciaYLoRegistra() throws Exception {
        long id = habitacionId("102");
        marcarSucia(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("102"))
                .andExpect(jsonPath("$.condicion").value("SUCIA"));
        assertThat(jdbc.queryForObject("SELECT condicion FROM habitaciones WHERE id = ?", String.class, id))
                .isEqualTo("SUCIA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM historial_estados h JOIN empleados e ON e.id = h.id_responsable
                WHERE h.tipo_entidad = 'HABITACION_CONDICION' AND h.id_entidad = ?
                  AND h.estado_anterior = 'LIMPIA' AND h.estado_nuevo = 'SUCIA'
                  AND h.tipo_responsable = 'EMPLEADO' AND e.correo = 'ana.perez@villaserena.test'""",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void marcarSuciaEnCualquierOtroEstadoDa409() throws Exception {
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '101'");
        marcarSucia(habitacionId("101"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje", containsString("ocupada y limpia")));

        long sucia = habitacionId("102");
        marcarSucia(sucia).andExpect(status().isOk());
        marcarSucia(sucia).andExpect(status().isConflict());

        jdbc.update("UPDATE habitaciones SET condicion = 'FUERA_DE_SERVICIO' WHERE numero = '201'");
        marcarSucia(habitacionId("201")).andExpect(status().isConflict());

        marcarSucia(999_999).andExpect(status().isNotFound());
    }

    // ------------------------------------------------- asignar (HU-REC-07)

    @Test
    void asignaYCambiaLaHabitacionAntesDelCheckin() throws Exception {
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", null));
        asignar(codigo, habitacionId("103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.habitacion.numero").value("103"));
        asignar(codigo, habitacionId("105"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habitacion.numero").value("105"));
        assertThat(jdbc.queryForObject("""
                SELECT e.correo FROM reservas r JOIN empleados e ON e.id = r.habitacion_asignada_por_empleado_id
                WHERE r.codigo = ?""", String.class, codigo)).isEqualTo("ana.perez@villaserena.test");
    }

    @Test
    void asignarUnaHabitacionOcupadaEnEsasFechasDa409() throws Exception {
        crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-18", habitacionId("103"));
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-17", "2026-10-19", null));
        asignar(codigo, habitacionId("103"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_OCUPADA"));
        // La salida de una y la entrada de otra pueden ser el mismo día.
        String siguiente = codigoDe(crear(tipoId("Doble Superior"), "2026-10-18", "2026-10-20", null));
        asignar(siguiente, habitacionId("103")).andExpect(status().isOk());
    }

    @Test
    void asignarRespetaTipoEstadoYFueraDeServicio() throws Exception {
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", null));
        asignar(codigo, habitacionId("101")).andExpect(status().isBadRequest());

        jdbc.update("UPDATE habitaciones SET condicion = 'FUERA_DE_SERVICIO' WHERE numero = '104'");
        asignar(codigo, habitacionId("104"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_NO_DISPONIBLE"));

        // No exige que esté limpia.
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE numero = '105'");
        asignar(codigo, habitacionId("105")).andExpect(status().isOk());

        jdbc.update("UPDATE reservas SET estado = 'EN_ESTADIA' WHERE codigo = ?", codigo);
        entityManager.clear();
        asignar(codigo, habitacionId("103"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        asignar("VS-NOEXIS", habitacionId("103")).andExpect(status().isNotFound());
        asignar("malo", habitacionId("103")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/reservas/{c}/habitacion", codigo).header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disponiblesExcluyeTraslapesYFueraDeServicio() throws Exception {
        crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", habitacionId("103"));
        jdbc.update("UPDATE habitaciones SET condicion = 'FUERA_DE_SERVICIO' WHERE numero = '104'");
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE numero = '105'");
        disponibles(tipoId("Doble Superior"), "2026-10-16", "2026-10-18")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].numero", contains("105", "203")))
                .andExpect(jsonPath("$[0].piso").value(1));
        disponibles(tipoId("Doble Superior"), "2026-10-17", "2026-10-18")
                .andExpect(jsonPath("$[*].numero", contains("103", "105", "203")));
        disponibles(tipoId("Doble Superior"), "2026-10-18", "2026-10-18").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/habitaciones/disponibles").header("Authorization", recepcion)
                        .param("tipoHabitacionId", "1"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ permisos

    @Test
    void soloRecepcionUsaEstasRutas() throws Exception {
        String codigo = codigoDe(crear(tipoId("Doble Superior"), "2026-10-15", "2026-10-17", null));
        for (String correo : new String[] {"sofia.morales@villaserena.test", "rodrigo.aju@villaserena.test",
                "luis.garcia@villaserena.test"}) {
            String otro = token(correo);
            mvc.perform(get("/api/v1/habitaciones").header("Authorization", otro)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/habitaciones/disponibles").header("Authorization", otro)
                            .param("tipoHabitacionId", "1").param("entrada", "2026-10-15")
                            .param("salida", "2026-10-17"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/habitaciones/{id}/marcar-sucia", habitacionId("102"))
                            .header("Authorization", otro))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/reservas/{c}/habitacion", codigo).header("Authorization", otro)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\": 1}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/habitaciones")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT condicion FROM habitaciones WHERE numero = '102'", String.class))
                .isEqualTo("LIMPIA");
    }

    // ------------------------------------------------------------ apoyo

    private ResultActions listar(String filtros) throws Exception {
        return mvc.perform(get("/api/v1/habitaciones" + filtros).header("Authorization", recepcion));
    }

    private ResultActions marcarSucia(long id) throws Exception {
        return mvc.perform(post("/api/v1/habitaciones/{id}/marcar-sucia", id).header("Authorization", recepcion));
    }

    private ResultActions asignar(String codigo, long habitacion) throws Exception {
        return mvc.perform(put("/api/v1/reservas/{c}/habitacion", codigo).header("Authorization", recepcion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\": " + habitacion + "}"));
    }

    private ResultActions disponibles(long tipo, String entrada, String salida) throws Exception {
        return mvc.perform(get("/api/v1/habitaciones/disponibles").header("Authorization", recepcion)
                .param("tipoHabitacionId", String.valueOf(tipo)).param("entrada", entrada).param("salida", salida));
    }

    private ResultActions crear(long tipo, String entrada, String salida, Long habitacion) throws Exception {
        String json = """
                {"huespedId": %d, "tipoHabitacionId": %d, "entrada": "%s", "salida": "%s",
                 "numeroHuespedes": 1, "habitacionId": %s}"""
                .formatted(huespedId, tipo, entrada, salida, habitacion);
        return mvc.perform(post("/api/v1/reservas").header("Authorization", recepcion)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private String codigoDe(ResultActions creada) throws Exception {
        return JsonPath.read(creada.andReturn().getResponse().getContentAsString(), "$.codigo");
    }

    private long incidencia(String numero, boolean impideUso, String descripcion) {
        return jdbc.queryForObject("""
                INSERT INTO incidencias (habitacion_id, descripcion, impide_uso, reportada_por_empleado_id)
                SELECT h.id, ?, ?, e.id FROM habitaciones h, empleados e
                WHERE h.numero = ? AND e.correo = 'luis.garcia@villaserena.test'
                RETURNING id""", Long.class, descripcion, impideUso, numero);
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
