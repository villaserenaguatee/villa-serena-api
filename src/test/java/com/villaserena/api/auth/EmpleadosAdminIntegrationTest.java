package com.villaserena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.admin.ContrasenaRestablecida;
import com.villaserena.api.auth.admin.EmpleadoCreado;

@SpringBootTest(properties = "logging.level.com.villaserena.api.comun.ManejadorErrores=DEBUG")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class EmpleadosAdminIntegrationTest {
    private static final String RUTA = "/api/v1/admin/empleados";
    private static final String ADMIN = "sofia.morales@villaserena.test";
    private static final String RECEPCION = "ana.perez@villaserena.test";
    @Autowired MockMvc mvc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder contrasenas;
    @Autowired jakarta.persistence.EntityManager entidades;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void listaCuentasRealesYFiltraSinExponerCredenciales() throws Exception {
        String cuerpo = enviar(get(RUTA).param("rol", "ADMIN").param("estado", "ACTIVO"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> lista = JsonPath.read(cuerpo, "$");
        assertThat(lista).isNotEmpty().allSatisfy(e -> {
            assertThat(e).containsEntry("rol", "ADMIN").containsEntry("estado", "ACTIVO");
            assertThat(e.keySet()).containsExactlyInAnyOrder("id", "nombreCompleto", "correo", "telefono", "rol", "area", "estado");
        });
        assertThat(lista).anySatisfy(e -> assertThat(e).containsEntry("correo", ADMIN));
        enviar(get(RUTA).param("rol", "DESCONOCIDO"), null).andExpect(status().isBadRequest());
        enviar(get(RUTA).param("estado", "DESCONOCIDO"), null).andExpect(status().isBadRequest());
    }

    @Test
    void altaGuardaHashYPermiteLoginConCambioObligatorio() throws Exception {
        String respuesta = crear("nueva.persona@example.test", "RECEPCION", "");
        String temporal = JsonPath.read(respuesta, "$.contrasenaTemporal");
        Empleado empleado = empleados.findByCorreoIgnoreCase("nueva.persona@example.test").orElseThrow();
        assertThat(temporal).hasSizeGreaterThanOrEqualTo(32);
        assertThat(contrasenas.matches(temporal, empleado.getContrasenaHash())).isTrue();
        assertThat(empleado.getContrasenaHash()).isNotEqualTo(temporal);
        assertThat(empleado.isDebeCambiarContrasena()).isTrue();
        Map<String, Object> datos = JsonPath.read(respuesta, "$");
        assertThat(datos).containsOnlyKeys("empleado", "contrasenaTemporal");
        String sesion = login(empleado.getCorreo(), temporal, 200);
        assertThat((Boolean) JsonPath.read(sesion, "$.empleado.debeCambiarContrasena")).isTrue();
        String acceso = JsonPath.read(sesion, "$.accessToken");
        mvc.perform(get(RUTA).header("Authorization", "Bearer " + acceso))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CONTRASENA_TEMPORAL"));
        mvc.perform(post("/api/v1/auth/cambiar-contrasena").header("Authorization", "Bearer " + acceso)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"contrasenaActual":"%s","contrasenaNueva":"ClavePropia123","confirmacion":"ClavePropia123"}
                """.formatted(temporal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.empleado.debeCambiarContrasena").value(false));
        login(empleado.getCorreo(), temporal, 401);
        login(empleado.getCorreo(), "ClavePropia123", 200);
        assertThat(new EmpleadoCreado(null, temporal).toString()).doesNotContain(temporal);
        assertThat(new ContrasenaRestablecida(temporal).toString()).doesNotContain(temporal);
    }

    @Test
    void correoDuplicadoIgnoraMayusculas() throws Exception {
        enviar(post(RUTA), alta(ADMIN.toUpperCase(java.util.Locale.ROOT), "ADMIN", ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nombreCompleto\":null}", "{\"nombreCompleto\":\"   \"}",
            "{\"telefono\":null}", "{\"rol\":null}", "{\"rol\":\"OTRO\"}",
            "{\"correo\":\"otro@example.test\"}", "{\"contrasenaHash\":\"x\"}",
            "{\"areaInformada\":true}", "{\"estado\":\"INACTIVO\"}"})
    void edicionRechazaCamposInvalidosONoEditables(String cuerpo) throws Exception {
        enviar(patch(RUTA + "/" + id(RECEPCION)), cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    void altaValidaCamposLongitudesYRelacionRolArea() throws Exception {
        for (String cuerpo : List.of("{}", alta("mal-correo", "ADMIN", ""),
                alta("nuevo@example.test", "MANTENIMIENTO_LIMPIEZA", ""),
                alta("nuevo@example.test", "RECEPCION", ",\"area\":\"LIMPIEZA\""),
                alta("nuevo@example.test", "ADMIN", ",\"password_hash\":\"valor\""),
                alta("nuevo@example.test", "ADMIN", ",\"estado\":\"ACTIVO\""),
                alta("nuevo@example.test", "ADMIN", "").replace("Persona Nueva", "x".repeat(151)),
                alta("nuevo@example.test", "ADMIN", "").replace("5555", "1".repeat(31)))) {
            enviar(post(RUTA), cuerpo).andExpect(status().isBadRequest());
        }
    }

    @Test
    void edicionParcialConservaDatosYValidaAreaDelRolResultante() throws Exception {
        long id = id(RECEPCION);
        enviar(patch(RUTA + "/" + id), "{\"telefono\":\"12345678\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(RECEPCION))
                .andExpect(jsonPath("$.nombreCompleto").value(empleados.findById(id).orElseThrow().getNombreCompleto()));
        enviar(patch(RUTA + "/" + id), "{\"rol\":\"MANTENIMIENTO_LIMPIEZA\"}").andExpect(status().isBadRequest());
        enviar(patch(RUTA + "/" + id), "{\"rol\":\"MANTENIMIENTO_LIMPIEZA\",\"area\":\"AMBAS\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").value("AMBAS"));
        enviar(patch(RUTA + "/" + id), "{\"nombreCompleto\":\"Nombre Editado\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").value("AMBAS"));
        enviar(patch(RUTA + "/" + id), "{\"area\":null}").andExpect(status().isBadRequest());
        enviar(patch(RUTA + "/" + id), "{\"rol\":\"RECEPCION\",\"area\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").isEmpty());
    }

    @Test
    void adminNoPuedeCambiarSuRolNiDesactivarse() throws Exception {
        enviar(patch(RUTA + "/" + id(ADMIN)), "{\"rol\":\"RECEPCION\"}").andExpect(status().isConflict());
        enviar(patch(RUTA + "/" + id(ADMIN) + "/estado"), "{\"estado\":\"INACTIVO\"}")
                .andExpect(status().isConflict());
        enviar(patch(RUTA + "/" + id(ADMIN)), "{\"nombreCompleto\":\"Nombre Propio\"}").andExpect(status().isOk());
    }

    @Test
    void desactivarImpideLoginRenovacionYAccesoYActivarPermiteLogin() throws Exception {
        String sesion = login(RECEPCION, "Demo1234", 200);
        String acceso = JsonPath.read(sesion, "$.accessToken");
        String refresh = JsonPath.read(sesion, "$.refreshToken");
        enviar(patch(RUTA + "/" + id(RECEPCION) + "/estado"), "{\"estado\":\"INACTIVO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("INACTIVO"));
        login(RECEPCION, "Demo1234", 401);
        renovar(refresh).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_ACTUALIZAR"));
        enviar(patch(RUTA + "/" + id(RECEPCION) + "/estado"), "{\"estado\":\"ACTIVO\"}").andExpect(status().isOk());
        login(RECEPCION, "Demo1234", 200);
        renovar(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarPermisosRevocaRefreshPreviosYRechazaJwtAnterior() throws Exception {
        String sesion = login(RECEPCION, "Demo1234", 200);
        String acceso = JsonPath.read(sesion, "$.accessToken");
        enviar(patch(RUTA + "/" + id(RECEPCION)), "{\"rol\":\"ROOM_SERVICE\"}").andExpect(status().isOk());
        renovar(JsonPath.read(sesion, "$.refreshToken")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + acceso))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_ACTUALIZAR"));
    }

    @Test
    void edicionInvalidaVersionAnteriorDelCatalogoDePersonal() throws Exception {
        long id = id(RECEPCION);
        Long version = jdbc.queryForObject("SELECT version FROM employees WHERE id = ?", Long.class, id);
        enviar(patch(RUTA + "/" + id), "{\"telefono\":\"12345678\"}").andExpect(status().isOk());
        enviar(post("/api/v1/admin/catalogos/personal"), """
                {"clave":"%s","id":%d,"version":%d,"datos":{}}
                """.formatted(java.util.UUID.randomUUID(), id, version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("VERSION_CAMBIADA"));
    }

    @Test
    void restablecerEntregaClaveUnaVezDesbloqueaYRevocaSesiones() throws Exception {
        String sesion = login(RECEPCION, "Demo1234", 200);
        for (int i = 0; i < 5; i++) login(RECEPCION, "Incorrecta1", 401);
        String respuesta = enviar(post(RUTA + "/" + id(RECEPCION) + "/restablecer-contrasena"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> datos = JsonPath.read(respuesta, "$");
        assertThat(datos).containsOnlyKeys("contrasenaTemporal");
        String temporal = (String) datos.get("contrasenaTemporal");
        renovar(JsonPath.read(sesion, "$.refreshToken")).andExpect(status().isUnauthorized());
        login(RECEPCION, "Demo1234", 401);
        String nueva = login(RECEPCION, temporal, 200);
        assertThat((Boolean) JsonPath.read(nueva, "$.empleado.debeCambiarContrasena")).isTrue();
        String segunda = enviar(post(RUTA + "/" + id(RECEPCION) + "/restablecer-contrasena"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(segunda, "$.contrasenaTemporal")).isNotEqualTo(temporal);
        login(RECEPCION, temporal, 401);
        assertThat(empleados.findById(id(RECEPCION)).orElseThrow().getContrasenaHash()).doesNotContain(temporal);
    }

    @Test
    void todasLasOperacionesExigenAdminYContrasenaDefinitiva() throws Exception {
        String recepcion = acceso(RECEPCION);
        Empleado admin = empleados.findByCorreoIgnoreCase(ADMIN).orElseThrow();
        admin.setDebeCambiarContrasena(true);
        empleados.flush();
        String temporal = acceso(ADMIN);
        for (String token : List.of("", recepcion, temporal)) {
            int esperado = token.isEmpty() ? 401 : 403;
            for (MockHttpServletRequestBuilder peticion : List.of(get(RUTA),
                    post(RUTA).content(alta("denegada@example.test", "ADMIN", "")),
                    patch(RUTA + "/1").content("{\"telefono\":\"1234\"}"),
                    patch(RUTA + "/1/estado").content("{\"estado\":\"ACTIVO\"}"),
                    post(RUTA + "/1/restablecer-contrasena"))) {
                mvc.perform(peticion.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().is(esperado));
            }
        }
    }

    @Test
    void idsInexistentesYEstadoInvalido() throws Exception {
        enviar(patch(RUTA + "/999999999"), "{\"telefono\":\"123\"}").andExpect(status().isNotFound());
        enviar(patch(RUTA + "/999999999/estado"), "{\"estado\":\"ACTIVO\"}").andExpect(status().isNotFound());
        enviar(post(RUTA + "/999999999/restablecer-contrasena"), null).andExpect(status().isNotFound());
        enviar(patch(RUTA + "/0"), "{\"telefono\":\"123\"}").andExpect(status().isBadRequest());
        enviar(patch(RUTA + "/" + id(RECEPCION) + "/estado"), "{\"estado\":null}").andExpect(status().isBadRequest());
        enviar(patch(RUTA + "/" + id(RECEPCION) + "/estado"), "{\"estado\":\"OTRO\"}").andExpect(status().isBadRequest());
    }

    @Test
    void rutaInexistenteRegistraMetodoYPathSinQuery(CapturedOutput salida) throws Exception {
        enviar(get("/api/v1/admin/inexistente").param("token", "MARCADOR_SENSIBLE"), null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(salida.getOut()).contains("Ruta no encontrada: GET /api/v1/admin/inexistente");
        assertThat(salida.getOut()).doesNotContain("MARCADOR_SENSIBLE");
    }

    @Test
    void debugDeSpringOcultaContrasenasTemporales(CapturedOutput salida) throws Exception {
        String logger = "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor";
        var sistema = org.springframework.boot.logging.LoggingSystem.get(getClass().getClassLoader());
        var anterior = sistema.getLoggerConfiguration(logger).getConfiguredLevel();
        sistema.setLogLevel(logger, org.springframework.boot.logging.LogLevel.DEBUG);
        try {
            String respuesta = crear("secreto-en-logs@example.test", "RECEPCION", "");
            String primera = JsonPath.read(respuesta, "$.contrasenaTemporal");
            Number id = JsonPath.read(respuesta, "$.empleado.id");
            String restablecida = enviar(post(RUTA + "/" + id + "/restablecer-contrasena"), null)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String segunda = JsonPath.read(restablecida, "$.contrasenaTemporal");
            assertThat(salida.getOut()).contains("contrasenaTemporal=PROTEGIDA").doesNotContain(primera, segunda);
        } finally {
            sistema.setLogLevel(logger, anterior);
        }
    }

    private String crear(String correo, String rol, String extra) throws Exception {
        return enviar(post(RUTA), alta(correo, rol, extra)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String alta(String correo, String rol, String extra) {
        return """
                {"nombreCompleto":"Persona Nueva","correo":"%s","telefono":"5555","rol":"%s"%s}
                """.formatted(correo, rol, extra);
    }

    private long id(String correo) { return empleados.findByCorreoIgnoreCase(correo).orElseThrow().getId(); }

    private String acceso(String correo) {
        return tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow()).accessToken();
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + acceso(ADMIN));
        if (cuerpo != null) peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        entidades.flush();
        entidades.clear();
        var respuesta = mvc.perform(peticion);
        entidades.flush();
        entidades.clear();
        return respuesta;
    }

    private String login(String correo, String contrasena, int esperado) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }

    private ResultActions renovar(String refresh) throws Exception {
        return mvc.perform(post("/api/v1/auth/renovar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)));
    }
}
