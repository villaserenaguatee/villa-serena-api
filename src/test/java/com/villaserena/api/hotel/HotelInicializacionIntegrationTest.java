package com.villaserena.api.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class HotelInicializacionIntegrationTest {
    private static final String RUTA = "/api/v1/admin/hotel/inicializacion";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired HotelService hotel;
    @Autowired TransactionTemplate transacciones;
    @MockitoBean PasarelaPagos pasarela;
    String admin;
    String recepcion;

    @BeforeEach
    void preparar() {
        admin = transacciones.execute(status -> "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
        recepcion = transacciones.execute(status -> "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken());
    }

    @Test
    void creaRepiteNormalizaYConservaFotosSeriesYFacturas() throws Exception {
        jdbc.update("DELETE FROM hotel_configuration");
        jdbc.update("INSERT INTO hotel_photos (object_key, sort_order) VALUES ('hotel/previa.jpg', 7)");
        jdbc.update("UPDATE invoice_series SET last_number = 17");
        var antes = estadoAjeno();
        mvc.perform(get("/api/v1/admin/hotel").header("Authorization", admin)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));
        mvc.perform(get("/api/v1/publico/hotel")).andExpect(status().isNotFound());
        mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(peticion("Hotel prueba", "6-k")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nit").value("6-K"));
        mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(peticion("  Hotel prueba  ", "6K")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Hotel prueba"));
        mvc.perform(get("/api/v1/publico/hotel")).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Hotel prueba"));
        assertThat(estadoAjeno()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_configuration", Integer.class)).isEqualTo(1);
    }

    @Test
    void tampocoAprovisionaEntidadesAusentes() throws Exception {
        jdbc.update("DELETE FROM hotel_configuration");
        jdbc.update("DELETE FROM hotel_photos");
        jdbc.update("DELETE FROM invoice_series");
        var antes = estadoAjeno();
        mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(peticion("Hotel prueba", "6-K"))).andExpect(status().isCreated());
        assertThat(estadoAjeno()).isEqualTo(antes);
    }

    @Test
    void configuracionDistintaNoSeSobrescribeNiSeRestauraAlReintentar() throws Exception {
        var original = hotel.admin();
        var ajeno = estadoAjeno();
        mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(peticion("Distinto", "6-K")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("HOTEL_YA_CONFIGURADO"));
        assertThat(hotel.admin()).isEqualTo(original);
        assertThat(estadoAjeno()).isEqualTo(ajeno);
    }

    @Test
    void putNoInicializaNiTocaFotosOSeries() throws Exception {
        jdbc.update("DELETE FROM hotel_configuration");
        var antes = estadoAjeno();
        String input = peticion("Hotel prueba", "6-K").replace("}", ",\"fotos\":[]}");
        mvc.perform(put("/api/v1/admin/hotel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));
        assertThat(estadoAjeno()).isEqualTo(antes);
    }

    @Test
    void permisosDatosInvalidosYCamposExtraNoCreanConfiguracion() throws Exception {
        jdbc.update("DELETE FROM hotel_configuration");
        String valido = peticion("Hotel prueba", "6-K");
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(valido)).andExpect(status().isUnauthorized());
        mvc.perform(post(RUTA).header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content(valido)).andExpect(status().isForbidden());
        for (String input : List.of(peticion(" ", "6-K"), peticion("Hotel prueba", "CF"), peticion("Hotel prueba", "123"),
                valido.replace("hotel@example.invalid", "invalido"), valido.replace("}", ",\"fotos\":[]}"),
                valido.replace("}", ",\"serie\":\"VS-X\"}"), valido.replace("Descripción de prueba", "x".repeat(4001)))) {
            mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_configuration", Integer.class)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosInicializacionesIgualesSonUnaCreacionYUnaRepeticion() throws Exception {
        concurrentes("Hotel prueba", List.of(200, 201));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosInicializacionesDistintasNoSobrescribenALaGanadora() throws Exception {
        concurrentes("Otro hotel", List.of(201, 409));
    }

    private void concurrentes(String segundo, List<Integer> esperados) throws Exception {
        var original = hotel.admin();
        var antes = estadoAjeno();
        jdbc.update("DELETE FROM hotel_configuration");
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var uno = CompletableFuture.supplyAsync(() -> enviarConcurrente(salida, "Hotel prueba"), pool);
            var dos = CompletableFuture.supplyAsync(() -> enviarConcurrente(salida, segundo), pool);
            salida.countDown();
            assertThat(List.of(uno.get(30, TimeUnit.SECONDS), dos.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrderElementsOf(esperados);
            assertThat(hotel.admin().nombre()).isIn("Hotel prueba", segundo);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_configuration", Integer.class)).isEqualTo(1);
            assertThat(estadoAjeno()).isEqualTo(antes);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            // Restauración exclusiva del contenedor de pruebas, fuera de la transacción de las peticiones.
            jdbc.update("DELETE FROM hotel_configuration");
            jdbc.update("INSERT INTO hotel_configuration (id,name,description,address,phone,email,trade_name,legal_name,tax_id,tax_address) VALUES (1,?,?,?,?,?,?,?,?,?)",
                    original.nombre(), original.descripcion(), original.direccion(), original.telefono(), original.correo(),
                    original.nombreComercial(), original.razonSocial(), original.nit(), original.direccionFiscal());
        }
    }

    private int enviarConcurrente(CountDownLatch salida, String nombre) {
        try {
            if (!salida.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la concurrencia");
            return mvc.perform(post(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(peticion(nombre, "6-K")))
                    .andReturn().getResponse().getStatus();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private Map<String, List<Map<String, Object>>> estadoAjeno() {
        return Map.of("fotos", jdbc.queryForList("SELECT * FROM hotel_photos ORDER BY id"),
                "series", jdbc.queryForList("SELECT * FROM invoice_series ORDER BY id"),
                "facturas", jdbc.queryForList("SELECT * FROM invoices ORDER BY id"));
    }

    private static String peticion(String nombre, String nit) {
        return """
                {"nombre":"%s","descripcion":"Descripción de prueba","direccion":"Dirección de prueba",
                 "telefono":"55551234","correo":"hotel@example.invalid","nombreComercial":"Hotel de prueba",
                 "razonSocial":"Razón de prueba","nit":"%s","direccionFiscal":"Dirección fiscal de prueba"}
                """.formatted(nombre, nit);
    }
}
