package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import tools.jackson.databind.ObjectMapper;

/** El esquema físico en inglés conserva los contratos existentes de JDBC y JPA. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class EsquemaInglesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;

    @ParameterizedTest
    @ValueSource(strings = {"tipos", "habitaciones", "personal", "temporadas"})
    void losCatalogosConservanSusCamposPublicos(String recurso) throws Exception {
        mvc.perform(get("/api/v1/admin/catalogos/{recurso}", recurso)
                        .header("Authorization", token("sofia.morales@villaserena.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].version").isNumber());
    }

    @Test
    void guardaUnTipoMedianteLaTablaDinamicaYConservaElContrato() throws Exception {
        enviar("/api/v1/admin/catalogos/tipos", Map.of("clave", UUID.randomUUID(), "version", 0,
                "datos", Map.of("nombre", "Tipo adicional", "descripcion", "Prueba", "capacidad", 2,
                        "precioBase", 125, "ajusteFinSemanaPct", 0, "estado", "ACTIVO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("SELECT base_price FROM room_types WHERE name='Tipo adicional'",
                Integer.class)).isEqualTo(125);
    }

    @Test
    void laAuditoriaDelPersonalNoGuardaElHashDeContrasena() throws Exception {
        var empleado = empleados.findByCorreoIgnoreCase("diego.castillo@villaserena.test").orElseThrow();
        enviar("/api/v1/admin/catalogos/personal", Map.of("clave", UUID.randomUUID(), "id", empleado.getId(),
                "version", 1, "datos", Map.of("nombre", "Diego Castillo", "correo", empleado.getCorreo(),
                        "telefono", "+50250000002", "rol", "ADMIN", "estado", "ACTIVO", "perfil", Map.of())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT jsonb_exists(previous, 'password_hash') OR jsonb_exists(new, 'password_hash')
                FROM administration_audit WHERE resource='personal' AND record_id=?
                """, Boolean.class, empleado.getId())).isFalse();
    }

    @Test
    void registraYConsultaAsistenciaConLosNombresPublicosAnteriores() throws Exception {
        long id = empleados.findByCorreoIgnoreCase("luis.garcia@villaserena.test").orElseThrow().getId();
        enviar("/api/v1/laboral/admin/asistencia", solicitud(Map.of("empleadoId", id, "version", 0,
                "fecha", "2026-10-05", "turno", "Mañana", "estado", "presente", "observacion", "Prueba")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.empleado_id").value(id))
                .andExpect(jsonPath("$.estado").value("presente"));
        mvc.perform(get("/api/v1/laboral/admin/asistencia").param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31").header("Authorization", token("sofia.morales@villaserena.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].turno").value("Mañana"));
    }

    @Test
    void calculaYConsultaNominaSinCambiarLosNombresDelContrato() throws Exception {
        long id = empleados.findByCorreoIgnoreCase("luis.garcia@villaserena.test").orElseThrow().getId();
        jdbc.update("""
                UPDATE employees SET profile='{"salarioBase":2000}'::jsonb WHERE id=?
                """, id);
        enviar("/api/v1/laboral/admin/nominas", solicitud(Map.of("periodo", "2026-10", "empleados", List.of(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registros[0].neto").value(2000));
        mvc.perform(get("/api/v1/laboral/admin/nominas").param("periodo", "2026-10")
                        .header("Authorization", token("sofia.morales@villaserena.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Luis García"));
    }

    @Test
    void unaCorreccionTraduceElCampoPublicoALaColumnaFisica() throws Exception {
        var respuesta = mvc.perform(post("/api/v1/laboral/correcciones").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", token("luis.garcia@villaserena.test"))
                        .content(json.writeValueAsString(solicitud(Map.of("campo", "telefono", "nuevo", "+50255551234",
                                "motivo", "Actualizar contacto")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(respuesta).get("id").asLong();
        enviar("/api/v1/laboral/admin/correcciones/" + id,
                solicitud(Map.of("version", 1, "estado", "aprobada", "respuesta", "Datos verificados")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("aprobada"));
        assertThat(jdbc.queryForObject("SELECT phone FROM employees WHERE email='luis.garcia@villaserena.test'",
                String.class)).isEqualTo("+50255551234");
    }

    private Map<String, Object> solicitud(Map<String, ?> datos) {
        return Map.of("clave", UUID.randomUUID(), "datos", datos);
    }

    private ResultActions enviar(String ruta, Map<String, ?> contenido) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", token("sofia.morales@villaserena.test"))
                .content(json.writeValueAsString(contenido)));
    }

    private String token(String correo) {
        return "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow()).accessToken();
    }
}
