package com.villaserena.api.catalogo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

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

import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.catalogo.dto.CategoriaMenuPeticion;
import com.villaserena.api.catalogo.dto.CrearItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EditarItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
@Transactional
class MenuAdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired AlmacenEnMemoria.Almacen almacen;
    @Autowired PlatformTransactionManager txManager;

    private String tokenAdmin;
    private String tokenRecepcion;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                INSERT INTO menu_categories (name)
                SELECT DISTINCT category FROM menu_items
                WHERE category IS NOT NULL AND TRIM(category) <> ''
                ON CONFLICT (name) DO NOTHING;
                UPDATE menu_items m
                SET category_id = c.id
                FROM menu_categories c
                WHERE m.category = c.name AND m.category_id IS NULL;
                """);

        tokenAdmin = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("sofia.morales@villaserena.test").orElseThrow()).accessToken());
        tokenRecepcion = "Bearer " + new TransactionTemplate(txManager).execute(s ->
                tokens.emitirParaEmpleado(
                        empleados.findByCorreoIgnoreCase("ana.perez@villaserena.test").orElseThrow()).accessToken());
    }

    @Test
    void listarCategoriasAdminDevuelveCategoriasExistentes() throws Exception {
        mvc.perform(get("/api/v1/admin/menu/categorias")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].nombre").isString());
    }

    @Test
    void crearYEditarCategoriaAdminExitosamente() throws Exception {
        String nombreNueva = "Categoria Test " + UUID.randomUUID().toString().substring(0, 6);

        // Crear categoría
        String respCrear = mvc.perform(post("/api/v1/admin/menu/categorias")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CategoriaMenuPeticion(nombreNueva))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value(nombreNueva))
                .andReturn().getResponse().getContentAsString();

        long id = json.readTree(respCrear).get("id").asLong();

        // Editar categoría
        String nombreModificado = nombreNueva + " Editada";
        mvc.perform(patch("/api/v1/admin/menu/categorias/{id}", id)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CategoriaMenuPeticion(nombreModificado))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value(nombreModificado));

        assertThat(jdbc.queryForObject("SELECT name FROM menu_categories WHERE id = ?", String.class, id))
                .isEqualTo(nombreModificado);
    }

    @Test
    void crearCategoriaConNombreDuplicadoDevuelveConflicto409() throws Exception {
        String nombreExistente = jdbc.queryForObject("SELECT name FROM menu_categories ORDER BY id LIMIT 1", String.class);

        mvc.perform(post("/api/v1/admin/menu/categorias")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CategoriaMenuPeticion(nombreExistente))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EXISTENTE"));
    }

    @Test
    void listarItemsAdminConFiltros() throws Exception {
        mvc.perform(get("/api/v1/admin/menu/items")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].nombre").isString())
                .andExpect(jsonPath("$[0].categoriaId").isNumber())
                .andExpect(jsonPath("$[0].disponibilidad").isString())
                .andExpect(jsonPath("$[0].estado").isString());

        // Filtrado por estado ACTIVO
        mvc.perform(get("/api/v1/admin/menu/items")
                .param("estado", "ACTIVO")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void crearEditarYReactivarItemAdmin() throws Exception {
        long categoriaId = jdbc.queryForObject("SELECT id FROM menu_categories ORDER BY id LIMIT 1", Long.class);
        String nombreItem = "Platillo Especial " + UUID.randomUUID().toString().substring(0, 6);

        // Subir foto simulada en memoria con clave menu/...
        String fotoClave = "menu/" + UUID.randomUUID() + ".jpg";
        almacen.guardarPublico(fotoClave, new byte[]{1, 2, 3}, "image/jpeg");

        // Crear ítem
        CrearItemMenuPeticion peticionCrear = new CrearItemMenuPeticion(
                nombreItem,
                "Descripción detallada del platillo",
                categoriaId,
                new BigDecimal("65.50"),
                fotoClave);

        String resp = mvc.perform(post("/api/v1/admin/menu/items")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(peticionCrear)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value(nombreItem))
                .andExpect(jsonPath("$.precio").value(65.50))
                .andExpect(jsonPath("$.categoriaId").value(categoriaId))
                .andExpect(jsonPath("$.fotoClave").value(fotoClave))
                .andExpect(jsonPath("$.disponibilidad").value("DISPONIBLE"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();

        long itemId = json.readTree(resp).get("id").asLong();

        // Editar ítem (precio y descripción)
        EditarItemMenuPeticion peticionEditar = new EditarItemMenuPeticion(
                null,
                "Descripción actualizada",
                null,
                new BigDecimal("70.00"),
                null);

        mvc.perform(patch("/api/v1/admin/menu/items/{id}", itemId)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(peticionEditar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.precio").value(70.00))
                .andExpect(jsonPath("$.descripcion").value("Descripción actualizada"));

        // Desactivar ítem
        mvc.perform(patch("/api/v1/admin/menu/items/{id}/estado", itemId)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new EstadoCatalogoPeticion("INACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        // Activar ítem de nuevo
        mvc.perform(patch("/api/v1/admin/menu/items/{id}/estado", itemId)
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new EstadoCatalogoPeticion("ACTIVO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        // Simular que Room Service agotó el ítem
        jdbc.update("UPDATE menu_items SET availability = 'AGOTADO', exhausted_at = now() WHERE id = ?", itemId);

        // Reactivar ítem por ADMIN
        mvc.perform(post("/api/v1/admin/menu/items/{id}/reactivar", itemId)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponibilidad").value("DISPONIBLE"));

        // Si ya está disponible, un segundo llamado devuelve conflicto 409
        mvc.perform(post("/api/v1/admin/menu/items/{id}/reactivar", itemId)
                .header("Authorization", tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEM_YA_DISPONIBLE"));
    }

    @Test
    void crearItemConNombreDuplicadoDevuelveConflicto409() throws Exception {
        var row = jdbc.queryForMap("SELECT id, name, category_id, price FROM menu_items WHERE category_id IS NOT NULL LIMIT 1");
        long catId = ((Number) row.get("category_id")).longValue();

        CrearItemMenuPeticion p = new CrearItemMenuPeticion(
                (String) row.get("name"),
                "Intento duplicado",
                catId,
                new BigDecimal("50.00"),
                null);

        mvc.perform(post("/api/v1/admin/menu/items")
                .header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(p)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ITEM_EXISTENTE"));
    }

    @Test
    void subidaDeFotosParaMenuPermitidaParaAdminYDenegadaParaOtros() throws Exception {
        byte[] jpgFicticio = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

        // ADMIN puede subir fotos con uso=MENU
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/archivos/imagenes")
                .file(new org.springframework.mock.web.MockMultipartFile("archivo", "menu.jpg", "image/jpeg", jpgFicticio))
                .param("uso", "MENU")
                .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").isString())
                .andExpect(jsonPath("$.clave").value(org.hamcrest.Matchers.startsWith("menu/")));

        // RECEPCION no puede subir fotos con uso=MENU
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/archivos/imagenes")
                .file(new org.springframework.mock.web.MockMultipartFile("archivo", "menu.jpg", "image/jpeg", jpgFicticio))
                .param("uso", "MENU")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void operacionesMenuRequierenRolAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/menu/categorias")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/menu/items")
                .header("Authorization", tokenRecepcion))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/menu/categorias")
                .header("Authorization", tokenRecepcion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CategoriaMenuPeticion("Postres"))))
                .andExpect(status().isForbidden());
    }
}
