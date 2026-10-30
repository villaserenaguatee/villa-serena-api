package com.villaserena.api.hotel.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
class EditorialIntegrationTest {
    static final String ADMIN = "/api/v1/admin/hotel/editorial/";
    static final String PUBLICO = "/api/v1/publico/hotel/editorial/";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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

    @Test
    void botonConservaBusquedaEnBorradorYPublicaSoloConConfirmacionDeRevision() throws Exception {
        guardar("es", """
                {"revision":0,"elementos":[{"id":"inicio.boton","texto":"Buscar mi estancia","destino":"/#buscar"}]}
                """, 200);
        mvc.perform(get(ADMIN + "es").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].destino").value("/#buscar"));
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isNotFound());
        publicar("es", 0, 409);
        publicar("es", 1, 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].texto").value("Buscar mi estancia"))
                .andExpect(jsonPath("$.elementos[0].destino").value("/#buscar"));
    }

    private String token(String correo) {
        return transacciones.execute(s -> "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow()).accessToken());
    }

    @Test
    void borradorPrivadoPublicacionExplicitaHistoriaEIdiomasIndependientes() throws Exception {
        mvc.perform(get(ADMIN + "es").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(0)).andExpect(jsonPath("$.elementos").isEmpty());
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isNotFound());
        guardar("es", cuerpo(0, "Título de prueba"), 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isNotFound());
        publicar("es", 1, 200);
        publicar("es", 1, 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_editorial_publications", Integer.class)).isEqualTo(1);
        guardar("es", cuerpo(1, "Borrador nuevo"), 200);
        guardar("en", cuerpo(0, "Test heading"), 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isOk()).andExpect(jsonPath("$.elementos[0].texto").value("Título de prueba"));
        mvc.perform(get(PUBLICO + "en")).andExpect(status().isNotFound());
        publicar("es", 1, 409);
        publicar("es", 2, 200);
        publicar("en", 1, 200);
        mvc.perform(get(PUBLICO + "en")).andExpect(status().isOk()).andExpect(jsonPath("$.elementos[0].texto").value("Test heading"));
        assertThat(jdbc.queryForObject("SELECT content->0->>'texto' FROM hotel_editorial_publications WHERE language='es' AND revision=1", String.class)).isEqualTo("Título de prueba");
    }

    @Test
    void permisosValidacionesYAusenciaNoInventanContenido() throws Exception {
        mvc.perform(get(ADMIN + "es")).andExpect(status().isUnauthorized());
        for (String ruta : List.of(ADMIN + "es", ADMIN + "en")) {
            mvc.perform(get(ruta).header("Authorization", recepcion)).andExpect(status().isForbidden());
            mvc.perform(put(ruta).header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content(cuerpo(0,"Prueba"))).andExpect(status().isForbidden());
            mvc.perform(post(ruta + "/publicacion").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isForbidden());
        }
        for (String idioma : List.of("fr", "ES")) mvc.perform(get(PUBLICO + idioma)).andExpect(status().isBadRequest());
        for (String input : List.of(cuerpo(0,"<script>alert(1)</script>"), cuerpo(0,"x".repeat(201)),
                cuerpo(0,"Prueba").replace("inicio.titulo", "contacto.correo"),
                "{\"revision\":0,\"elementos\":[]}",
                "{\"revision\":0,\"elementos\":[{\"id\":\"inicio.boton\",\"texto\":\"Ir\",\"destino\":\"javascript:alert(1)\"}]}",
                "{\"revision\":0,\"elementos\":[{\"id\":\"inicio.boton\",\"texto\":\"Ir\"}]}",
                cuerpo(0,"Prueba").replace("\"texto\"", "\"html\""),
                "{\"revision\":0,\"elementos\":[{\"id\":\"inicio.titulo\",\"texto\":\"A\"},{\"id\":\"inicio.titulo\",\"texto\":\"B\"}]}"))
            guardar("es", input, 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_editorial_drafts", Integer.class)).isZero();
        jdbc.update("DELETE FROM hotel_configuration");
        guardar("es", cuerpo(0,"Prueba"), 404);
        mvc.perform(get(ADMIN + "es").header("Authorization", admin)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));
    }

    @Test
    void fotografiaDebeSerHotelYFalloAlPublicarConservaVersionAnterior() throws Exception {
        guardar("es", cuerpo(0,"Versión anterior"), 200);
        publicar("es",1,200);
        String clave = "hotel/00000000-0000-0000-0000-000000000011.png";
        String foto = "{\"revision\":1,\"elementos\":[{\"id\":\"historia.imagen\",\"fotoClave\":\"" + clave + "\"}]}";
        guardar("es",foto,400);
        jdbc.update("INSERT INTO hotel_photos(object_key,sort_order) VALUES (?,0)",clave);
        almacen.guardarPublico(clave,new byte[]{1},"image/png");
        guardar("es",foto,200);
        doThrow(new IllegalStateException("Fallo de almacenamiento de prueba")).when(almacen).existePublico(clave);
        publicar("es",2,503);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.elementos[0].texto").value("Versión anterior"));
        mvc.perform(get(ADMIN + "es").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_editorial_publications",Integer.class)).isEqualTo(1);
    }

    @Test
    void albumOrdenadoEnBorradorNoCambiaGaleriaNiCatalogoYPublicacionEsExplicita() throws Exception {
        String a = "hotel/00000000-0000-0000-0000-000000000021.png";
        String b = "hotel/00000000-0000-0000-0000-000000000022.png";
        almacen.guardarPublico(a, new byte[]{1}, "image/png");
        almacen.guardarPublico(b, new byte[]{2}, "image/png");
        var galeria = jdbc.queryForList("SELECT * FROM hotel_photos ORDER BY id");
        var tipos = jdbc.queryForList("SELECT * FROM room_type_photos ORDER BY id");
        String contenido = "{\"revision\":0,\"elementos\":[{\"id\":\"inicio.imagenes\",\"fotoClaves\":[\"" + b + "\",\"" + a + "\"]}]}";
        mvc.perform(put(ADMIN + "es").header("Authorization", recepcion).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isForbidden());
        guardar("es", contenido, 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT * FROM hotel_photos ORDER BY id")).isEqualTo(galeria);
        assertThat(jdbc.queryForList("SELECT * FROM room_type_photos ORDER BY id")).isEqualTo(tipos);
        publicar("es", 1, 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].fotoClaves[0]").value(b));
        guardar("es", "{\"revision\":1,\"elementos\":[{\"id\":\"inicio.imagenes\",\"fotoClaves\":[]}]}", 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(jsonPath("$.elementos[0].fotoClaves.length()").value(2));
        publicar("es", 1, 409);
        publicar("es", 2, 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(jsonPath("$.elementos[0].fotoClaves").isEmpty());
        assertThat(almacen.existePublico(a)).isTrue();
        assertThat(almacen.existePublico(b)).isTrue();
    }

    @Test
    void rechazaAlbumDuplicadoPosicionIndividualMultipleYClaveAjena() throws Exception {
        for (String elemento : List.of(
                "{\"id\":\"inicio.imagenes\",\"fotoClaves\":[\"tipos/ajena.png\"]}",
                "{\"id\":\"servicios.spa.imagen\",\"fotoClaves\":[\"a\",\"b\"]}",
                "{\"id\":\"inicio.imagenes\",\"fotoClaves\":[\"a\",\"a\"]}",
                "{\"id\":\"inicio.imagenes\",\"fotoClaves\":[],\"texto\":\"mezcla\"}"))
            guardar("es", "{\"revision\":0,\"elementos\":[" + elemento + "]}", 400);
    }

    @Test
    void conservaClavesAnterioresRegistradasSinRenombrarlasNiBorrarArchivos() throws Exception {
        String anterior = "hotel/fachada-anterior.jpg";
        jdbc.update("INSERT INTO hotel_photos(object_key,sort_order) VALUES (?,0)", anterior);
        almacen.guardarPublico(anterior, new byte[]{1}, "image/jpeg");
        guardar("es", "{\"revision\":0,\"elementos\":[{\"id\":\"galeria-hotel.imagenes\",\"fotoClaves\":[\"" + anterior + "\"]}]}", 200);
        publicar("es", 1, 200);
        mvc.perform(get(PUBLICO + "es")).andExpect(jsonPath("$.elementos[0].fotoClaves[0]").value(anterior));
        assertThat(almacen.existePublico(anterior)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_photos WHERE object_key=?", Integer.class, anterior)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persisteEntrePeticionesYSesionesSinSobrescrituraConcurrente() throws Exception {
        var antes = jdbc.queryForList("SELECT * FROM hotel_configuration");
        var fotos = jdbc.queryForList("SELECT * FROM hotel_photos ORDER BY id");
        var salida = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = CompletableFuture.supplyAsync(() -> guardarConcurrente(salida,"Uno"),pool);
            var b = CompletableFuture.supplyAsync(() -> guardarConcurrente(salida,"Dos"),pool);
            salida.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            // Nueva sesión y transacción: la respuesta se recupera de PostgreSQL, no de estado en memoria.
            String nuevaSesion = token("sofia.morales@villaserena.test");
            mvc.perform(get(ADMIN + "es").header("Authorization",nuevaSesion)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
            Long persistida = transacciones.execute(s -> jdbc.queryForObject("SELECT revision FROM hotel_editorial_drafts WHERE language='es'",Long.class));
            assertThat(persistida).isEqualTo(1L);
            publicar("es",1,200);
            mvc.perform(get(PUBLICO + "es")).andExpect(status().isOk());
            assertThat(jdbc.queryForList("SELECT * FROM hotel_configuration")).isEqualTo(antes);
            assertThat(jdbc.queryForList("SELECT * FROM hotel_photos ORDER BY id")).isEqualTo(fotos);
        } finally {
            pool.shutdownNow(); pool.awaitTermination(5,TimeUnit.SECONDS);
            jdbc.update("DELETE FROM hotel_editorial_publications WHERE language='es'");
            jdbc.update("DELETE FROM hotel_editorial_drafts WHERE language='es'");
        }
    }

    private int guardarConcurrente(CountDownLatch salida,String texto) {
        try {
            if (!salida.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("No comenzó la prueba");
            return mvc.perform(put(ADMIN + "es").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo(0,texto))).andReturn().getResponse().getStatus();
        } catch(Exception error) { throw new IllegalStateException(error); }
    }

    private void guardar(String idioma,String cuerpo,int estado) throws Exception {
        mvc.perform(put(ADMIN + idioma).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().is(estado));
    }

    private void publicar(String idioma,long revision,int estado) throws Exception {
        mvc.perform(post(ADMIN + idioma + "/publicacion").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":" + revision + "}")).andExpect(status().is(estado));
    }

    private String cuerpo(long revision,String texto) {
        return "{\"revision\":" + revision + ",\"elementos\":[{\"id\":\"inicio.titulo\",\"texto\":\"" + texto + "\"}]}";
    }
}
