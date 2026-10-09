package com.villaserena.api.hotel;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/** Pruebas de OBJ-1A: información pública del hotel y catálogo de tipos, sin sesión. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class CatalogoPublicoIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PropiedadesVillaSerena propiedades;

    @MockitoBean
    PasarelaStripe stripe;

    // ------------------------------------------------------ hotel (HU-HUE-01)

    @Test
    void elHotelSeVeSinSesionConSusFotosYHorasFijas() throws Exception {
        jdbc.update("INSERT INTO fotos_hotel (clave_objeto, orden) VALUES ('hotel/jardin.jpg', 2), "
                + "('hotel/fachada.jpg', 1)");
        String base = propiedades.archivos().urlPublica();
        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Villa Serena"))
                .andExpect(jsonPath("$.descripcion").isNotEmpty())
                .andExpect(jsonPath("$.fotos", contains(base + "/hotel/fachada.jpg", base + "/hotel/jardin.jpg")))
                .andExpect(jsonPath("$.ubicacion.direccion")
                        .value("5a Avenida Norte 12, Antigua Guatemala, Sacatepéquez"))
                .andExpect(jsonPath("$.contacto.telefono").value("+502 7832 0000"))
                .andExpect(jsonPath("$.contacto.correo").value("reservas@villaserena.test"))
                .andExpect(jsonPath("$.horaCheckIn").value("15:00"))
                .andExpect(jsonPath("$.horaCheckOut").value("12:00"))
                // Los datos fiscales no son públicos.
                .andExpect(jsonPath("$.nit").doesNotExist())
                .andExpect(jsonPath("$.razonSocial").doesNotExist());
    }

    @Test
    void sinConfiguracionDelHotelResponde404() throws Exception {
        jdbc.update("DELETE FROM configuracion_hotel");
        mvc.perform(get("/api/v1/publico/hotel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("HOTEL_NO_CONFIGURADO"));
    }

    // -------------------------------------------- catálogo (HU-HUE-02)

    @Test
    void elCatalogoSoloMuestraLosTiposActivos() throws Exception {
        mvc.perform(get("/api/v1/publico/tipos-habitacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].nombre").value("Estándar Jardín"))
                .andExpect(jsonPath("$[0].capacidad").value(2))
                .andExpect(jsonPath("$[0].precioBaseNoche").value(650.00))
                .andExpect(jsonPath("$[0].fotos", hasSize(0)));

        jdbc.update("UPDATE tipos_habitacion SET estado = 'INACTIVO' WHERE nombre = 'Suite Volcán'");
        mvc.perform(get("/api/v1/publico/tipos-habitacion"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Suite Volcán"))));

        jdbc.update("UPDATE tipos_habitacion SET estado = 'INACTIVO'");
        mvc.perform(get("/api/v1/publico/tipos-habitacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elDetalleTraeTodasLasFotosConLaPrincipalPrimero() throws Exception {
        long id = tipoId("Doble Superior");
        jdbc.update("""
                INSERT INTO fotos_tipo_habitacion (tipo_habitacion_id, clave_objeto, es_principal, orden)
                VALUES (?, 'tipos/1/bano.jpg', FALSE, 1), (?, 'tipos/1/principal.jpg', TRUE, 5)""", id, id);
        String base = propiedades.archivos().urlPublica();
        mvc.perform(get("/api/v1/publico/tipos-habitacion/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Doble Superior"))
                .andExpect(jsonPath("$.descripcion").value("Dos camas matrimoniales con vista al jardín."))
                .andExpect(jsonPath("$.capacidad").value(4))
                .andExpect(jsonPath("$.precioBaseNoche").value(850.00))
                .andExpect(jsonPath("$.fotos", contains(base + "/tipos/1/principal.jpg", base + "/tipos/1/bano.jpg")));
    }

    @Test
    void unTipoInactivoOInexistenteResponde404() throws Exception {
        long id = tipoId("Suite Familiar");
        jdbc.update("UPDATE tipos_habitacion SET estado = 'INACTIVO' WHERE id = ?", id);
        mvc.perform(get("/api/v1/publico/tipos-habitacion/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/publico/tipos-habitacion/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/publico/tipos-habitacion/abc")).andExpect(status().isBadRequest());
    }

    private long tipoId(String nombre) {
        return jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = ?", Long.class, nombre);
    }
}
