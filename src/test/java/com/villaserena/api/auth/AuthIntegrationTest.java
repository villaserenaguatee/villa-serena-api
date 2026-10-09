package com.villaserena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.TestcontainersConfiguration;

/** Pruebas de OBJ-0D: login, bloqueo, renovación, cierre de sesión, contraseña temporal y roles. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    private static final String CONTRASENA = "Demo1234";
    private static final String RECEPCION = "ana.perez@villaserena.test";

    @Autowired
    MockMvc mvc;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokenService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loginCorrectoYConsultaYo() throws Exception {
        String acceso = JsonPath.read(login(RECEPCION, CONTRASENA, 200), "$.accessToken");

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(RECEPCION))
                .andExpect(jsonPath("$.rol").value("RECEPCION"))
                .andExpect(jsonPath("$.area").doesNotExist());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/v1/auth/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void contrasenaIncorrectaRespondeMensajeGenerico() throws Exception {
        String cuerpo = login(RECEPCION, "Equivocada1", 401);
        assertThat((String) JsonPath.read(cuerpo, "$.codigo")).isEqualTo("CREDENCIALES_INVALIDAS");

        String noExiste = login("nadie@villaserena.test", "Equivocada1", 401);
        assertThat((String) JsonPath.read(noExiste, "$.mensaje")).isEqualTo("Correo o contraseña incorrectos.");
    }

    @Test
    void cincoIntentosFallidosBloqueanLaCuenta() throws Exception {
        String correo = "rodrigo.aju@villaserena.test";
        for (int i = 0; i < 5; i++) {
            login(correo, "Equivocada1", 401);
        }
        String bloqueado = login(correo, CONTRASENA, 401);
        assertThat((String) JsonPath.read(bloqueado, "$.codigo")).isEqualTo("CUENTA_BLOQUEADA");
    }

    @Test
    void empleadoInactivoNoEntra() throws Exception {
        Empleado empleado = empleados.findByCorreoIgnoreCase("marta.xicara@villaserena.test").orElseThrow();
        empleado.setEstado(EstadoEmpleado.INACTIVO);
        empleados.flush();

        login("marta.xicara@villaserena.test", CONTRASENA, 401);
    }

    @Test
    void renovarRotaElRefresh() throws Exception {
        String sesion = login(RECEPCION, CONTRASENA, 200);
        String refresh = JsonPath.read(sesion, "$.refreshToken");

        String nuevo = mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(nuevo, "$.refreshToken")).isNotEqualTo(refresh);

        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    void cerrarSesionRevocaElRefresh() throws Exception {
        String sesion = login(RECEPCION, CONTRASENA, 200);
        String acceso = JsonPath.read(sesion, "$.accessToken");
        String refresh = JsonPath.read(sesion, "$.refreshToken");

        mvc.perform(post("/api/v1/auth/cerrar-sesion").header("Authorization", "Bearer " + acceso)
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(refresh)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contrasenaTemporalBloqueaTodoMenosCambiarla() throws Exception {
        Empleado empleado = empleados.findByCorreoIgnoreCase(RECEPCION).orElseThrow();
        empleado.setDebeCambiarContrasena(true);
        empleados.flush();

        String sesion = login(RECEPCION, CONTRASENA, 200);
        String acceso = JsonPath.read(sesion, "$.accessToken");
        assertThat((Boolean) JsonPath.read(sesion, "$.empleado.debeCambiarContrasena")).isTrue();

        // /yo sí se permite; cualquier otra ruta responde CONTRASENA_TEMPORAL.
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservas").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_TEMPORAL"));

        // Reglas de la nueva contraseña.
        cambiar(acceso, CONTRASENA, "solotexto", "solotexto")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_INVALIDA"));
        cambiar(acceso, "Equivocada1", "NuevaClave1", "NuevaClave1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));

        String nueva = cambiar(acceso, CONTRASENA, "NuevaClave1", "NuevaClave1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleado.debeCambiarContrasena").value(false))
                .andReturn().getResponse().getContentAsString();

        // La anterior deja de funcionar y la nueva sí entra.
        login(RECEPCION, CONTRASENA, 401);
        login(RECEPCION, "NuevaClave1", 200);
        assertThat((String) JsonPath.read(nueva, "$.accessToken")).isNotBlank();
    }

    @Test
    void huespedNoPuedeUsarRutasDelPersonal() throws Exception {
        Long huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Huésped Prueba', 'huesped.prueba@correo.test', '+502 5555 5555', 'Guatemalteca', 'DPI', '1234567890101')
                RETURNING id""", Long.class);
        String acceso = tokenService.emitirParaHuesped(huespedId).accessToken();

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    private String login(String correo, String contrasena, int estadoEsperado) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)))
                .andExpect(status().is(estadoEsperado))
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions cambiar(String acceso, String actual, String nueva,
            String confirmacion) throws Exception {
        return mvc.perform(post("/api/v1/auth/cambiar-contrasena").header("Authorization", "Bearer " + acceso)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasenaActual\":\"%s\",\"contrasenaNueva\":\"%s\",\"confirmacion\":\"%s\"}"
                        .formatted(actual, nueva, confirmacion)));
    }

    private static String refreshJson(String refresh) {
        return "{\"refreshToken\":\"" + refresh + "\"}";
    }
}
