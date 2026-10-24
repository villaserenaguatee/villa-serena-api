package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.web.servlet.ResultActions;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConflictoCatalogosIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        tokenAdmin = "Bearer " + new org.springframework.transaction.support.TransactionTemplate(txManager).execute(status ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
    }

    private ResultActions enviarCatalogo(String recurso, Map<?, ?> body) throws Exception {
        return mvc.perform(post("/api/v1/admin/catalogos/{recurso}", recurso)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    @Test
    void creacionConNombreDeCategoriaExistenteDevuelveConflicto409() throws Exception {
        String nombreExistente = jdbc.queryForObject("SELECT name FROM room_types ORDER BY id LIMIT 1", String.class);

        enviarCatalogo("tipos", Map.of(
                "clave", UUID.randomUUID(),
                "version", 0,
                "datos", Map.of(
                        "nombre", nombreExistente,
                        "descripcion", "Duplicado de prueba",
                        "capacidad", 2,
                        "precioBase", 150,
                        "ajusteFinSemanaPct", 0,
                        "estado", "ACTIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EXISTENTE"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe una categoría de habitación con ese nombre."));
    }

    @Test
    void edicionConNombreDeCategoriaExistenteDevuelveConflicto409() throws Exception {
        var tipos = jdbc.queryForList("SELECT id, name, version FROM room_types ORDER BY id LIMIT 2");
        String nombreTipo1 = (String) tipos.get(0).get("name");
        Long idTipo2 = ((Number) tipos.get(1).get("id")).longValue();
        long versionTipo2 = ((Number) tipos.get(1).get("version")).longValue();

        enviarCatalogo("tipos", Map.of(
                "clave", UUID.randomUUID(),
                "id", idTipo2,
                "version", versionTipo2,
                "datos", Map.of(
                        "nombre", nombreTipo1,
                        "descripcion", "Cambio a nombre repetido",
                        "capacidad", 2,
                        "precioBase", 120,
                        "ajusteFinSemanaPct", 0,
                        "estado", "ACTIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EXISTENTE"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe una categoría de habitación con ese nombre."));
    }

    @Test
    void endpointsRestDirectosTiposDevuelvenConflicto409() throws Exception {
        var tipos = jdbc.queryForList("SELECT id, name FROM room_types ORDER BY id LIMIT 2");
        String nombreTipo1 = (String) tipos.get(0).get("name");
        Long idTipo2 = ((Number) tipos.get(1).get("id")).longValue();

        // POST /api/v1/admin/tipos-habitacion con nombre duplicado
        mvc.perform(post("/api/v1/admin/tipos-habitacion")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "nombre", nombreTipo1,
                        "descripcion", "Intento duplicado directo",
                        "capacidad", 2,
                        "precioBase", 200.00))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EXISTENTE"));

        // PATCH /api/v1/admin/tipos-habitacion/{id}
        mvc.perform(patch("/api/v1/admin/tipos-habitacion/{id}", idTipo2)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "nombre", nombreTipo1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EXISTENTE"));
    }

    @Test
    void creacionDeHabitacionConNumeroExistenteDevuelveHabitacionExistente() throws Exception {
        // Habitación 101 ya existe
        enviarCatalogo("habitaciones", Map.of(
                "clave", UUID.randomUUID(),
                "version", 0,
                "datos", Map.of(
                        "numero", "101",
                        "piso", 1,
                        "tipoHabitacionId", 1,
                        "estado", "ACTIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_EXISTENTE"))
                .andExpect(jsonPath("$.mensaje").value("Ese número de habitación ya existe."));

        // Direct REST endpoint POST /api/v1/admin/habitaciones
        mvc.perform(post("/api/v1/admin/habitaciones")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "numero": "101",
                          "piso": 1,
                          "tipoHabitacionId": 1
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_EXISTENTE"));
    }

    @Test
    void creacionConcurrenteConMismoNombreSoloUnaPersisteYOtraRecibeConflicto() throws Exception {
        String nombreUnico = "Categoria Concurrente " + UUID.randomUUID().toString().substring(0, 8);
        int hilos = 2;
        var executor = Executors.newFixedThreadPool(hilos);
        var latch = new CountDownLatch(1);

        Callable<Integer> tarea = () -> {
            latch.await();
            var res = enviarCatalogo("tipos", Map.of(
                    "clave", UUID.randomUUID(),
                    "version", 0,
                    "datos", Map.of(
                            "nombre", nombreUnico,
                            "descripcion", "Test concurrente",
                            "capacidad", 2,
                            "precioBase", 100,
                            "ajusteFinSemanaPct", 0,
                            "estado", "ACTIVO"))).andReturn();
            return res.getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(tarea);
        Future<Integer> f2 = executor.submit(tarea);
        latch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();

        // Una debe ser 200 y la otra 409
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);

        // En la base de datos solo debe haber exactamente 1 registro
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM room_types WHERE name = ?", Integer.class, nombreUnico);
        assertThat(total).isEqualTo(1);
    }

    @Test
    void creacionConcurrenteHabitacionMismoNumeroSoloUnaPersisteYOtraRecibeConflicto() throws Exception {
        String numeroUnico = "99" + (int)(Math.random() * 90 + 10);
        int hilos = 2;
        var executor = Executors.newFixedThreadPool(hilos);
        var latch = new CountDownLatch(1);

        Callable<Integer> tarea = () -> {
            latch.await();
            var res = enviarCatalogo("habitaciones", Map.of(
                    "clave", UUID.randomUUID(),
                    "version", 0,
                    "datos", Map.of(
                            "numero", numeroUnico,
                            "piso", 9,
                            "tipoHabitacionId", 1,
                            "estado", "ACTIVO"))).andReturn();
            return res.getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(tarea);
        Future<Integer> f2 = executor.submit(tarea);
        latch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();

        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM rooms WHERE number = ?", Integer.class, numeroUnico);
        assertThat(total).isEqualTo(1);
    }
}
