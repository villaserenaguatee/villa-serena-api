package com.villaserena.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RegistroHttpTest {
    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 400, 401, 403, 404, 409, 500})
    void registraEstadoYDuracionSinDatosSensibles(int estado, CapturedOutput salida) throws Exception {
        var peticion = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        peticion.setQueryString("token=QUERY_SENSIBLE");
        peticion.addParameter("contrasena", "PARAMETRO_SENSIBLE");
        peticion.addHeader("Authorization", "Bearer CABECERA_SENSIBLE");
        peticion.addHeader("Cookie", "sesion=COOKIE_SENSIBLE");
        peticion.setContent("{\"contrasena\":\"CUERPO_SENSIBLE\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var respuesta = new MockHttpServletResponse();
        new RegistroHttp().doFilter(peticion, respuesta, (req, res) -> {
            ((HttpServletResponse) res).setStatus(estado);
            res.getWriter().write("RESPUESTA_SENSIBLE");
        });
        assertThat(respuesta.getStatus()).isEqualTo(estado);
        assertThat(salida.getOut()).containsPattern("HTTP POST /api/v1/auth/login -> " + estado + " \\(\\d+ ms\\)");
        assertThat(salida.getOut()).doesNotContain("QUERY_SENSIBLE", "PARAMETRO_SENSIBLE", "CABECERA_SENSIBLE",
                "COOKIE_SENSIBLE", "CUERPO_SENSIBLE", "RESPUESTA_SENSIBLE");
    }

    @Test
    void excepcionNoResueltaRegistra500YSePropagaSinImprimirSuMensaje(CapturedOutput salida) {
        var error = new ServletException("MENSAJE_SENSIBLE");
        assertThatThrownBy(() -> new RegistroHttp().doFilter(new MockHttpServletRequest("GET", "/api/v1/prueba"),
                new MockHttpServletResponse(), (req, res) -> { throw error; })).isSameAs(error);
        assertThat(salida.getOut()).contains("HTTP GET /api/v1/prueba -> 500").doesNotContain("MENSAJE_SENSIBLE");
    }

    @Test
    void rutaNoPuedeInyectarOtraLineaEnElLog(CapturedOutput salida) throws Exception {
        new RegistroHttp().doFilter(new MockHttpServletRequest("GET", "/api/v1/prueba\r\nFORJADO"),
                new MockHttpServletResponse(), (req, res) -> {});
        assertThat(salida.getOut()).contains("HTTP GET /api/v1/prueba__FORJADO -> 200")
                .doesNotContain("prueba\r\nFORJADO");
    }
}
