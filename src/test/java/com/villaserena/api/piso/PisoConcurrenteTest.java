package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Dos empleados a la vez (OBJ-3B-1): solo uno inicia la limpieza o toma la incidencia;
 * el otro recibe 409 con el nombre de quien ganó. El evento 4 sale después de confirmar.
 * Sin {@code @Transactional}: usa transacciones reales y limpia lo que cambia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
class PisoConcurrenteTest {

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
    PasarelaStripe stripe;

    @MockitoBean
    HabitacionEventos eventos;

    long habitacion;
    long historialPrevio;

    @BeforeEach
    void preparar() {
        habitacion = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '302'", Long.class);
        historialPrevio = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM historial_estados", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM incidencias WHERE habitacion_id = ?", habitacion);
        jdbc.update("DELETE FROM historial_estados WHERE id > ?", historialPrevio);
        jdbc.update("UPDATE habitaciones SET condicion = 'LIMPIA', limpieza_empleado_id = NULL WHERE id = ?",
                habitacion);
        reloj.reiniciar();
    }

    @Test
    void dosEmpleadosInicianLaMismaLimpiezaYSoloUnoGana() throws Exception {
        jdbc.update("UPDATE habitaciones SET condicion = 'SUCIA' WHERE id = ?", habitacion);
        List<MockHttpServletResponse> respuestas = aLaVez(
                "/api/v1/limpieza/habitaciones/" + habitacion + "/iniciar",
                token("luis.garcia@villaserena.test"), token("pedro.coy@villaserena.test"));

        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        MockHttpServletResponse perdedora = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst()
                .orElseThrow();
        String ganador = jdbc.queryForObject("""
                SELECT e.nombre_completo FROM habitaciones h JOIN empleados e ON e.id = h.limpieza_empleado_id
                WHERE h.id = ?""", String.class, habitacion);
        assertThat(perdedora.getContentAsString()).contains("LIMPIEZA_EN_CURSO").contains(ganador);
        verify(eventos, timeout(2000).times(1)).habitacionCambio(habitacion);
    }

    @Test
    void dosTecnicosTomanLaMismaIncidenciaYSoloUnoGana() throws Exception {
        long id = jdbc.queryForObject("""
                INSERT INTO incidencias (habitacion_id, descripcion, impide_uso, reportada_por_empleado_id)
                SELECT ?, 'Puerta trabada.', FALSE, id FROM empleados WHERE correo = 'luis.garcia@villaserena.test'
                RETURNING id""", Long.class, habitacion);
        List<MockHttpServletResponse> respuestas = aLaVez("/api/v1/incidencias/" + id + "/tomar",
                token("marta.xicara@villaserena.test"), token("pedro.coy@villaserena.test"));

        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        String tecnico = jdbc.queryForObject("""
                SELECT e.nombre_completo FROM incidencias i JOIN empleados e ON e.id = i.tecnico_id
                WHERE i.id = ?""", String.class, id);
        assertThat(respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow()
                .getContentAsString()).contains("INCIDENCIA_TOMADA").contains(tecnico);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_estados WHERE tipo_entidad = 'INCIDENCIA' "
                + "AND id_entidad = ? AND estado_nuevo = 'EN_PROCESO'", Integer.class, id)).isEqualTo(1);
        // Tomar no cambia la habitación: no hay evento 4.
        verify(eventos, never()).habitacionCambio(Mockito.anyLong());
    }

    /** Envía la misma petición con dos tokens al mismo tiempo. */
    private List<MockHttpServletResponse> aLaVez(String ruta, String... tokensEmpleados) throws Exception {
        CyclicBarrier salida = new CyclicBarrier(tokensEmpleados.length);
        List<CompletableFuture<MockHttpServletResponse>> envios = new ArrayList<>();
        for (String token : tokensEmpleados) {
            envios.add(CompletableFuture.supplyAsync(() -> {
                try {
                    salida.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post(ruta).header("Authorization", token)).andReturn().getResponse();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        List<MockHttpServletResponse> respuestas = new ArrayList<>();
        for (CompletableFuture<MockHttpServletResponse> envio : envios) {
            respuestas.add(envio.get(20, TimeUnit.SECONDS));
        }
        return respuestas;
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
}
