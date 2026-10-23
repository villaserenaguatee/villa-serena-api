package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class TiposHabitacionAdminIntegrationTest {

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired AlmacenEnMemoria.Almacen almacen;
    @Autowired PropiedadesVillaSerena propiedades;
    @MockitoBean PasarelaPagos pasarela;

    private String tokenAdmin;
    private String tokenRecepcion;
    private long tipoId;

    @BeforeEach
    void setup() {
        almacen.objetos().clear();
        tokenAdmin = "Bearer " + tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken();
        tokenRecepcion = "Bearer " + tokens.emitirParaEmpleado(
                empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken();

        tipoId = jdbc.queryForObject("SELECT id FROM room_types ORDER BY id LIMIT 1", Long.class);
    }

    @Test
    void adminPuedeSubirFotoTipoHabitacionConPrefijoTipos() throws Exception {
        var respuesta = mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", "suite-principal.jpg", "image/jpeg", JPG))
                        .param("uso", "TIPO_HABITACION")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave", startsWith("tipos/")))
                .andExpect(jsonPath("$.clave", containsString(".jpg")))
                .andReturn().getResponse().getContentAsString();

        String clave = JsonPath.read(respuesta, "$.clave");
        assertThat(almacen.existePublico(clave)).isTrue();
    }

    @Test
    void soloAdminPuedeSubirFotoDeTipoHabitacion() throws Exception {
        mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", "suite.jpg", "image/jpeg", JPG))
                        .param("uso", "TIPO_HABITACION")
                        .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", "suite.jpg", "image/jpeg", JPG))
                        .param("uso", "TIPO_HABITACION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subidaRechazaArchivosNoPermitidos() throws Exception {
        mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", "suite.txt", "text/plain", "texto falso".getBytes()))
                        .param("uso", "TIPO_HABITACION")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminActualizaGaleriaYFotoPrincipalDelTipoExitosamente() throws Exception {
        String clave1 = subirFoto("foto1.jpg", JPG);
        String clave2 = subirFoto("foto2.png", PNG);

        int versionInicial = jdbc.queryForObject("SELECT version FROM room_types WHERE id = ?", Integer.class, tipoId);

        String json = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 },
                    { "clave": "%s", "principal": false, "orden": 1 }
                  ]
                }
                """.formatted(clave1, clave2);

        String base = propiedades.archivos().urlPublica();
        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) tipoId)))
                .andExpect(jsonPath("$.fotos", hasSize(2)))
                .andExpect(jsonPath("$.fotos[0].clave", is(clave1)))
                .andExpect(jsonPath("$.fotos[0].url", is(base + "/" + clave1)))
                .andExpect(jsonPath("$.fotos[0].principal", is(true)))
                .andExpect(jsonPath("$.fotos[0].orden", is(0)))
                .andExpect(jsonPath("$.fotos[1].clave", is(clave2)))
                .andExpect(jsonPath("$.fotos[1].url", is(base + "/" + clave2)))
                .andExpect(jsonPath("$.fotos[1].principal", is(false)))
                .andExpect(jsonPath("$.fotos[1].orden", is(1)));

        int versionFinal = jdbc.queryForObject("SELECT version FROM room_types WHERE id = ?", Integer.class, tipoId);
        assertThat(versionFinal).isEqualTo(versionInicial + 1);

        List<String> clavesEnDb = jdbc.queryForList(
                "SELECT object_key FROM room_type_photos WHERE room_type_id = ? ORDER BY sort_order",
                String.class, tipoId);
        assertThat(clavesEnDb).containsExactly(clave1, clave2);
    }

    @Test
    void actualizacionFallaSiNoHayFotoPrincipalOExisteMasDeUna() throws Exception {
        String clave1 = subirFoto("foto1.jpg", JPG);
        String clave2 = subirFoto("foto2.jpg", JPG);

        // Cero principales
        String sinPrincipal = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": false, "orden": 0 },
                    { "clave": "%s", "principal": false, "orden": 1 }
                  ]
                }
                """.formatted(clave1, clave2);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("DATOS_INVALIDOS")))
                .andExpect(jsonPath("$.mensaje", containsString("exactamente una foto principal")));

        // Dos principales
        String dosPrincipales = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 },
                    { "clave": "%s", "principal": true, "orden": 1 }
                  ]
                }
                """.formatted(clave1, clave2);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dosPrincipales))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("DATOS_INVALIDOS")))
                .andExpect(jsonPath("$.mensaje", containsString("exactamente una foto principal")));
    }

    @Test
    void actualizacionFallaSiClaveNoEmpiezaConTiposONoExisteEnAlmacenamiento() throws Exception {
        String claveInexistente = "tipos/no-existe.jpg";
        String claveInvalida = "incidencias/foto.jpg";

        String jsonInexistente = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 }
                  ]
                }
                """.formatted(claveInexistente);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInexistente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("DATOS_INVALIDOS")))
                .andExpect(jsonPath("$.mensaje", containsString("no existe en el almacenamiento")));

        String jsonInvalida = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 }
                  ]
                }
                """.formatted(claveInvalida);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", is("DATOS_INVALIDOS")))
                .andExpect(jsonPath("$.mensaje", containsString("pertenecer a tipos/")));
    }

    @Test
    void actualizacionFallaSiTipoNoExiste() throws Exception {
        String clave = subirFoto("foto.jpg", JPG);
        String json = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 }
                  ]
                }
                """.formatted(clave);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/999999/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpointsDeConsultaAdminRetornanDetalleYListadoConFotos() throws Exception {
        String clave = subirFoto("foto.jpg", JPG);
        String json = """
                {
                  "fotos": [
                    { "clave": "%s", "principal": true, "orden": 0 }
                  ]
                }
                """.formatted(clave);

        mvc.perform(put("/api/v1/admin/tipos-habitacion/" + tipoId + "/fotos")
                        .header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/tipos-habitacion/" + tipoId)
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) tipoId)))
                .andExpect(jsonPath("$.fotos", hasSize(1)))
                .andExpect(jsonPath("$.fotos[0].clave", is(clave)));

        mvc.perform(get("/api/v1/admin/tipos-habitacion")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/admin/tipos-habitacion/" + tipoId)
                        .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());
    }

    private String subirFoto(String nombre, byte[] contenido) throws Exception {
        String cuerpo = mvc.perform(multipart("/api/v1/archivos/imagenes")
                        .file(new MockMultipartFile("archivo", nombre, "image/jpeg", contenido))
                        .param("uso", "TIPO_HABITACION")
                        .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.clave");
    }
}
