package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pruebas de las incidencias y sus fotos (OBJ-3B-1, parte B). Luis es de LIMPIEZA,
 * Marta de MANTENIMIENTO y Pedro de AMBAS; el bucket privado está en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class IncidenciasIntegrationTest {

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

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
    AlmacenEnMemoria.Almacen almacen;

    @MockitoBean
    PasarelaStripe stripe;

    String recepcion;
    String luis;
    String marta;
    String pedro;
    String admin;

    @BeforeEach
    void preparar() {
        almacen.objetos().clear();
        recepcion = token("ana.perez@villaserena.test");
        luis = token("luis.garcia@villaserena.test");
        marta = token("marta.xicara@villaserena.test");
        pedro = token("pedro.coy@villaserena.test");
        admin = token("sofia.morales@villaserena.test");
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ------------------------------------------------------------- fotos

    @Test
    void subeFotosJpgOPngAlBucketPrivado() throws Exception {
        String clave = clave(subir(luis, "dano.jpg", JPG).andExpect(status().isCreated()));
        assertThat(clave).startsWith("incidencias/").endsWith(".jpg");
        assertThat(almacen.objetos()).containsKey(clave);
        subir(recepcion, "dano.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave", startsWith("incidencias/")));
    }

    @Test
    void rechazaFotosDeMasDe5MBOQueNoSonImagen() throws Exception {
        byte[] seisMegas = Arrays.copyOf(JPG, 6 * 1024 * 1024);
        subir(luis, "grande.jpg", seisMegas)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
        // Un PDF renombrado como .jpg: se valida el contenido real.
        subir(luis, "factura.jpg", "%PDF-1.7 documento".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_PERMITIDO"));
        byte[] exacto = Arrays.copyOf(JPG, 5 * 1024 * 1024);
        subir(luis, "limite.jpg", exacto).andExpect(status().isCreated());
        assertThat(almacen.objetos()).hasSize(1);
    }

    @Test
    void soloRecepcionYMylSubenFotosDeIncidencias() throws Exception {
        subir(admin, "dano.jpg", JPG).andExpect(status().isForbidden());
        subir(token("rodrigo.aju@villaserena.test"), "dano.jpg", JPG).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", "hotel.jpg", "image/jpeg", JPG))
                        .param("uso", "HOTEL").header("Authorization", luis))
                .andExpect(status().isForbidden());
        assertThat(almacen.objetos()).isEmpty();
    }

    // ------------------------------------------------------------ reportar

    @Test
    void danoQueImpideElUsoDejaLaHabitacionLibreFueraDeServicio() throws Exception {
        String foto = clave(subir(recepcion, "fuga.jpg", JPG));
        long id = idDe(reportar(recepcion, "201", "Fuga de agua en el baño.", true, foto)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("REPORTADA"))
                .andExpect(jsonPath("$.habitacionId").value(habitacionId("201")))
                .andExpect(jsonPath("$.reportadaEn").exists()));
        assertThat(condicion("201")).isEqualTo("FUERA_DE_SERVICIO");

        // Recepción ve la incidencia que la bloquea.
        mvc.perform(get("/api/v1/habitaciones").param("condicion", "FUERA_DE_SERVICIO")
                        .header("Authorization", recepcion))
                .andExpect(jsonPath("$[0].numero").value("201"))
                .andExpect(jsonPath("$[0].incidenciaBloqueante.id").value(id));
        // Mantenimiento ve la foto con URL firmada.
        detalle(id, marta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(AlmacenEnMemoria.URL_FIRMADA + foto))
                .andExpect(jsonPath("$.reportadaPor.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("REPORTADA"));
    }

    @Test
    void enHabitacionOcupadaSoloQuedaElIndicadorPendiente() throws Exception {
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '202'");
        reportar(luis, "202", "No enciende el aire acondicionado.", true, null).andExpect(status().isCreated());
        assertThat(condicion("202")).isEqualTo("LIMPIA");
        mvc.perform(get("/api/v1/habitaciones").param("piso", "2").header("Authorization", recepcion))
                .andExpect(jsonPath("$[?(@.numero == '202')].incidenciaPendiente").value(true));
    }

    @Test
    void danoQueNoImpideElUsoNoCambiaLaHabitacion() throws Exception {
        reportar(pedro, "101", "Foco fundido.", false, null).andExpect(status().isCreated());
        assertThat(condicion("101")).isEqualTo("LIMPIA");
    }

    @Test
    void siLaEstabanLimpiandoDejaDeEstarACargo() throws Exception {
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE numero = '102'");
        mvc.perform(post("/api/v1/limpieza/habitaciones/{id}/iniciar", habitacionId("102"))
                .header("Authorization", luis)).andExpect(status().isOk());
        reportar(luis, "102", "Se rompió el lavamanos.", true, null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT condicion || '/' || coalesce(limpieza_empleado_id::text, '-') "
                + "FROM habitaciones WHERE numero = '102'", String.class)).isEqualTo("FUERA_DE_SERVICIO/-");
    }

    @Test
    void validaLosDatosYLaFoto() throws Exception {
        mvc.perform(post("/api/v1/incidencias").header("Authorization", luis)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\": 1, \"impideUso\": true}"))
                .andExpect(status().isBadRequest());
        reportar(luis, "101", "Daño.", true, "incidencias/no-subida.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("fotoClave"));
        reportar(luis, "101", "Daño.", true, "../hotel/fachada.jpg").andExpect(status().isBadRequest());
        String foto = clave(subir(luis, "dano.jpg", JPG));
        reportar(luis, "101", "Daño.", false, foto).andExpect(status().isCreated());
        // La misma foto no se reutiliza en otra incidencia.
        reportar(luis, "102", "Otro daño.", false, foto).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/incidencias").header("Authorization", luis)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"habitacionId\": 999999, \"descripcion\": \"x\", \"impideUso\": false}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------ consultar y tomar

    @Test
    void mantenimientoVeSuColaActivaYElAdministradorTodo() throws Exception {
        long vieja = incidenciaDel("101", "2026-10-01T10:00:00Z", "RESUELTA");
        long media = incidenciaDel("102", "2026-10-02T10:00:00Z", "REPORTADA");
        long nueva = incidenciaDel("103", "2026-10-03T10:00:00Z", "REPORTADA");

        listar(marta, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) media, (int) nueva)))
                .andExpect(jsonPath("$[0].habitacion.numero").value("102"))
                .andExpect(jsonPath("$[0].tecnicoACargo").value(is((Object) null)));
        listar(marta, "?estado=RESUELTA").andExpect(status().isForbidden());
        listar(admin, "")
                .andExpect(jsonPath("$[*].id", contains((int) nueva, (int) media, (int) vieja)));
        listar(admin, "?estado=RESUELTA").andExpect(jsonPath("$[*].id", contains((int) vieja)));
        listar(admin, "?habitacionId=" + habitacionId("102")).andExpect(jsonPath("$[*].id", contains((int) media)));
        detalle(vieja, admin).andExpect(status().isOk());
    }

    @Test
    void siOtroTecnicoYaLaTomoResponde409ConSuNombre() throws Exception {
        long id = idDe(reportar(recepcion, "201", "Fuga.", true, null));
        tomar(id, marta)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.tecnicoACargo.nombre").value("Marta Xicará"));
        tomar(id, pedro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INCIDENCIA_TOMADA"))
                .andExpect(jsonPath("$.mensaje", containsString("Marta Xicará")));
        resolver(id, pedro, "Arreglado.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_ESTA_A_TU_CARGO"));
        listar(admin, "?tecnicoId=" + empleadoId("marta.xicara@villaserena.test"))
                .andExpect(jsonPath("$[*].id", contains((int) id)));
    }

    // ------------------------------------------------------------ resolver

    @Test
    void alResolverLaHabitacionFueraDeServicioPasaASucia() throws Exception {
        long id = idDe(reportar(recepcion, "201", "Fuga.", true, null));
        tomar(id, marta).andExpect(status().isOk());
        mvc.perform(post("/api/v1/incidencias/{id}/resolver", id).header("Authorization", marta)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"solucion\": \"  \"}"))
                .andExpect(status().isBadRequest());
        resolver(id, marta, "Se cambió el empaque de la llave.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"))
                .andExpect(jsonPath("$.solucion").value("Se cambió el empaque de la llave."))
                .andExpect(jsonPath("$.resueltaEn").exists())
                .andExpect(jsonPath("$.historial[*].estadoNuevo", contains("REPORTADA", "EN_PROCESO", "RESUELTA")));
        assertThat(condicion("201")).isEqualTo("SUCIA");
        // Ya no se modifica.
        resolver(id, marta, "Otra solución.").andExpect(status().isConflict());
        tomar(id, pedro).andExpect(status().isConflict());
    }

    @Test
    void conOtraIncidenciaPendienteSigueFueraDeServicio() throws Exception {
        long primera = idDe(reportar(recepcion, "201", "Fuga.", true, null));
        long segunda = idDe(reportar(luis, "201", "Ventana rota.", true, null));
        tomar(primera, marta).andExpect(status().isOk());
        resolver(primera, marta, "Fuga reparada.").andExpect(status().isOk());
        assertThat(condicion("201")).isEqualTo("FUERA_DE_SERVICIO");

        tomar(segunda, pedro).andExpect(status().isOk());
        resolver(segunda, pedro, "Vidrio cambiado.").andExpect(status().isOk());
        assertThat(condicion("201")).isEqualTo("SUCIA");
    }

    @Test
    void enHabitacionOcupadaResolverQuitaElIndicador() throws Exception {
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE numero = '202'");
        long id = idDe(reportar(luis, "202", "Sin agua caliente.", true, null));
        tomar(id, marta).andExpect(status().isOk());
        resolver(id, marta, "Se reinició el calentador.").andExpect(status().isOk());
        assertThat(condicion("202")).isEqualTo("LIMPIA");
        mvc.perform(get("/api/v1/habitaciones").param("piso", "2").header("Authorization", recepcion))
                .andExpect(jsonPath("$[?(@.numero == '202')].incidenciaPendiente").value(false));
    }

    // ------------------------------------------------------------ permisos

    @Test
    void permisosPorRolYArea() throws Exception {
        long id = idDe(reportar(luis, "201", "Fuga.", true, null));
        // Limpieza no ve ni toma incidencias; Recepción tampoco.
        for (String otro : new String[] {luis, recepcion}) {
            listar(otro, "").andExpect(status().isForbidden());
            detalle(id, otro).andExpect(status().isForbidden());
            tomar(id, otro).andExpect(status().isForbidden());
        }
        // El Administrador solo consulta.
        tomar(id, admin).andExpect(status().isForbidden());
        resolver(id, admin, "x").andExpect(status().isForbidden());
        reportar(admin, "101", "x", false, null).andExpect(status().isForbidden());
        reportar(token("rodrigo.aju@villaserena.test"), "101", "x", false, null).andExpect(status().isForbidden());
        // Mantenimiento y AMBAS sí.
        listar(marta, "").andExpect(status().isOk());
        listar(pedro, "").andExpect(status().isOk());
    }

    // ------------------------------------------------------------ apoyo

    private ResultActions subir(String token, String nombre, byte[] contenido) throws Exception {
        return mvc.perform(multipart("/api/v1/archivos/imagenes")
                .file(new MockMultipartFile("archivo", nombre, "image/jpeg", contenido))
                .param("uso", "INCIDENCIA").header("Authorization", token));
    }

    private ResultActions reportar(String token, String numero, String descripcion, boolean impideUso,
            String foto) throws Exception {
        String json = """
                {"habitacionId": %d, "descripcion": "%s", "impideUso": %s, "fotoClave": %s}"""
                .formatted(habitacionId(numero), descripcion, impideUso, foto == null ? "null" : "\"" + foto + "\"");
        return mvc.perform(post("/api/v1/incidencias").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions listar(String token, String filtros) throws Exception {
        return mvc.perform(get("/api/v1/incidencias" + filtros).header("Authorization", token));
    }

    private ResultActions detalle(long id, String token) throws Exception {
        return mvc.perform(get("/api/v1/incidencias/{id}", id).header("Authorization", token));
    }

    private ResultActions tomar(long id, String token) throws Exception {
        return mvc.perform(post("/api/v1/incidencias/{id}/tomar", id).header("Authorization", token));
    }

    private ResultActions resolver(long id, String token, String solucion) throws Exception {
        return mvc.perform(post("/api/v1/incidencias/{id}/resolver", id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"solucion\": \"" + solucion + "\"}"));
    }

    /** Incidencia insertada directamente, reportada en la fecha indicada. */
    private long incidenciaDel(String numero, String fecha, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO incidencias (habitacion_id, descripcion, impide_uso, estado, reportada_por_empleado_id,
                                         tecnico_id, solucion, creado_en)
                SELECT h.id, 'Daño de prueba', FALSE, ?, e.id, CASE WHEN ? = 'RESUELTA' THEN e.id END,
                       CASE WHEN ? = 'RESUELTA' THEN 'Listo' END, ?::timestamptz
                FROM habitaciones h, empleados e
                WHERE h.numero = ? AND e.correo = 'marta.xicara@villaserena.test'
                RETURNING id""", Long.class, estado, estado, estado, fecha, numero);
    }

    private static String clave(ResultActions subida) throws Exception {
        return JsonPath.read(subida.andReturn().getResponse().getContentAsString(), "$.clave");
    }

    private static long idDe(ResultActions creada) throws Exception {
        return ((Number) JsonPath.read(creada.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String condicion(String numero) {
        return jdbc.queryForObject("SELECT condicion FROM habitaciones WHERE numero = ?", String.class, numero);
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

    private long empleadoId(String correo) {
        return jdbc.queryForObject("SELECT id FROM empleados WHERE correo = ?", Long.class, correo);
    }

    private long habitacionId(String numero) {
        return jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = ?", Long.class, numero);
    }
}
