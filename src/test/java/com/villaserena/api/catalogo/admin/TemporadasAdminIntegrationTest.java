package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.catalogo.dto.TemporadaPeticion;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class TemporadasAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager txManager;

    private String tokenAdmin;
    private String tokenRecepcion;
    private long tipoId;

    @BeforeEach
    void setUp() {
        tokenAdmin = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
        tokenRecepcion = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken());

        tipoId = jdbc.queryForObject("SELECT id FROM room_types ORDER BY id LIMIT 1", Long.class);
    }

    @Test
    void listarTemporadasAdminDevuelveLista() throws Exception {
        mvc.perform(get("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void crearEditarYEliminarTemporadaExitosamente() throws Exception {
        LocalDate inicio = LocalDate.of(2028, 6, 1);
        LocalDate fin = LocalDate.of(2028, 6, 30);

        TemporadaPeticion peticionCrear = new TemporadaPeticion(
                "Temporada Verano 2028",
                inicio,
                fin,
                new BigDecimal("15.50"),
                List.of(tipoId));

        String respCrear = mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(peticionCrear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Temporada Verano 2028"))
                .andExpect(jsonPath("$.inicio").value("2028-06-01"))
                .andExpect(jsonPath("$.fin").value("2028-06-30"))
                .andExpect(jsonPath("$.ajustePct").value(15.50))
                .andExpect(jsonPath("$.tipoHabitacionIds[0]").value(tipoId))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(respCrear).get("id").asLong();

        // Editar temporada (PUT)
        TemporadaPeticion peticionEditar = new TemporadaPeticion(
                "Temporada Verano Actualizada",
                inicio,
                fin,
                new BigDecimal("20.00"),
                Collections.emptyList()); // Aplica a todos

        mvc.perform(put("/api/v1/admin/temporadas/{id}", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(peticionEditar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Temporada Verano Actualizada"))
                .andExpect(jsonPath("$.ajustePct").value(20.00))
                .andExpect(jsonPath("$.tipoHabitacionIds").isEmpty());

        // Eliminar temporada (DELETE -> 204)
        mvc.perform(delete("/api/v1/admin/temporadas/{id}", id)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        // Verificación de que ya no existe -> 404
        mvc.perform(delete("/api/v1/admin/temporadas/{id}", id)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void conflictoPorSolapamientoDevuelve409() throws Exception {
        LocalDate inicio = LocalDate.of(2028, 7, 1);
        LocalDate fin = LocalDate.of(2028, 7, 31);

        TemporadaPeticion base = new TemporadaPeticion(
                "Temporada Base Julio",
                inicio,
                fin,
                new BigDecimal("10.00"),
                List.of(tipoId));

        mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(base)))
                .andExpect(status().isCreated());

        // Intentar crear otra temporada solapada para el mismo tipo -> 409
        TemporadaPeticion solapada = new TemporadaPeticion(
                "Temporada Solapada Julio",
                LocalDate.of(2028, 7, 15),
                LocalDate.of(2028, 8, 15),
                new BigDecimal("12.00"),
                List.of(tipoId));

        mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(solapada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TEMPORADA_SOLAPADA"));
    }

    @Test
    void validacionesDatosInvalidosDevuelve400() throws Exception {
        // Fin anterior a Inicio
        TemporadaPeticion fechasInvertidas = new TemporadaPeticion(
                "Fechas malas",
                LocalDate.of(2028, 9, 30),
                LocalDate.of(2028, 9, 1),
                new BigDecimal("10.00"),
                Collections.emptyList());

        mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(fechasInvertidas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Ajuste <= -100
        TemporadaPeticion ajusteInvalido = new TemporadaPeticion(
                "Ajuste malo",
                LocalDate.of(2028, 9, 1),
                LocalDate.of(2028, 9, 30),
                new BigDecimal("-100.00"),
                Collections.emptyList());

        mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ajusteInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Tipo inexistente
        TemporadaPeticion tipoInexistente = new TemporadaPeticion(
                "Tipo inexistente",
                LocalDate.of(2028, 9, 1),
                LocalDate.of(2028, 9, 30),
                new BigDecimal("10.00"),
                List.of(99999L));

        mvc.perform(post("/api/v1/admin/temporadas")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(tipoInexistente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void operacionesTemporadaRequierenRolAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/temporadas"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/temporadas")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());
    }
}
