package com.villaserena.api.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
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
class HotelGaleriaIntegrationTest {
    static final String RUTA = "/api/v1/admin/hotel";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired HotelService hotel;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired TransactionTemplate transacciones;
    @MockitoSpyBean AlmacenEnMemoria.Almacen almacen;
    @MockitoBean PasarelaPagos pasarela;
    String admin;
    String recepcion;

    @BeforeEach
    void preparar() {
        admin = token("sofia.morales@villaserena.test");
        recepcion = token("ana.perez@villaserena.test");
    }

    private String token(String correo) {
        return transacciones.execute(s -> "Bearer " + tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase(correo).orElseThrow()).accessToken());
    }

    @Test
    void cargaAsociaOrdenaReemplazaYRetiraSinBorrarObjetos() throws Exception {
        String primera = subir(), segunda = subir();
        String fotos = fotos(primera, segunda);
        var respuesta = mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", revision())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotos[0].clave").value(primera))
                .andReturn().getResponse();
        String vigente = respuesta.getHeader("ETag");
        assertThat(hotel.admin().fotos()).extracting(FotoHotelPeticion::clave).containsExactly(primera, segunda);
        mvc.perform(get("/api/v1/publico/hotel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos[0]").value(org.hamcrest.Matchers.endsWith(primera)));
        mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", vigente)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos(segunda, primera))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotos[0].clave").value(segunda));
        mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", revision())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("[]"))).andExpect(status().isOk());
        assertThat(hotel.admin().fotos()).isEmpty();
        assertThat(almacen.existePublico(primera)).isTrue();
        assertThat(almacen.existePublico(segunda)).isTrue();
    }

    @Test
    void revisionObligatoriaSoloAlCambiarGaleriaYConflictoNoModificaDatos() throws Exception {
        String clave = subir(), anterior = revision();
        mvc.perform(put(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(fotos(clave)))).andExpect(status().isConflict());
        mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", anterior)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos(clave)))).andExpect(status().isOk());
        var guardado = hotel.admin();
        mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", anterior)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("[]").replace("Hotel de prueba", "Cambio conflictivo")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("GALERIA_CAMBIADA"));
        assertThat(hotel.admin()).isEqualTo(guardado);
        // Fotos omitidas o idénticas conservan la compatibilidad sin revisión.
        mvc.perform(put(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(null))).andExpect(status().isOk());
        mvc.perform(put(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(fotos(clave)))).andExpect(status().isOk());
        assertThat(hotel.admin().fotos()).isEqualTo(guardado.fotos());
        mvc.perform(put(RUTA).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("null"))).andExpect(status().isBadRequest());
    }

    @Test
    void rechazaReferenciasAjenasDuplicadasOInexistentesSinPerderGaleria() throws Exception {
        var original = hotel.admin();
        for (String fotos : List.of(fotos("tipos/otra.jpg"), fotos("https://externo.test/foto.png"),
                fotos("hotel/00000000-0000-0000-0000-000000000099.jpg"), fotos(subir(), subir()).replace("\"orden\":1", "\"orden\":0"))) {
            mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", revision())
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos))).andExpect(status().isBadRequest());
            assertThat(hotel.admin()).isEqualTo(original);
        }
    }

    @Test
    void cargaValidaContenidoTamanoYPermisos() throws Exception {
        byte[] valida = png();
        mvc.perform(multipart("/api/v1/archivos/imagenes").param("uso", "HOTEL")
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", valida))).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/archivos/imagenes").param("uso", "HOTEL").header("Authorization", recepcion)
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", valida))).andExpect(status().isForbidden());
        for (byte[] invalida : List.of(new byte[0], new byte[]{(byte)255, (byte)216, (byte)255}, new byte[5 * 1024 * 1024 + 1])) {
            mvc.perform(multipart("/api/v1/archivos/imagenes").param("uso", "HOTEL").header("Authorization", admin)
                    .file(new MockMultipartFile("archivo", "foto.png", "image/png", invalida))).andExpect(status().isBadRequest());
        }
        mvc.perform(put(RUTA).header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(null))).andExpect(status().isForbidden());
    }

    @Test
    void falloDeAlmacenamientoNoGuardaDatosNiReferencias() throws Exception {
        var original = hotel.admin();
        String clave = subir();
        doThrow(new IllegalStateException("Almacén de prueba no disponible")).when(almacen).existePublico(clave);
        mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", revision())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos(clave))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("ALMACEN_NO_DISPONIBLE"));
        assertThat(hotel.admin()).isEqualTo(original);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosAdministradoresNoPuedenSobrescribirUnaRevision() throws Exception {
        var original = hotel.admin();
        String uno = subir(), dos = subir(), revision = revision();
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = CompletableFuture.supplyAsync(() -> concurrente(salida, uno, revision), pool);
            var b = CompletableFuture.supplyAsync(() -> concurrente(salida, dos, revision), pool);
            salida.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(hotel.admin().fotos()).hasSize(1);
            assertThat(hotel.admin().fotos().getFirst().clave()).isIn(uno, dos);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
            // Solo restaura el PostgreSQL desechable de Testcontainers.
            jdbc.update("DELETE FROM hotel_photos");
            for (var foto : original.fotos()) jdbc.update("INSERT INTO hotel_photos(object_key,sort_order) VALUES (?,?)", foto.clave(), foto.orden());
            hotel.actualizar(new HotelAdminPeticion(original.nombre(), original.descripcion(), original.direccion(), original.telefono(),
                    original.correo(), original.nombreComercial(), original.razonSocial(), original.nit(), original.direccionFiscal(), null));
        }
    }

    private int concurrente(CountDownLatch salida, String clave, String revision) {
        try {
            if (!salida.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba");
            return mvc.perform(put(RUTA).header("Authorization", admin).header("If-Match", revision)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo(fotos(clave)))).andReturn().getResponse().getStatus();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private String revision() throws Exception {
        return mvc.perform(get(RUTA).header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }

    private String subir() throws Exception {
        var respuesta = mvc.perform(multipart("/api/v1/archivos/imagenes").param("uso", "HOTEL").header("Authorization", admin)
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", png())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String clave = JsonPath.read(respuesta, "$.clave");
        assertThat(clave).matches("hotel/[0-9a-f-]{36}\\.png");
        assertThat(almacen.existePublico(clave)).isTrue();
        return clave;
    }

    private byte[] png() throws Exception {
        var salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }

    private String fotos(String... claves) {
        var elementos = new java.util.ArrayList<String>();
        for (int i = 0; i < claves.length; i++) elementos.add("{\"clave\":\"" + claves[i] + "\",\"orden\":" + i + "}");
        return "[" + String.join(",", elementos) + "]";
    }

    private String cuerpo(String fotos) {
        return """
                {"nombre":"Hotel de prueba","descripcion":"Descripción de prueba","direccion":"Dirección de prueba",
                "telefono":"55551234","correo":"hotel@example.invalid","nombreComercial":"Hotel de prueba",
                "razonSocial":"Razón de prueba","nit":"6-K","direccionFiscal":"Dirección fiscal de prueba"%s}
                """.formatted(fotos == null ? "" : ",\"fotos\":" + fotos);
    }
}
