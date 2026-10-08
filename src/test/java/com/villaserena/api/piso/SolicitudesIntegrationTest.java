package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Supplier;

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
import com.villaserena.api.auth.dto.Tokens;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

import jakarta.persistence.EntityManager;

/**
 * Pruebas de las solicitudes del huésped (OBJ-3B-2). La huésped está en estadía en la
 * habitación 101 desde hoy (5 de octubre de 2026). Luis es de LIMPIEZA, Pedro de
 * AMBAS y Marta de MANTENIMIENTO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class SolicitudesIntegrationTest {

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

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    PasarelaStripe stripe;

    long huespedId;
    String codigo;
    String huesped;
    String luis;
    String pedro;

    @BeforeEach
    void preparar() {
        long recepcionista = empleadoId("ana.perez@villaserena.test");
        huespedId = huesped("Carla Estadía", "carla@correo.test");
        long habitacion = habitacionId("101");
        long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                habitacion);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacion, recepcionista));
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionista);
        // El check-in cambia la reserva con JPA; las solicitudes la leen con SQL.
        entityManager.flush();
        codigo = reserva.getCodigo();
        huesped = token(() -> tokens.emitirParaHuesped(huespedId));
        luis = empleado("luis.garcia@villaserena.test");
        pedro = empleado("pedro.coy@villaserena.test");
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ------------------------------------------------------------ huésped

    @Test
    void elCatalogoDeArticulosConSusMaximos() throws Exception {
        mvc.perform(get("/api/v1/app/reservas/{c}/articulos", codigo).header("Authorization", huesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].nombre").value("Toalla de baño"))
                .andExpect(jsonPath("$[0].cantidadMaxima").value(4));
    }

    @Test
    void soloUnaLimpiezaEnCursoPorHabitacion() throws Exception {
        pedirLimpieza("{\"comentario\": \"Después de las 10:00, por favor.\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("LIMPIEZA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.comentario").value("Después de las 10:00, por favor."))
                .andExpect(jsonPath("$.articulos", hasSize(0)));
        pedirLimpieza(null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMPIEZA_YA_SOLICITADA"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Ya hay una solicitud de limpieza en curso para tu habitación."));
        // No cambia la condición de la habitación.
        assertThat(jdbc.queryForObject("SELECT condicion FROM habitaciones WHERE numero = '101'", String.class))
                .isEqualTo("LIMPIA");
    }

    @Test
    void despuesDeAtendidaSePuedePedirOtraLimpieza() throws Exception {
        long id = idDe(pedirLimpieza(null));
        accion(id, "tomar", luis).andExpect(status().isOk());
        accion(id, "atender", luis).andExpect(status().isOk());
        pedirLimpieza(null).andExpect(status().isCreated());
    }

    @Test
    void articulosRespetaLosMaximos() throws Exception {
        long toalla = articuloId("Toalla de baño");
        long almohada = articuloId("Almohada");
        pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 2}, {\"articuloId\": %d, \"cantidad\": 1}]"
                .formatted(toalla, almohada))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ARTICULOS"))
                .andExpect(jsonPath("$.articulos[*].nombre", contains("Toalla de baño", "Almohada")))
                .andExpect(jsonPath("$.articulos[0].cantidad").value(2));

        pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 3}]".formatted(almohada))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("articulos[0].cantidad"))
                .andExpect(jsonPath("$.detalles[0].mensaje").value("Puedes pedir hasta 2."));
        pedirArticulos("[{\"articuloId\": 999999, \"cantidad\": 1}]").andExpect(status().isBadRequest());
        pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 1}, {\"articuloId\": %d, \"cantidad\": 1}]"
                .formatted(toalla, toalla)).andExpect(status().isBadRequest());
        pedirArticulos("[]").andExpect(status().isBadRequest());
        pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 0}]".formatted(toalla)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes", Integer.class)).isEqualTo(1);
    }

    @Test
    void soloDuranteLaEstadiaYSoloSuReserva() throws Exception {
        String ajeno = token(() -> tokens.emitirParaHuesped(huesped("Otro", "otro@correo.test")));
        mvc.perform(get("/api/v1/app/reservas/{c}/articulos", codigo).header("Authorization", ajeno))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/app/reservas/{c}/solicitudes/limpieza", codigo).header("Authorization", ajeno))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/app/reservas/{c}/solicitudes", codigo).header("Authorization", ajeno))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE reservas SET estado = 'FINALIZADA' WHERE codigo = ?", codigo);
        pedirLimpieza(null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        mvc.perform(get("/api/v1/app/reservas/{c}/articulos", codigo).header("Authorization", huesped))
                .andExpect(status().isConflict());
        // Después del check-out todavía puede ver sus solicitudes.
        mvc.perform(get("/api/v1/app/reservas/{c}/solicitudes", codigo).header("Authorization", huesped))
                .andExpect(status().isOk());
    }

    @Test
    void veSusSolicitudesYCancelaSoloLasPendientes() throws Exception {
        long limpieza = idDe(pedirLimpieza(null));
        long articulos = idDe(pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 1}]"
                .formatted(articuloId("Jabón"))));
        accion(limpieza, "tomar", luis).andExpect(status().isOk());

        mvc.perform(get("/api/v1/app/reservas/{c}/solicitudes", codigo).header("Authorization", huesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) articulos, (int) limpieza)))
                .andExpect(jsonPath("$[1].estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$[0].empleadoACargo").doesNotExist());

        cancelar(articulos).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        cancelar(articulos)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("cancelada")));
        cancelar(limpieza)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("en proceso")));
        cancelar(999_999).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("""
                SELECT coalesce(estado_anterior, '-') || '>' || estado_nuevo || ' ' || tipo_responsable
                FROM historial_estados WHERE tipo_entidad = 'SOLICITUD' AND id_entidad = ? ORDER BY id""",
                String.class, articulos)).containsExactly("->PENDIENTE HUESPED", "PENDIENTE>CANCELADA HUESPED");
    }

    // ------------------------------------------------------------ Limpieza

    @Test
    void laColaMuestraPendientesYEnProcesoSinDatosPersonales() throws Exception {
        long limpieza = idDe(pedirLimpieza(null));
        long articulos = idDe(pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 2}]"
                .formatted(articuloId("Cobija"))));
        long cancelada = idDe(pedirArticulos("[{\"articuloId\": %d, \"cantidad\": 1}]"
                .formatted(articuloId("Jabón"))));
        cancelar(cancelada).andExpect(status().isOk());
        accion(limpieza, "tomar", pedro).andExpect(status().isOk());

        mvc.perform(get("/api/v1/limpieza/solicitudes").header("Authorization", luis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) limpieza, (int) articulos)))
                .andExpect(jsonPath("$[0].habitacion.numero").value("101"))
                .andExpect(jsonPath("$[0].empleadoACargo.nombre").value("Pedro Coy"))
                .andExpect(jsonPath("$[1].articulos[0].nombre").value("Cobija"))
                .andExpect(jsonPath("$[1].articulos[0].cantidad").value(2))
                .andExpect(jsonPath("$[1].empleadoACargo").value(is((Object) null)))
                .andExpect(jsonPath("$[0].huesped").doesNotExist())
                .andExpect(jsonPath("$[0].codigoReserva").doesNotExist());
    }

    @Test
    void tomarYAtenderConPushAlHuesped() throws Exception {
        jdbc.update("INSERT INTO dispositivos_push (huesped_id, token_expo) VALUES (?, 'ExponentPushToken[a]'), "
                + "(?, 'ExponentPushToken[b]')", huespedId, huespedId);
        long id = idDe(pedirLimpieza(null));
        accion(id, "tomar", luis)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.empleadoACargo.nombre").value("Luis García"));
        accion(id, "tomar", pedro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_TOMADA"))
                .andExpect(jsonPath("$.mensaje", containsString("Luis García")));
        accion(id, "atender", pedro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_ESTA_A_TU_CARGO"));
        accion(id, "atender", luis).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"));
        accion(id, "atender", luis).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/app/reservas/{c}/solicitudes", codigo).header("Authorization", huesped))
                .andExpect(jsonPath("$[0].estado").value("ATENDIDA"));
        assertThat(jdbc.queryForList("""
                SELECT destinatario || ' ' || (payload ->> 'body') || ' ' || (payload -> 'data' ->> 'solicitudId')
                FROM outbox WHERE tipo = 'PUSH_SOLICITUD_ATENDIDA' ORDER BY destinatario""", String.class))
                .containsExactly("ExponentPushToken[a] Tu solicitud fue atendida " + id,
                        "ExponentPushToken[b] Tu solicitud fue atendida " + id);
        // El push no lleva datos personales.
        assertThat(jdbc.queryForObject("SELECT payload::text FROM outbox WHERE tipo = 'PUSH_SOLICITUD_ATENDIDA' "
                + "LIMIT 1", String.class)).doesNotContain("Carla").doesNotContain("carla@");
    }

    @Test
    void unaSolicitudCanceladaNoSeTomaNiSeAtiende() throws Exception {
        long id = idDe(pedirLimpieza(null));
        cancelar(id).andExpect(status().isOk());
        accion(id, "tomar", luis)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_CANCELADA"));
        accion(id, "atender", luis)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_CANCELADA"));
        accion(999_999, "tomar", luis).andExpect(status().isNotFound());
    }

    @Test
    void permisosPorRolYArea() throws Exception {
        long id = idDe(pedirLimpieza(null));
        for (String correo : new String[] {"marta.xicara@villaserena.test", "ana.perez@villaserena.test",
                "sofia.morales@villaserena.test", "rodrigo.aju@villaserena.test"}) {
            String otro = empleado(correo);
            mvc.perform(get("/api/v1/limpieza/solicitudes").header("Authorization", otro))
                    .andExpect(status().isForbidden());
            accion(id, "tomar", otro).andExpect(status().isForbidden());
            // El personal no usa las rutas del huésped.
            mvc.perform(get("/api/v1/app/reservas/{c}/solicitudes", codigo).header("Authorization", otro))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/limpieza/solicitudes").header("Authorization", huesped))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/limpieza/solicitudes").header("Authorization", pedro)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ apoyo

    private ResultActions pedirLimpieza(String cuerpo) throws Exception {
        var peticion = post("/api/v1/app/reservas/{c}/solicitudes/limpieza", codigo).header("Authorization", huesped);
        return mvc.perform(cuerpo == null ? peticion
                : peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions pedirArticulos(String articulos) throws Exception {
        return mvc.perform(post("/api/v1/app/reservas/{c}/solicitudes/articulos", codigo)
                .header("Authorization", huesped).contentType(MediaType.APPLICATION_JSON)
                .content("{\"articulos\": " + articulos + "}"));
    }

    private ResultActions cancelar(long id) throws Exception {
        return mvc.perform(post("/api/v1/app/reservas/{c}/solicitudes/{id}/cancelar", codigo, id)
                .header("Authorization", huesped));
    }

    private ResultActions accion(long id, String accion, String token) throws Exception {
        return mvc.perform(post("/api/v1/limpieza/solicitudes/{id}/" + accion, id).header("Authorization", token));
    }

    private static long idDe(ResultActions creada) throws Exception {
        return ((Number) JsonPath.read(creada.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long huesped(String nombre, String correo) {
        return jdbc.queryForObject("""
                INSERT INTO huespedes
                    (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES (?, ?, '+502 5555 0000', 'Guatemalteca', 'DPI', '1') RETURNING id""", Long.class,
                nombre, correo);
    }

    private String empleado(String correo) {
        return token(() -> tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow()));
    }

    /** El validador de JWT usa la hora real: el token se emite con ella. */
    private String token(Supplier<Tokens> emitir) {
        Instant antes = reloj.instant();
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + emitir.get().accessToken();
        } finally {
            reloj.fijar(antes);
        }
    }

    private long articuloId(String nombre) {
        return jdbc.queryForObject("SELECT id FROM articulos WHERE nombre = ?", Long.class, nombre);
    }

    private long empleadoId(String correo) {
        return jdbc.queryForObject("SELECT id FROM empleados WHERE correo = ?", Long.class, correo);
    }

    private long habitacionId(String numero) {
        return jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = ?", Long.class, numero);
    }
}
