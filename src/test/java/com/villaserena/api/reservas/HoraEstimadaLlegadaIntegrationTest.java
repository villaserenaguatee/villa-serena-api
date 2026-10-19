package com.villaserena.api.reservas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.huespedes.HuespedDatos;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

/** Issue #57: creación pública, persistencia y lectura autorizada de la hora opcional. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class HoraEstimadaLlegadaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;
    @MockitoBean PasarelaPagos pasarela;

    @ParameterizedTest
    @CsvSource({"false,00:00", "false,09:05", "false,23:59", "true,00:00", "true,18:30", "true,23:59"})
    void guardaLaHoraLocalYRecepcionLaRecibeSinSegundos(boolean oferta, String hora) throws Exception {
        Map<String, Object> body = peticion(oferta);
        body.put("horaEstimadaLlegada", hora);
        String codigo = codigo(crear(oferta, body, intento(body)));
        assertThat(jdbc.queryForObject("SELECT hora_estimada_llegada FROM reservas WHERE codigo = ?",
                LocalTime.class, codigo)).isEqualTo(LocalTime.parse(hora));
        entityManager.clear();
        detalle(codigo).andExpect(status().isOk())
                .andExpect(jsonPath("$.horaEstimadaLlegada").value(hora));
        // 00:00 también se acepta: es una estimación, no cambia la política del hotel.
        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(jsonPath("$.horaCheckIn").value("15:00"));
        mvc.perform(get("/api/v1/publico/reservas/{codigo}/estado", codigo))
                .andExpect(jsonPath("$.horaEstimadaLlegada").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void omitidaONulaNoAsignaUnValorPorDefecto(boolean oferta, boolean explicita) throws Exception {
        Map<String, Object> body = peticion(oferta);
        if (explicita) body.put("horaEstimadaLlegada", null);
        String codigo = codigo(crear(oferta, body, intento(body)));
        assertThat(jdbc.queryForObject("SELECT hora_estimada_llegada FROM reservas WHERE codigo = ?",
                LocalTime.class, codigo)).isNull();
        detalle(codigo).andExpect(status().isOk())
                .andExpect(jsonPath("$.horaEstimadaLlegada").value(nullValue()));
    }

    static Stream<Arguments> horasInvalidas() {
        return Stream.of(false, true).flatMap(oferta -> Stream.of("24:00", "12:60", "9:30", "09:5",
                "18:30:00", "18:30Z", "18:30-06:00", "", " 18:30 ", "mañana")
                .map(hora -> Arguments.of(oferta, hora)));
    }

    @ParameterizedTest
    @MethodSource("horasInvalidas")
    void rechazaHorasInvalidasSinCrearReservaNiConsumirElComprobante(boolean oferta, String hora) throws Exception {
        Map<String, Object> body = peticion(oferta);
        body.put("horaEstimadaLlegada", hora);
        Intento intento = intento(body);
        int anteriores = jdbc.queryForObject("SELECT count(*) FROM reservas", Integer.class);
        crear(oferta, body, intento).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("horaEstimadaLlegada")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas", Integer.class)).isEqualTo(anteriores);
        body.put("horaEstimadaLlegada", "18:30");
        codigo(crear(oferta, body, intento));
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void elReenvioNoDuplicaYCambiarLaHoraConLaMismaClaveDaConflicto(boolean oferta) throws Exception {
        Map<String, Object> body = peticion(oferta);
        body.put("horaEstimadaLlegada", "18:30");
        Intento intento = intento(body);
        String codigo = codigo(crear(oferta, body, intento));
        assertThat(codigo(crear(oferta, body, intento))).isEqualTo(codigo);
        body.put("horaEstimadaLlegada", "19:00");
        crear(oferta, body, intento).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BOOKING_REQUEST_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas WHERE codigo = ?", Integer.class, codigo))
                .isEqualTo(1);
        detalle(codigo).andExpect(jsonPath("$.horaEstimadaLlegada").value("18:30"));
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void reintentosDeLaVersionAnteriorSiguenFuncionandoSinHora(boolean oferta) throws Exception {
        Map<String, Object> body = peticion(oferta);
        Intento intento = intento(body);
        String codigo = codigo(crear(oferta, body, intento));
        // Representa exactamente el record previo a #57, sin el nuevo componente.
        PeticionAnterior anterior = json.convertValue(body, PeticionAnterior.class);
        Object huellaAnterior = oferta ? Map.of("slug", slug(), "reserva", anterior) : anterior;
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(huellaAnterior).getBytes(StandardCharsets.UTF_8)));
        jdbc.update("UPDATE reservas_solicitudes SET huella = ? WHERE clave = ?", hash, intento.clave());
        body.put("horaEstimadaLlegada", null);
        assertThat(codigo(crear(oferta, body, intento))).isEqualTo(codigo);
    }

    @Test
    void elDetalleSigueRestringidoARecepcion() throws Exception {
        Map<String, Object> body = peticion(false);
        body.put("horaEstimadaLlegada", "18:30");
        String codigo = codigo(crear(false, body, intento(body)));
        mvc.perform(get("/api/v1/reservas/{codigo}", codigo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reservas/{codigo}", codigo)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void laMigracionNoRellenaNiReescribeLasReservasAnteriores() throws Exception {
        Map<String, Object> body = peticion(false);
        String codigo = codigo(crear(false, body, intento(body)));
        // Copia temporal de una fila real con la estructura anterior a V28.
        // Solo vive en esta conexión/transacción; no altera el esquema de las demás pruebas.
        jdbc.execute("CREATE TEMP TABLE reservas ON COMMIT DROP AS SELECT * FROM public.reservas");
        jdbc.execute("ALTER TABLE reservas DROP COLUMN hora_estimada_llegada");
        String antes = jdbc.queryForObject("SELECT to_jsonb(r)::text FROM reservas r WHERE codigo = ?",
                String.class, codigo);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__hora_estimada_llegada.sql"))
                .execute(dataSource);
        assertThat(jdbc.queryForObject("SELECT (to_jsonb(r) - 'hora_estimada_llegada')::text "
                + "FROM reservas r WHERE codigo = ?", String.class, codigo)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT hora_estimada_llegada FROM reservas WHERE codigo = ?",
                LocalTime.class, codigo)).isNull();
        assertThat(jdbc.queryForObject("SELECT column_default FROM information_schema.columns "
                + "WHERE table_schema = (SELECT nspname FROM pg_namespace WHERE oid = pg_my_temp_schema()) "
                + "AND table_name = 'reservas' AND column_name = 'hora_estimada_llegada'", String.class)).isNull();
    }

    private Map<String, Object> peticion(boolean oferta) {
        Long tipo = jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = 'Estándar Jardín'", Long.class);
        if (oferta) tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM catalogo_ofertas WHERE slug = ?",
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
        return jdbc.queryForObject("SELECT slug FROM catalogo_ofertas ORDER BY orden LIMIT 1", String.class);
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
                INSERT INTO reserva_correo_verificaciones (id, correo, borrador, codigo_hash, creado_en, expira_en,
                    verificado_en, comprobante_hash, comprobante_expira_en)
                VALUES (?, ?, ?, 'NO_APLICA', ?, ?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), ?)
                """, UUID.randomUUID(), ((Map<?, ?>) body.get("huesped")).get("correo"), borrador,
                Timestamp.from(RelojFijoConfig.AHORA), Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(600)),
                Timestamp.from(RelojFijoConfig.AHORA), comprobante,
                Timestamp.from(RelojFijoConfig.AHORA.plusSeconds(1800)));
        return new Intento(UUID.randomUUID().toString(), borrador, comprobante);
    }

    private record Intento(String clave, UUID borrador, String comprobante) {}
    private record PeticionAnterior(Long tipoHabitacionId, LocalDate entrada, LocalDate salida,
            Integer numeroHuespedes, HuespedDatos huesped) {}
}
