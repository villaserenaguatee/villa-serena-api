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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

/**
 * Pruebas de la limpieza (OBJ-3B-1, parte A). Empleados de prueba: Luis (LIMPIEZA),
 * Marta (MANTENIMIENTO) y Pedro (AMBAS). "Hoy" es el 5 de octubre de 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class LimpiezaIntegrationTest {

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

    @MockitoBean
    PasarelaPagos pasarela;

    String luis;
    String pedro;

    @BeforeEach
    void preparar() {
        luis = token("luis.garcia@villaserena.test");
        pedro = token("pedro.coy@villaserena.test");
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    @Test
    void pendientesPrimeroLasQueTienenLlegadaHoyYLuegoLasMasAntiguas() throws Exception {
        sucia("201", "2026-10-03T10:00:00Z");
        sucia("104", "2026-10-04T10:00:00Z");
        sucia("102", "2026-10-02T10:00:00Z");
        sucia("103", "2026-10-05T08:00:00Z");
        llegadaHoy("103");
        // No son pendientes: ocupada sucia y fuera de servicio.
        jdbc.update("UPDATE rooms SET occupancy = 'OCUPADA', condition = 'SUCIA' WHERE number = '301'");
        jdbc.update("UPDATE rooms SET condition = 'FUERA_DE_SERVICIO' WHERE number = '302'");

        mvc.perform(get("/api/v1/limpieza/habitaciones").header("Authorization", luis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].habitacion.numero", contains("103", "102", "201", "104")))
                .andExpect(jsonPath("$[0].llegadaHoy").value(true))
                .andExpect(jsonPath("$[0].condicion").value("SUCIA"))
                .andExpect(jsonPath("$[1].llegadaHoy").value(false))
                .andExpect(jsonPath("$[1].suciaDesde").value("2026-10-02T04:00:00-06:00"))
                .andExpect(jsonPath("$[1].empleadoACargo").value(is((Object) null)));
    }

    @Test
    void iniciarYTerminarDejanLaHabitacionLimpiaYLoRegistran() throws Exception {
        long id = sucia("102", "2026-10-04T10:00:00Z");
        accion(id, "iniciar", luis)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habitacion.numero").value("102"))
                .andExpect(jsonPath("$.ocupacion").value("LIBRE"))
                .andExpect(jsonPath("$.condicion").value("EN_LIMPIEZA"))
                .andExpect(jsonPath("$.empleadoACargo.nombre").value("Luis García"));
        mvc.perform(get("/api/v1/limpieza/habitaciones").header("Authorization", pedro))
                .andExpect(jsonPath("$[0].condicion").value("EN_LIMPIEZA"))
                .andExpect(jsonPath("$[0].empleadoACargo.nombre").value("Luis García"));

        accion(id, "terminar", luis)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condicion").value("LIMPIA"))
                .andExpect(jsonPath("$.empleadoACargo").value(is((Object) null)));

        // Recepción la ve limpia en el estado de las habitaciones.
        mvc.perform(get("/api/v1/habitaciones").param("condicion", "LIMPIA").param("piso", "1")
                        .header("Authorization", token("ana.perez@villaserena.test")))
                .andExpect(jsonPath("$[?(@.numero == '102')].condicion").value("LIMPIA"));
        mvc.perform(get("/api/v1/limpieza/habitaciones").header("Authorization", luis))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(jdbc.queryForList("""
                SELECT previous_status || '>' || new_status  FROM status_history
                WHERE entity_type = 'HABITACION_CONDICION' AND entity_id = ? AND responsible_type = 'EMPLEADO'
                ORDER BY id""", String.class, id))
                .containsExactly("SUCIA>EN_LIMPIEZA", "EN_LIMPIEZA>LIMPIA");
    }

    @Test
    void siOtroYaLaInicioResponde409ConSuNombre() throws Exception {
        long id = sucia("102", "2026-10-04T10:00:00Z");
        accion(id, "iniciar", luis).andExpect(status().isOk());
        accion(id, "iniciar", pedro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMPIEZA_EN_CURSO"))
                .andExpect(jsonPath("$.mensaje", containsString("Luis García")));
        accion(id, "terminar", pedro)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_ESTA_A_TU_CARGO"));
        accion(id, "interrumpir", pedro).andExpect(status().isConflict());
        accion(id, "iniciar", luis)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LIMPIEZA_YA_INICIADA"));
    }

    @Test
    void interrumpirLaDevuelveASuciaSinResponsable() throws Exception {
        long id = sucia("102", "2026-10-04T10:00:00Z");
        accion(id, "iniciar", luis).andExpect(status().isOk());
        accion(id, "interrumpir", luis)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condicion").value("SUCIA"))
                .andExpect(jsonPath("$.empleadoACargo").value(is((Object) null)));
        // Ahora la puede iniciar otro empleado.
        accion(id, "iniciar", pedro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleadoACargo.nombre").value("Pedro Coy"));
    }

    @Test
    void soloSeLimpiaUnaHabitacionLibreYSucia() throws Exception {
        accion(habitacionId("101"), "iniciar", luis)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        jdbc.update("UPDATE rooms SET occupancy = 'OCUPADA', condition = 'SUCIA' WHERE number = '301'");
        accion(habitacionId("301"), "iniciar", luis).andExpect(status().isConflict());
        accion(habitacionId("101"), "terminar", luis).andExpect(status().isConflict());
        accion(999_999, "iniciar", luis).andExpect(status().isNotFound());
    }

    @Test
    void soloLimpiezaOAmbasUsanLaLimpieza() throws Exception {
        long id = sucia("102", "2026-10-04T10:00:00Z");
        for (String correo : new String[] {"marta.xicara@villaserena.test", "ana.perez@villaserena.test",
                "sofia.morales@villaserena.test", "rodrigo.aju@villaserena.test"}) {
            String otro = token(correo);
            mvc.perform(get("/api/v1/limpieza/habitaciones").header("Authorization", otro))
                    .andExpect(status().isForbidden());
            accion(id, "iniciar", otro).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/limpieza/habitaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/limpieza/habitaciones").header("Authorization", pedro)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT condition AS \"condicion\" FROM rooms WHERE id = ?", String.class, id))
                .isEqualTo("SUCIA");
    }

    // ------------------------------------------------------------ apoyo

    private ResultActions accion(long habitacion, String accion, String token) throws Exception {
        return mvc.perform(post("/api/v1/limpieza/habitaciones/{id}/" + accion, habitacion)
                .header("Authorization", token));
    }

    /** Marca la habitación como SUCIA desde el instante indicado (como lo haría el check-out). */
    private long sucia(String numero, String desde) {
        long id = habitacionId(numero);
        jdbc.update("UPDATE rooms SET condition = 'SUCIA' WHERE id = ?", id);
        jdbc.update("""
                INSERT INTO status_history (entity_type, entity_id, previous_status, new_status,
                                               responsible_type, date)
                VALUES ('HABITACION_CONDICION', ?, 'LIMPIA', 'SUCIA', 'SISTEMA', ?::timestamptz)""", id, desde);
        return id;
    }

    private void llegadaHoy(String numero) {
        long huesped = jdbc.queryForObject("""
                INSERT INTO guests
                    (full_name, email, phone, nationality, document_type, document_number)
                VALUES ('Llega Hoy', 'llega.hoy@correo.test', '1', 'Guatemalteca', 'DPI', '1') RETURNING id""",
                Long.class);
        jdbc.update("""
                INSERT INTO reservations (code, guest_id, room_type_id, room_id, checkin_date,
                                      checkout_date, guest_count, status, channel, total)
                SELECT 'VS-LLEGA1', ?, h.room_type_id, h.id, '2026-10-05', '2026-10-07', 1, 'CONFIRMADA',
                       'RECEPCION', 100.00
                FROM rooms h WHERE h.number = ?""", huesped, numero);
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

    private long habitacionId(String numero) {
        return jdbc.queryForObject("SELECT id  FROM rooms WHERE number = ?", Long.class, numero);
    }
}
