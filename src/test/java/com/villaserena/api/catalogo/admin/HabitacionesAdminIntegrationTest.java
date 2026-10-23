package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class HabitacionesAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @MockitoBean PasarelaPagos pasarela;

    private String tokenAdmin;
    private String tokenRecepcion;
    private long tipoId;

    @BeforeEach
    void setup() {
        tokenAdmin = "Bearer " + tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken();
        tokenRecepcion = "Bearer " + tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken();

        tipoId = jdbc.queryForObject("SELECT id FROM room_types WHERE status = 'ACTIVO' ORDER BY id LIMIT 1", Long.class);
    }

    @Test
    void adminPuedeCrearHabitacionFisica() throws Exception {
        String json = """
                {
                  "numero": "999",
                  "piso": 9,
                  "tipoHabitacionId": %d
                }
                """.formatted(tipoId);

        mvc.perform(post("/api/v1/admin/habitaciones")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero", is("999")))
                .andExpect(jsonPath("$.piso", is(9)))
                .andExpect(jsonPath("$.tipoHabitacionId", is((int) tipoId)))
                .andExpect(jsonPath("$.estado", is("ACTIVO")))
                .andExpect(jsonPath("$.ocupacion", is("LIBRE")))
                .andExpect(jsonPath("$.condicion", is("LIMPIA")));
    }

    @Test
    void crearHabitacionRechazaNumeroDuplicado() throws Exception {
        String numeroExistente = jdbc.queryForObject("SELECT number FROM rooms LIMIT 1", String.class);
        String json = """
                {
                  "numero": "%s",
                  "piso": 2,
                  "tipoHabitacionId": %d
                }
                """.formatted(numeroExistente, tipoId);

        mvc.perform(post("/api/v1/admin/habitaciones")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("HABITACION_EXISTENTE")));
    }

    @Test
    void crearHabitacionRechazaTipoInexistenteOInactivo() throws Exception {
        String json = """
                {
                  "numero": "888",
                  "piso": 1,
                  "tipoHabitacionId": 999999
                }
                """;

        mvc.perform(post("/api/v1/admin/habitaciones")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("DATOS_INVALIDOS")));
    }

    @Test
    void adminPuedeListarYFiltrarHabitaciones() throws Exception {
        mvc.perform(get("/api/v1/admin/habitaciones")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/admin/habitaciones?estado=ACTIVO&tipoHabitacionId=" + tipoId)
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminPuedeActualizarHabitacionYCambiarEstado() throws Exception {
        long habitacionId = jdbc.queryForObject("SELECT id FROM rooms ORDER BY id LIMIT 1", Long.class);

        String jsonEdicion = """
                {
                  "numero": "998",
                  "piso": 4
                }
                """;

        mvc.perform(patch("/api/v1/admin/habitaciones/" + habitacionId)
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEdicion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero", is("998")))
                .andExpect(jsonPath("$.piso", is(4)));

        String jsonEstado = """
                {
                  "estado": "INACTIVO"
                }
                """;

        mvc.perform(patch("/api/v1/admin/habitaciones/" + habitacionId + "/estado")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("INACTIVO")));
    }

    @Test
    void noPermiteDesactivarHabitacionConReservasActivas() throws Exception {
        long habitacionId = jdbc.queryForObject("SELECT id FROM rooms WHERE status = 'ACTIVO' ORDER BY id LIMIT 1", Long.class);
        Long huespedId = jdbc.queryForObject("""
                INSERT INTO guests(full_name, email, phone, document_type, document_number)
                VALUES('Demo Guest', 'demo.guest@test.com', '50212345678', 'PASAPORTE', 'A12345678')
                RETURNING id""", Long.class);

        // Crear una reserva activa asignada a esta habitación
        jdbc.update("""
                INSERT INTO reservations(code, guest_id, room_type_id, room_id, checkin_date, checkout_date, guest_count, status, channel, total)
                VALUES('VS-ACT001', ?, ?, ?, ?, ?, 1, 'CONFIRMADA', 'RECEPCION', 500.00)""",
                huespedId, tipoId, habitacionId, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4));

        String jsonEstado = """
                {
                  "estado": "INACTIVO"
                }
                """;

        mvc.perform(patch("/api/v1/admin/habitaciones/" + habitacionId + "/estado")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEstado))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("RESERVAS_ACTIVAS")));
    }

    @Test
    void soloAdminTieneAccesoAEndpointsDeHabitaciones() throws Exception {
        mvc.perform(get("/api/v1/admin/habitaciones")
                        .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/habitaciones"))
                .andExpect(status().isUnauthorized());
    }
}
