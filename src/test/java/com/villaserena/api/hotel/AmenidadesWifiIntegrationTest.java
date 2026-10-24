package com.villaserena.api.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AmenidadesWifiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager txManager;

    private String tokenAdmin;
    private String tokenRecepcion;
    private String tokenHuesped;

    @BeforeEach
    void setUp() {
        tokenAdmin = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
        tokenRecepcion = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken());

        Long huespedId = jdbc.query("SELECT id FROM guests LIMIT 1", rs -> rs.next() ? rs.getLong("id") : null);
        if (huespedId == null) {
            huespedId = jdbc.queryForObject("""
                    INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
                    VALUES ('Huésped Prueba', 'huesped@correo.test', '+502 5555 1234', 'Guatemalteca', 'DPI', '1234567890101')
                    RETURNING id""", Long.class);
        }
        final long idFinal = huespedId;
        tokenHuesped = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaHuesped(idFinal).accessToken());
    }

    @Test
    void adminPuedeListarCrearEditarYCambiarEstadoDeAmenidades() throws Exception {
        // Listar inicial
        mvc.perform(get("/api/v1/admin/amenidades")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Crear amenidad
        AmenidadPeticion crear = new AmenidadPeticion(
                "Piscina Climatizada",
                "Piscina con agua climatizada y vista a los jardines",
                "amenidades/piscina.jpg",
                "07:00 - 21:00",
                "Área de jardines",
                1
        );

        String respuestaCrear = mvc.perform(post("/api/v1/admin/amenidades")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(crear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Piscina Climatizada"))
                .andExpect(jsonPath("$.descripcion").value("Piscina con agua climatizada y vista a los jardines"))
                .andExpect(jsonPath("$.fotoClave").value("amenidades/piscina.jpg"))
                .andExpect(jsonPath("$.horario").value("07:00 - 21:00"))
                .andExpect(jsonPath("$.ubicacion").value("Área de jardines"))
                .andExpect(jsonPath("$.orden").value(1))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(respuestaCrear).get("id").asLong();

        // Editar amenidad
        EditarAmenidadPeticion editar = new EditarAmenidadPeticion(
                "Piscina Semi-Olímpica",
                "Descripción editada",
                null,
                "06:00 - 22:00",
                "Sector A",
                2
        );

        mvc.perform(patch("/api/v1/admin/amenidades/{id}", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(editar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Piscina Semi-Olímpica"))
                .andExpect(jsonPath("$.descripcion").value("Descripción editada"))
                .andExpect(jsonPath("$.horario").value("06:00 - 22:00"))
                .andExpect(jsonPath("$.ubicacion").value("Sector A"))
                .andExpect(jsonPath("$.orden").value(2))
                .andExpect(jsonPath("$.fotoClave").isEmpty());

        // Cambiar estado a INACTIVO
        mvc.perform(patch("/api/v1/admin/amenidades/{id}/estado", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new EstadoCatalogoPeticion("INACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        // Huesped no ve amenidades inactivas
        mvc.perform(get("/api/v1/app/amenidades")
                .header("Authorization", tokenHuesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());

        // Reactivar amenidad a ACTIVO
        mvc.perform(patch("/api/v1/admin/amenidades/{id}/estado", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new EstadoCatalogoPeticion("ACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        // Huesped ahora sí la ve
        mvc.perform(get("/api/v1/app/amenidades")
                .header("Authorization", tokenHuesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].nombre").value("Piscina Semi-Olímpica"));
    }

    @Test
    void validacionesYErroresAmenidades() throws Exception {
        // Nombre vacío en creación -> 400
        AmenidadPeticion nombreVacio = new AmenidadPeticion("", "desc", null, "horario", "ubicacion", 0);
        mvc.perform(post("/api/v1/admin/amenidades")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(nombreVacio)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Orden negativo en creación -> 400
        AmenidadPeticion ordenNegativo = new AmenidadPeticion("Gimnasio", "desc", null, "horario", "ubicacion", -1);
        mvc.perform(post("/api/v1/admin/amenidades")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ordenNegativo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));

        // Editar ID inexistente -> 404
        EditarAmenidadPeticion editar = new EditarAmenidadPeticion("Nuevo", "desc", null, "horario", "ubicacion", 1);
        mvc.perform(patch("/api/v1/admin/amenidades/{id}", 999999L)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(editar)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        // Estado inválido -> 400
        mvc.perform(patch("/api/v1/admin/amenidades/{id}/estado", 1L)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"OTRO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void gestionYConsultaDeWifi() throws Exception {
        // Consultar wifi inicial como admin
        mvc.perform(get("/api/v1/admin/wifi")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreRed").isNotEmpty())
                .andExpect(jsonPath("$.contrasena").isNotEmpty());

        // Actualizar wifi como admin
        Wifi nuevoWifi = new Wifi("VillaSerena-VIP", "SuperClaveSegura2026!");
        mvc.perform(put("/api/v1/admin/wifi")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(nuevoWifi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreRed").value("VillaSerena-VIP"))
                .andExpect(jsonPath("$.contrasena").value("SuperClaveSegura2026!"));

        // Huésped consulta wifi y recibe la nueva configuración
        mvc.perform(get("/api/v1/app/wifi")
                .header("Authorization", tokenHuesped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreRed").value("VillaSerena-VIP"))
                .andExpect(jsonPath("$.contrasena").value("SuperClaveSegura2026!"));

        // Validación de campos vacíos en wifi -> 400
        Wifi wifiInvalido = new Wifi("", "");
        mvc.perform(put("/api/v1/admin/wifi")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(wifiInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void seguridadYControlDeAcceso() throws Exception {
        // Sin token -> 401
        mvc.perform(get("/api/v1/admin/amenidades")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/wifi")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/app/amenidades")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/app/wifi")).andExpect(status().isUnauthorized());

        // Recepción no puede acceder a admin amenidades ni admin wifi -> 403
        mvc.perform(get("/api/v1/admin/amenidades")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/wifi")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        // Admin no puede acceder a endpoints exclusivos de huésped -> 403
        mvc.perform(get("/api/v1/app/amenidades")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/app/wifi")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
    }
}
