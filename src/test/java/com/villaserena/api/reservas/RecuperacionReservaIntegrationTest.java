package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;

import java.sql.Timestamp;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

/** Recuperación de respuestas perdidas sin duplicar ni exponer reservas ajenas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class RecuperacionReservaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;
    @MockitoBean PasarelaPagos pasarela;

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void recuperaElCodigoYEstadoActualSinCrearOtraReserva(boolean oferta) throws Exception {
        var body = peticion(oferta);
        Intento intento = intento(body);
        String codigo = codigo(crear(oferta, body, intento));
        int cantidad = jdbc.queryForObject("SELECT count(*)  FROM reservations", Integer.class);
        jdbc.update("UPDATE reservations SET status='CONFIRMADA' WHERE code=?", codigo);
        entityManager.clear();
        consultar(oferta, intento).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"))
                .andExpect(jsonPath("$.huesped").doesNotExist())
                .andExpect(jsonPath("$.total").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM reservations", Integer.class)).isEqualTo(cantidad);
        assertThat(codigo(crear(oferta, body, intento))).isEqualTo(codigo);
    }

    @Test
    void pruebaAjenaOBorradorDistintoNoRecuperanNiReenvian() throws Exception {
        var body = peticion(false);
        Intento original = intento(body);
        codigo(crear(false, body, original));
        Intento otro = intento(body); // Mismo correo, otro borrador.
        Intento ajeno = new Intento(original.clave(), otro.borrador(), otro.comprobante());
        consultar(false, ajeno).andExpect(status().isNotFound());
        crear(false, body, ajeno).andExpect(status().isNotFound());
        consultar(false, new Intento(original.clave(), original.borrador(), "a".repeat(43)))
                .andExpect(status().isNotFound());
        consultar(true, original).andExpect(status().isNotFound());
        body.put("huesped", Map.of("correo", "otro@correo.test"));
        Intento otroCorreo = intento(body);
        jdbc.update("UPDATE reservation_email_verifications SET draft=? WHERE proof_hash="
                + "encode(sha256(convert_to(?, 'UTF8')), 'hex')", original.borrador(), otroCorreo.comprobante());
        consultar(false, new Intento(original.clave(), original.borrador(), otroCorreo.comprobante()))
                .andExpect(status().isNotFound());
    }

    @Test
    void comprobanteVencidoRequiereRenovarLaVerificacionDelMismoCorreoYBorrador() throws Exception {
        var body = peticion(false);
        Intento original = intento(body);
        String codigo = codigo(crear(false, body, original));
        jdbc.update("UPDATE reservation_email_verifications SET proof_expires_at=? WHERE draft=?",
                Timestamp.from(RelojFijoConfig.AHORA), original.borrador());
        consultar(false, original).andExpect(status().isNotFound());
        Intento renovado = intento(body);
        jdbc.update("UPDATE reservation_email_verifications SET draft=? WHERE draft=?",
                original.borrador(), renovado.borrador());
        consultar(false, new Intento(original.clave(), original.borrador(), renovado.comprobante()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value(codigo));
    }

    @Test
    void intentoInexistenteNoCreaReservaNiConsumeComprobante() throws Exception {
        Intento intento = intento(peticion(false));
        int cantidad = jdbc.queryForObject("SELECT count(*)  FROM reservations", Integer.class);
        consultar(false, intento).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM reservations", Integer.class)).isEqualTo(cantidad);
        assertThat(jdbc.queryForObject("SELECT consumed_at AS \"consumido_en\" FROM reservation_email_verifications WHERE draft=?",
                Timestamp.class, intento.borrador())).isNull();
    }

    @Test
    void intentoHistoricoSinVinculoConservaReplayPeroNoHabilitaConsulta() throws Exception {
        var body = peticion(false);
        Intento intento = intento(body);
        String codigo = codigo(crear(false, body, intento));
        jdbc.update("UPDATE reservation_requests SET verification_id=NULL WHERE key=?", intento.clave());
        consultar(false, intento).andExpect(status().isNotFound());
        assertThat(codigo(crear(false, body, intento))).isEqualTo(codigo);
    }

    @Test
    void creacionEnOtraConexionSeInformaEnProcesoSinEsperarSuResultado() throws Exception {
        Intento intento = intento(peticion(false));
        try (var conexion = dataSource.getConnection();
             var bloqueo = conexion.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?,0))")) {
            bloqueo.setString(1, "publico:" + intento.clave());
            bloqueo.execute();
            try {
                consultar(false, intento).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.codigo").value("SOLICITUD_EN_PROCESO"));
            } finally {
                try (var desbloqueo = conexion.createStatement()) {
                    desbloqueo.execute("SELECT pg_advisory_unlock_all()");
                }
            }
        }
    }

    private ResultActions consultar(boolean oferta, Intento intento) throws Exception {
        return mvc.perform(get("/api/v1/publico/reservas/intento")
                .param("origen", oferta ? "OFERTA" : "RESERVA")
                .header("Idempotency-Key", intento.clave())
                .header("X-Booking-Draft", intento.borrador())
                .header("X-Booking-Email-Verification", intento.comprobante()));
    }

    private Map<String, Object> peticion(boolean oferta) {
        Long tipo = jdbc.queryForObject("SELECT id  FROM room_types WHERE name = 'Estándar Jardín'", Long.class);
        if (oferta) tipo = jdbc.queryForObject("SELECT room_type_id AS \"tipo_habitacion_id\" FROM catalog_offers WHERE slug = ?",
                Long.class, slug());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipoHabitacionId", tipo);
        body.put("entrada", "2026-10-20");
        body.put("salida", "2026-10-22");
        body.put("numeroHuespedes", 1);
        body.put("huesped", Map.of("nombreCompleto", "Llegada Prueba", "correo", "llegada@correo.test",
                "telefono", "+502 5555 0000", "tipoDocumento", "DPI", "numeroDocumento", "1234567890101"));
        return body;
    }

    private String slug() {
        return jdbc.queryForObject("SELECT slug  FROM catalog_offers ORDER BY sort_order LIMIT 1", String.class);
    }

    private ResultActions crear(boolean oferta, Map<String, Object> body, Intento intento) throws Exception {
        String ruta = oferta ? "/api/v1/publico/ofertas/" + slug() + "/reservas" : "/api/v1/publico/reservas";
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .header("Idempotency-Key", intento.clave())
                .header("X-Booking-Draft", intento.borrador())
                .header("X-Booking-Email-Verification", intento.comprobante()));
    }

    private ResultActions detalle(String codigo) throws Exception {
        return mvc.perform(get("/api/v1/reservas/{codigo}", codigo)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RECEPCION"))));
    }

    private String codigo(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.codigo");
    }

    private Intento intento(Map<String, Object> body) {
        UUID borrador = UUID.randomUUID();
        byte[] azar = new byte[32];
        new java.security.SecureRandom().nextBytes(azar);
        String comprobante = Base64.getUrlEncoder().withoutPadding().encodeToString(azar);
        jdbc.update("""
                INSERT INTO reservation_email_verifications (id, email, draft, code_hash, created_at, expires_at,
                    verified_at, proof_hash, proof_expires_at)
                VALUES (?, ?, ?, 'NO_APLICA', ?, ?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), ?)
                """, UUID.randomUUID(), ((Map<?, ?>) body.get("huesped")).get("correo"), borrador,
                Timestamp.from(RelojFijoConfig.AHORA), Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(600)),
                Timestamp.from(RelojFijoConfig.AHORA), comprobante,
                Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(1800)));
        return new Intento(UUID.randomUUID().toString(), borrador, comprobante);
    }

    private record Intento(String clave, UUID borrador, String comprobante) {}
}
