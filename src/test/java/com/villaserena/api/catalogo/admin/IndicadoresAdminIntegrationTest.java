package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class IndicadoresAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired PlatformTransactionManager txManager;

    private String tokenAdmin;
    private String tokenRecepcion;

    @BeforeEach
    void setUp() {
        tokenAdmin = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
        tokenRecepcion = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken());
    }

    @Test
    void consultarIndicadoresSinParametrosDevuelveMesActualYCalculosValidos() throws Exception {
        mvc.perform(get("/api/v1/admin/indicadores")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").isString())
                .andExpect(jsonPath("$.fin").isString())
                .andExpect(jsonPath("$.ocupacionHoy.fecha").isString())
                .andExpect(jsonPath("$.ocupacionHoy.habitacionesOcupadas").isNumber())
                .andExpect(jsonPath("$.ocupacionHoy.habitacionesActivas").isNumber())
                .andExpect(jsonPath("$.ocupacionHoy.porcentaje").isNumber())
                .andExpect(jsonPath("$.ingresos").isNumber())
                .andExpect(jsonPath("$.reservasPorCanal").isArray())
                .andExpect(jsonPath("$.reservasPorCanal.length()").value(4))
                .andExpect(jsonPath("$.reservasPorCanal[0].canal").value("DIRECTO_WEB"))
                .andExpect(jsonPath("$.reservasPorCanal[1].canal").value("RECEPCION"))
                .andExpect(jsonPath("$.reservasPorCanal[2].canal").value("BOOKING"))
                .andExpect(jsonPath("$.reservasPorCanal[3].canal").value("EXPEDIA"));
    }

    @Test
    void consultarIndicadoresConRangoExplicito() throws Exception {
        mvc.perform(get("/api/v1/admin/indicadores")
                .param("inicio", "2026-10-01")
                .param("fin", "2026-10-31")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-10-01"))
                .andExpect(jsonPath("$.fin").value("2026-10-31"))
                .andExpect(jsonPath("$.ingresos").isNumber());
    }

    @Test
    void consultarIndicadoresPeriodoSinActividadDevuelveCerosSinError() throws Exception {
        // Rango en el pasado distante o futuro sin registros
        mvc.perform(get("/api/v1/admin/indicadores")
                .param("inicio", "2020-01-01")
                .param("fin", "2020-01-31")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2020-01-01"))
                .andExpect(jsonPath("$.fin").value("2020-01-31"))
                .andExpect(jsonPath("$.ingresos").value(0.00))
                .andExpect(jsonPath("$.reservasPorCanal[0].cantidad").value(0))
                .andExpect(jsonPath("$.reservasPorCanal[1].cantidad").value(0))
                .andExpect(jsonPath("$.reservasPorCanal[2].cantidad").value(0))
                .andExpect(jsonPath("$.reservasPorCanal[3].cantidad").value(0));
    }

    @Test
    void consultarIndicadoresConFechasInvertidasDevuelveBadRequest400() throws Exception {
        mvc.perform(get("/api/v1/admin/indicadores")
                .param("inicio", "2026-10-31")
                .param("fin", "2026-10-01")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
    }

    @Test
    void consultarIndicadoresRequiereRolAdmin() throws Exception {
        // Sin token -> 401
        mvc.perform(get("/api/v1/admin/indicadores"))
                .andExpect(status().isUnauthorized());

        // Con rol RECEPCION -> 403
        mvc.perform(get("/api/v1/admin/indicadores")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointPrevioReportesSigueFuncionando() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes")
                .param("desde", "2026-10-01")
                .param("hasta", "2026-10-31")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-10-01"))
                .andExpect(jsonPath("$.hasta").value("2026-10-31"))
                .andExpect(jsonPath("$.reservas").isArray())
                .andExpect(jsonPath("$.pagos").isArray())
                .andExpect(jsonPath("$.ocupacionActual").isArray());
    }
}
