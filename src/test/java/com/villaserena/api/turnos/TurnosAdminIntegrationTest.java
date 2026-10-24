package com.villaserena.api.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.villaserena.api.auth.Empleado;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class TurnosAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager txManager;

    private String tokenAdmin;
    private String tokenRecepcion;
    private Empleado admin;
    private Empleado recepcionista;

    @BeforeEach
    void setUp() {
        admin = empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow();
        recepcionista = empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow();

        tokenAdmin = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(admin).accessToken());
        tokenRecepcion = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(recepcionista).accessToken());
    }

    @Test
    void adminPuedeCrearEditarYDesactivarTurno() throws Exception {
        TurnoPeticion crear = new TurnoPeticion("Matutino Test", "06:00", "14:00");

        String resCrear = mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(crear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Matutino Test"))
                .andExpect(jsonPath("$.horaInicio").value("06:00"))
                .andExpect(jsonPath("$.horaFin").value("14:00"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(resCrear).get("id").asLong();

        // Listar turnos
        mvc.perform(get("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].nombre").value("Matutino Test"));

        // Editar turno
        EditarTurnoPeticion editar = new EditarTurnoPeticion("Matutino Modificado", "06:30", "14:30");
        mvc.perform(patch("/api/v1/admin/turnos/{id}", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(editar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Matutino Modificado"))
                .andExpect(jsonPath("$.horaInicio").value("06:30"))
                .andExpect(jsonPath("$.horaFin").value("14:30"));

        // Desactivar turno
        mvc.perform(post("/api/v1/admin/turnos/{id}/desactivar", id)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    void validacionesDeTurno() throws Exception {
        // Horas iguales -> 400
        TurnoPeticion horasIguales = new TurnoPeticion("Turno Invalido", "08:00", "08:00");
        mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(horasIguales)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Nombre duplicado -> 409
        TurnoPeticion turno1 = new TurnoPeticion("Vespertino Test", "14:00", "22:00");
        mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(turno1)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(turno1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_EXISTENTE"));

        // 404 al editar inexistente
        mvc.perform(patch("/api/v1/admin/turnos/999999")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"No existe\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void asignarTurnosSemanaConsultarVistaYEliminar() throws Exception {
        // Crear turno nocturno cruzando medianoche (22:00 a 06:00)
        TurnoPeticion crearTurno = new TurnoPeticion("Nocturno Test", "22:00", "06:00");
        String resTurno = mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(crearTurno)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long turnoId = json.readTree(resTurno).get("id").asLong();

        LocalDate fecha1 = LocalDate.of(2028, 10, 16);
        LocalDate fecha2 = LocalDate.of(2028, 10, 17);

        AsignarTurnosPeticion asignar = new AsignarTurnosPeticion(
                recepcionista.getId(),
                turnoId,
                List.of(fecha1, fecha2)
        );

        String resAsignar = mvc.perform(post("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(asignar)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].empleado.id").value(recepcionista.getId()))
                .andExpect(jsonPath("$[0].turno.id").value(turnoId))
                .andReturn().getResponse().getContentAsString();

        long asignacionId1 = json.readTree(resAsignar).get(0).get("id").asLong();

        // Consultar vista semanal
        mvc.perform(get("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .param("inicio", "2028-10-15")
                .param("fin", "2028-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + asignacionId1 + ")].fecha").value("2028-10-16"));

        // Eliminar asignación
        mvc.perform(delete("/api/v1/admin/asignaciones-turnos/{id}", asignacionId1)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        // Ya no existe -> 404
        mvc.perform(delete("/api/v1/admin/asignaciones-turnos/{id}", asignacionId1)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void validacionesYConflictosAsignaciones() throws Exception {
        // Crear turno matutino
        TurnoPeticion crearMatutino = new TurnoPeticion("Matutino Conflicto", "07:00", "15:00");
        String resM = mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(crearMatutino)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long matutinoId = json.readTree(resM).get("id").asLong();

        // Asignar matutino en 2028-11-01
        LocalDate dia = LocalDate.of(2028, 11, 1);
        AsignarTurnosPeticion asignacionValida = new AsignarTurnosPeticion(
                recepcionista.getId(), matutinoId, List.of(dia));

        mvc.perform(post("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(asignacionValida)))
                .andExpect(status().isCreated());

        // Intentar asignar otro turno solapado el mismo día (e.g. 10:00 a 18:00) -> 409
        TurnoPeticion crearSolapado = new TurnoPeticion("Solapado", "10:00", "18:00");
        String resS = mvc.perform(post("/api/v1/admin/turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(crearSolapado)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long solapadoId = json.readTree(resS).get("id").asLong();

        AsignarTurnosPeticion asignacionSolapada = new AsignarTurnosPeticion(
                recepcionista.getId(), solapadoId, List.of(dia));

        mvc.perform(post("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(asignacionSolapada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_SOLAPADO"));

        // No se puede asignar a un ADMIN -> 400
        AsignarTurnosPeticion asignacionAdmin = new AsignarTurnosPeticion(
                admin.getId(), matutinoId, List.of(LocalDate.of(2028, 11, 2)));
        mvc.perform(post("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(asignacionAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Fechas con fin < inicio en consulta -> 400
        mvc.perform(get("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenAdmin)
                .param("inicio", "2028-11-10")
                .param("fin", "2028-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void controlDeAccesoTurnosYAsignaciones() throws Exception {
        mvc.perform(get("/api/v1/admin/turnos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/asignaciones-turnos")
                .param("inicio", "2028-10-01")
                .param("fin", "2028-10-07"))
                .andExpect(status().isUnauthorized());

        // Recepción no puede acceder a endpoints admin de turnos -> 403
        mvc.perform(get("/api/v1/admin/turnos")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/asignaciones-turnos")
                .header("Authorization", tokenRecepcion)
                .param("inicio", "2028-10-01")
                .param("fin", "2028-10-07"))
                .andExpect(status().isForbidden());
    }
}
