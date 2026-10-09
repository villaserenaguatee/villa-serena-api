package com.villaserena.api.canal;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pruebas de OBJ-1C parte B (HU-CM-01): autenticación por clave, validaciones,
 * idempotencia y el correo que queda encolado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class CanalIntegrationTest {

    /** Su SHA-256 es el CANAL1_KEY_HASH de application-test.yaml. */
    private static final String CLAVE_BOOKING = "clave-booking-pruebas";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PasarelaStripe stripe;

    private static String peticion(String identificador, String entrada, String salida, int huespedes) {
        return """
                {
                  "identificadorExterno": "%s",
                  "tipoHabitacionId": 1,
                  "entrada": "%s",
                  "salida": "%s",
                  "numeroHuespedes": %d,
                  "montoTotal": 1800.00,
                  "huesped": {
                    "nombreCompleto": "John Smith",
                    "correo": "john.smith@example.com",
                    "telefono": "+1 555 0100",
                    "nacionalidad": "Estadounidense",
                    "tipoDocumento": "PASAPORTE",
                    "numeroDocumento": "567890123"
                  }
                }""".formatted(identificador, entrada, salida, huespedes);
    }

    private org.springframework.test.web.servlet.ResultActions enviar(String clave, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/canal/reservas")
                .header(CanalReservasController.ENCABEZADO_CODIGO, "BOOKING")
                .header(CanalReservasController.ENCABEZADO_CLAVE, clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    @Test
    void creaLaReservaConPagoDelCanalYEncolaElCorreo() throws Exception {
        String respuesta = enviar(CLAVE_BOOKING, peticion("BK-1001", "2026-11-03", "2026-11-05", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.canal").value("BOOKING"))
                .andExpect(jsonPath("$.identificadorExterno").value("BK-1001"))
                .andExpect(jsonPath("$.montoTotal").value(1800.00))
                .andExpect(jsonPath("$.tipoHabitacion.nombre").value("Doble Superior"))
                .andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(respuesta, "$.codigo");

        // El alojamiento es el monto del canal, en una sola línea (RN-TAR-010), y el
        // pago queda APROBADO con método CANAL (RN-PAG-008).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM reserva_noches rn JOIN reservas r ON r.id = rn.reserva_id
                WHERE r.codigo = ?""", Integer.class, codigo)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT p.metodo || ':' || p.estado || ':' || p.monto
                FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id JOIN reservas r ON r.id = c.reserva_id
                WHERE r.codigo = ?""", String.class, codigo)).isEqualTo("CANAL:APROBADO:1800.00");

        // El correo de confirmación quedó PENDIENTE en el outbox, con los datos del huésped.
        assertThat(jdbc.queryForObject("""
                SELECT estado FROM outbox
                WHERE tipo = 'CORREO_CONFIRMACION' AND destinatario = 'john.smith@example.com'
                ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("""
                SELECT payload ->> 'codigo' FROM outbox
                WHERE tipo = 'CORREO_CONFIRMACION' ORDER BY id DESC LIMIT 1""", String.class)).isEqualTo(codigo);
    }

    @Test
    void elMismoIdentificadorNoCreaOtraReserva() throws Exception {
        String primera = enviar(CLAVE_BOOKING, peticion("BK-2002", "2026-11-10", "2026-11-12", 2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String repetida = enviar(CLAVE_BOOKING, peticion("BK-2002", "2026-11-10", "2026-11-12", 2))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(repetida, "$.codigo"))
                .isEqualTo(JsonPath.<String>read(primera, "$.codigo"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reservas WHERE canal = 'BOOKING' AND identificador_externo = ?",
                Integer.class, "BK-2002")).isEqualTo(1);
        // Tampoco se encola un segundo correo.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox
                WHERE tipo = 'CORREO_CONFIRMACION' AND destinatario = 'john.smith@example.com'""",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void claveIncorrectaOCanalInexistenteNoCreanNada() throws Exception {
        enviar("clave-equivocada", peticion("BK-3003", "2026-11-10", "2026-11-12", 2))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CANAL_NO_AUTORIZADO"));

        mvc.perform(post("/api/v1/canal/reservas")
                        .header(CanalReservasController.ENCABEZADO_CODIGO, "AIRBNB")
                        .header(CanalReservasController.ENCABEZADO_CLAVE, CLAVE_BOOKING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peticion("BK-3004", "2026-11-10", "2026-11-12", 2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CANAL_NO_AUTORIZADO"));

        mvc.perform(post("/api/v1/canal/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(peticion("BK-3005", "2026-11-10", "2026-11-12", 2)))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas WHERE identificador_externo LIKE 'BK-300%'",
                Integer.class)).isZero();
    }

    @Test
    void laClaveSeRevisaAntesQueLosDatos() throws Exception {
        // Clave mala Y datos incompletos: manda el 401, no el 400.
        enviar("clave-equivocada", """
                {"identificadorExterno": "", "tipoHabitacionId": null}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CANAL_NO_AUTORIZADO"));
    }

    @Test
    void datosIncompletosDelHuespedResponden400() throws Exception {
        enviar(CLAVE_BOOKING, """
                {
                  "identificadorExterno": "BK-4004",
                  "tipoHabitacionId": 1,
                  "entrada": "2026-11-03",
                  "salida": "2026-11-05",
                  "numeroHuespedes": 2,
                  "montoTotal": 1800.00,
                  "huesped": {
                    "nombreCompleto": "John Smith",
                    "correo": "esto-no-es-un-correo",
                    "telefono": "",
                    "nacionalidad": "Estadounidense",
                    "tipoDocumento": "PASAPORTE",
                    "numeroDocumento": "567890123"
                  }
                }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles[*].campo")
                        .value(org.hamcrest.Matchers.hasItems("huesped.correo", "huesped.telefono")));
    }

    @Test
    void fechasYCapacidadSeValidanIgualQueEnLaWeb() throws Exception {
        // 31 noches (el máximo es 30, RN-RES-006).
        enviar(CLAVE_BOOKING, peticion("BK-5005", "2026-11-01", "2026-12-02", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));

        // Fecha pasada: el reloj de las pruebas está en el 5 de octubre de 2026.
        enviar(CLAVE_BOOKING, peticion("BK-5006", "2026-09-01", "2026-09-03", 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));

        // Doble Superior (tipo 1) tiene capacidad 4.
        enviar(CLAVE_BOOKING, peticion("BK-5007", "2026-11-03", "2026-11-05", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles[0].campo").value("numeroHuespedes"));
    }

    @Test
    void sinDisponibilidadResponde409() throws Exception {
        // Ocupa todas las habitaciones del tipo 1 en esas fechas.
        Integer habitaciones = jdbc.queryForObject("""
                SELECT count(*) FROM habitaciones
                WHERE tipo_habitacion_id = 1 AND estado = 'ACTIVO' AND condicion <> 'FUERA_DE_SERVICIO'""",
                Integer.class);
        for (int i = 0; i < habitaciones; i++) {
            enviar(CLAVE_BOOKING, peticion("BK-600" + i, "2026-11-20", "2026-11-22", 2))
                    .andExpect(status().isCreated());
        }

        enviar(CLAVE_BOOKING, peticion("BK-6099", "2026-11-20", "2026-11-22", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_DISPONIBILIDAD"));
    }
}
