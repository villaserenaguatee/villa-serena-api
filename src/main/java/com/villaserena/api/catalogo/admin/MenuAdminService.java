package com.villaserena.api.catalogo.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.EmptyResultDataAccessException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.catalogo.dto.CategoriaMenuPeticion;
import com.villaserena.api.catalogo.dto.CrearItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EditarItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import com.villaserena.api.catalogo.dto.ItemMenuAdmin;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.roomservice.dto.CategoriaMenu;

@Service
public class MenuAdminService {

    private final JdbcTemplate jdbc;
    private final AlmacenArchivos almacen;
    private final String urlPublica;

    public MenuAdminService(JdbcTemplate jdbc, AlmacenArchivos almacen, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.almacen = almacen;
        this.urlPublica = propiedades.archivos().urlPublica().replaceAll("/+$", "");
    }

    private String fotoUrl(String clave) {
        return clave == null || clave.isBlank() ? null : urlPublica + "/" + clave;
    }

    public List<CategoriaMenu> listarCategorias() {
        sincronizarCategoriasSiEsNecesario();
        return jdbc.query(
                "SELECT id, name AS \"nombre\", franja, hora_inicio, hora_fin FROM menu_categories ORDER BY id",
                (rs, i) -> new CategoriaMenu(rs.getLong("id"), rs.getString("nombre"), rs.getString("franja"), rs.getTime("hora_inicio") == null ? null : rs.getTime("hora_inicio").toLocalTime(), rs.getTime("hora_fin") == null ? null : rs.getTime("hora_fin").toLocalTime()));
    }

    @Transactional
    public CategoriaMenu crearCategoria(CategoriaMenuPeticion peticion) {
        String nombre = peticion.nombre().trim();
        validarNombreCategoriaUnico(nombre, null);

        Long id = jdbc.queryForObject(
                "INSERT INTO menu_categories(name, franja, hora_inicio, hora_fin) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, nombre, franja(peticion), peticion.horaInicio(), peticion.horaFin());
        return new CategoriaMenu(id, nombre, franja(peticion), peticion.horaInicio(), peticion.horaFin());
    }

    @Transactional
    public CategoriaMenu editarCategoria(long id, CategoriaMenuPeticion peticion) {
        obtenerCategoria(id);
        String nombre = peticion.nombre().trim();
        validarNombreCategoriaUnico(nombre, id);

        jdbc.update("UPDATE menu_categories SET name = ?, franja = ?, hora_inicio = ?, hora_fin = ? WHERE id = ?", nombre, franja(peticion), peticion.horaInicio(), peticion.horaFin(), id);
        jdbc.update("UPDATE menu_items SET category = ? WHERE category_id = ?", nombre, id);

        return new CategoriaMenu(id, nombre, franja(peticion), peticion.horaInicio(), peticion.horaFin());
    }

    private String franja(CategoriaMenuPeticion p) {
        if ((p.horaInicio() == null) != (p.horaFin() == null)) {
            throw ApiException.datosInvalidos("Completa ambas horas o deja las dos vacías.",
                    List.of(new DetalleError("horaInicio", "Se requieren inicio y fin juntos.")));
        }
        if (p.horaInicio() != null && !p.horaInicio().isBefore(p.horaFin())) {
            throw ApiException.datosInvalidos("La hora inicial debe ser anterior a la hora final.",
                    List.of(new DetalleError("horaFin", "La hora final debe ser posterior a la inicial.")));
        }
        return p.franja() == null ? "OTROS" : p.franja();
    }

    public List<ItemMenuAdmin> listarItems(Long categoriaId, String estado) {
        sincronizarCategoriasSiEsNecesario();
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.name AS "nombre", m.description AS "descripcion",
                       coalesce(m.category_id, c.id) AS "categoriaId",
                       m.price AS "precio", m.photo_key AS "fotoClave",
                       m.availability AS "disponibilidad", m.status AS "estado"
                FROM menu_items m
                LEFT JOIN menu_categories c ON c.name = m.category
                WHERE 1=1""");

        List<Object> params = new ArrayList<>();
        if (categoriaId != null) {
            sql.append(" AND (m.category_id = ? OR c.id = ?)");
            params.add(categoriaId);
            params.add(categoriaId);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND m.status = ?");
            params.add(estado.toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY m.id");

        return jdbc.query(sql.toString(),
                (rs, i) -> new ItemMenuAdmin(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getLong("categoriaId"),
                        rs.getBigDecimal("precio"),
                        rs.getString("fotoClave"),
                        rs.getString("disponibilidad"),
                        rs.getString("estado"), fotoUrl(rs.getString("fotoClave"))),
                params.toArray());
    }

    public ItemMenuAdmin obtenerItem(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT m.id, m.name AS "nombre", m.description AS "descripcion",
                           coalesce(m.category_id, c.id) AS "categoriaId",
                           m.price AS "precio", m.photo_key AS "fotoClave",
                           m.availability AS "disponibilidad", m.status AS "estado"
                    FROM menu_items m
                    LEFT JOIN menu_categories c ON c.name = m.category
                    WHERE m.id = ?""",
                    (rs, i) -> new ItemMenuAdmin(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getLong("categoriaId"),
                            rs.getBigDecimal("precio"),
                            rs.getString("fotoClave"),
                            rs.getString("disponibilidad"),
                            rs.getString("estado"), fotoUrl(rs.getString("fotoClave"))),
                    id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public ItemMenuAdmin crearItem(CrearItemMenuPeticion peticion) {
        String categoriaNombre = obtenerCategoria(peticion.categoriaId());
        validarNombreItemUnico(peticion.nombre().trim(), null);

        if (peticion.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.datosInvalidos("El precio debe ser mayor a cero.",
                    List.of(new DetalleError("precio", "El precio debe ser mayor a cero.")));
        }

        String foto = validarYNormalizarFoto(peticion.fotoClave());

        Long id = jdbc.queryForObject("""
                INSERT INTO menu_items(name, description, category, category_id, price, photo_key, availability, status)
                VALUES (?, ?, ?, ?, ?, ?, 'DISPONIBLE', 'ACTIVO')
                RETURNING id""", Long.class,
                peticion.nombre().trim(),
                peticion.descripcion().trim(),
                categoriaNombre,
                peticion.categoriaId(),
                peticion.precio(),
                foto);

        return obtenerItem(id);
    }

    @Transactional
    public ItemMenuAdmin editarItem(long id, EditarItemMenuPeticion peticion) {
        var actual = obtenerItem(id);

        String nuevoNombre = peticion.nombre() != null && !peticion.nombre().isBlank()
                ? peticion.nombre().trim()
                : actual.nombre();

        if (peticion.nombre() != null && !peticion.nombre().isBlank()
                && !nuevoNombre.equalsIgnoreCase(actual.nombre())) {
            validarNombreItemUnico(nuevoNombre, id);
        }

        String nuevaDescripcion = peticion.descripcion() != null && !peticion.descripcion().isBlank()
                ? peticion.descripcion().trim()
                : actual.descripcion();

        Long nuevaCategoriaId = peticion.categoriaId() != null ? peticion.categoriaId() : actual.categoriaId();
        String nuevaCategoriaNombre = peticion.categoriaId() != null
                ? obtenerCategoria(peticion.categoriaId())
                : jdbc.queryForObject("SELECT category FROM menu_items WHERE id = ?", String.class, id);

        BigDecimal nuevoPrecio = peticion.precio() != null ? peticion.precio() : actual.precio();
        if (nuevoPrecio.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.datosInvalidos("El precio debe ser mayor a cero.",
                    List.of(new DetalleError("precio", "El precio debe ser mayor a cero.")));
        }

        String nuevaFoto = actual.fotoClave();
        if (peticion.fotoClave() != null) {
            nuevaFoto = validarYNormalizarFoto(peticion.fotoClave());
        }

        jdbc.update("""
                UPDATE menu_items
                SET name = ?, description = ?, category = ?, category_id = ?, price = ?, photo_key = ?
                WHERE id = ?""",
                nuevoNombre, nuevaDescripcion, nuevaCategoriaNombre, nuevaCategoriaId, nuevoPrecio, nuevaFoto, id);

        return obtenerItem(id);
    }

    @Transactional
    public ItemMenuAdmin cambiarEstado(long id, EstadoCatalogoPeticion peticion) {
        obtenerItem(id);
        String estado = peticion.estado().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw ApiException.datosInvalidos("Estado inválido. Debe ser ACTIVO o INACTIVO.",
                    List.of(new DetalleError("estado", "Debe ser ACTIVO o INACTIVO.")));
        }

        jdbc.update("UPDATE menu_items SET status = ? WHERE id = ?", estado, id);
        return obtenerItem(id);
    }

    @Transactional
    public ItemMenuAdmin reactivar(long id) {
        var item = obtenerItem(id);
        if ("DISPONIBLE".equals(item.disponibilidad())) {
            throw ApiException.conflicto("ITEM_YA_DISPONIBLE", "El ítem ya estaba marcado como disponible.");
        }

        jdbc.update("""
                UPDATE menu_items
                SET availability = 'DISPONIBLE', exhausted_at = NULL, exhausted_by_employee_id = NULL
                WHERE id = ?""", id);

        return obtenerItem(id);
    }

    private String obtenerCategoria(long categoriaId) {
        try {
            return jdbc.queryForObject("SELECT name FROM menu_categories WHERE id = ?", String.class, categoriaId);
        } catch (EmptyResultDataAccessException e) {
            sincronizarCategoriasSiEsNecesario();
            try {
                return jdbc.queryForObject("SELECT name FROM menu_categories WHERE id = ?", String.class, categoriaId);
            } catch (EmptyResultDataAccessException ex) {
                throw ApiException.datosInvalidos("Categoría inexistente.",
                        List.of(new DetalleError("categoriaId", "La categoría especificada no existe.")));
            }
        }
    }

    @Transactional
    public void sincronizarCategoriasSiEsNecesario() {
        try {
            jdbc.execute("""
                    INSERT INTO menu_categories (name)
                    SELECT DISTINCT category FROM menu_items
                    WHERE category IS NOT NULL AND TRIM(category) <> ''
                    ON CONFLICT (name) DO NOTHING
                    """);
            jdbc.execute("""
                    UPDATE menu_items m
                    SET category_id = c.id
                    FROM menu_categories c
                    WHERE m.category = c.name AND m.category_id IS NULL
                    """);
        } catch (Exception ignored) {
            // Manejar si la tabla aún no está lista
        }
    }

    private void validarNombreCategoriaUnico(String nombre, Long idExcluido) {
        String sql = "SELECT count(*) FROM menu_categories WHERE lower(name) = lower(?)"
                + (idExcluido != null ? " AND id <> ?" : "");
        Object[] params = idExcluido != null ? new Object[]{nombre, idExcluido} : new Object[]{nombre};
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        if (count != null && count > 0) {
            throw ApiException.conflicto("CATEGORIA_EXISTENTE", "Ya existe una categoría de menú con ese nombre.");
        }
    }

    private void validarNombreItemUnico(String nombre, Long idExcluido) {
        String sql = "SELECT count(*) FROM menu_items WHERE lower(name) = lower(?)"
                + (idExcluido != null ? " AND id <> ?" : "");
        Object[] params = idExcluido != null ? new Object[]{nombre, idExcluido} : new Object[]{nombre};
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        if (count != null && count > 0) {
            throw ApiException.conflicto("ITEM_EXISTENTE", "Ya existe un ítem en el menú con ese nombre.");
        }
    }

    private String validarYNormalizarFoto(String fotoClave) {
        if (fotoClave == null || fotoClave.isBlank()) {
            return null;
        }
        String clave = fotoClave.trim();
        if (!clave.startsWith("menu/") || !(clave.endsWith(".jpg") || clave.endsWith(".png"))) {
            throw ApiException.datosInvalidos("La clave de foto debe pertenecer a menu/ y ser .jpg o .png.",
                    List.of(new DetalleError("fotoClave", "La clave de foto debe pertenecer a menu/ y ser .jpg o .png.")));
        }
        if (!almacen.existePublico(clave)) {
            throw ApiException.datosInvalidos("La foto no existe en el almacenamiento público: " + clave,
                    List.of(new DetalleError("fotoClave", "La foto no existe en el almacenamiento público.")));
        }
        return clave;
    }
}
