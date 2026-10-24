package com.villaserena.api.catalogo.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.archivos.AlmacenArchivos;
import com.villaserena.api.catalogo.dto.FotoCatalogo;
import com.villaserena.api.catalogo.dto.FotoTipoPeticion;
import com.villaserena.api.catalogo.dto.FotosTipoPeticion;
import com.villaserena.api.catalogo.dto.TipoHabitacionAdmin;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;

@Service
public class TiposHabitacionAdminService {

    private final JdbcTemplate jdbc;
    private final AlmacenArchivos almacen;
    private final PropiedadesVillaSerena propiedades;

    public TiposHabitacionAdminService(JdbcTemplate jdbc, AlmacenArchivos almacen,
            PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.almacen = almacen;
        this.propiedades = propiedades;
    }

    @Transactional
    public TipoHabitacionAdmin actualizarFotos(long id, FotosTipoPeticion peticion) {
        Integer existe = jdbc.queryForObject("SELECT count(*) FROM room_types WHERE id = ?", Integer.class, id);
        if (existe == null || existe == 0) {
            throw ApiException.noEncontrado();
        }

        if (peticion.fotos() == null || peticion.fotos().isEmpty()) {
            throw ApiException.datosInvalidos("FOTOS_REQUERIDAS",
                    List.of(new DetalleError("fotos", "Debes incluir al menos una foto.")));
        }

        long principales = peticion.fotos().stream().filter(FotoTipoPeticion::principal).count();
        if (principales != 1) {
            throw ApiException.datosInvalidos("Debe haber exactamente una foto principal.",
                    List.of(new DetalleError("fotos", "Debe haber exactamente una foto principal.")));
        }

        for (var f : peticion.fotos()) {
            if (f.clave() == null || !f.clave().startsWith("tipos/")
                    || !(f.clave().endsWith(".jpg") || f.clave().endsWith(".png"))) {
                throw ApiException.datosInvalidos("La clave de la foto debe pertenecer a tipos/ y ser .jpg o .png.",
                        List.of(new DetalleError("fotos", "La clave de la foto debe pertenecer a tipos/ y ser .jpg o .png.")));
            }
            if (!almacen.existePublico(f.clave())) {
                throw ApiException.datosInvalidos("La foto no existe en el almacenamiento público: " + f.clave(),
                        List.of(new DetalleError("fotos", "La foto no existe en el almacenamiento público: " + f.clave())));
            }
        }

        jdbc.update("DELETE FROM room_type_photos WHERE room_type_id = ?", id);
        for (var f : peticion.fotos()) {
            jdbc.update("""
                    INSERT INTO room_type_photos(room_type_id, object_key, is_primary, sort_order)
                    VALUES(?, ?, ?, ?)""", id, f.clave(), f.principal(), f.orden());
        }
        jdbc.update("UPDATE room_types SET version = version + 1 WHERE id = ?", id);

        return obtener(id);
    }

    public TipoHabitacionAdmin obtener(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id, name AS "nombre", description AS "descripcion", capacity AS "capacidad",
                           base_price AS "precioBase", weekend_adjustment_pct AS "ajusteFinSemanaPct",
                           status AS "estado"
                    FROM room_types WHERE id = ?""",
                    (rs, i) -> new TipoHabitacionAdmin(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("descripcion"),
                            rs.getInt("capacidad"),
                            rs.getBigDecimal("precioBase"),
                            rs.getBigDecimal("ajusteFinSemanaPct"),
                            rs.getString("estado"),
                            fotosDeTipo(rs.getLong("id"))),
                    id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public TipoHabitacionAdmin crear(com.villaserena.api.catalogo.dto.CrearTipoHabitacionPeticion peticion) {
        Integer existe = jdbc.queryForObject(
                "SELECT count(*) FROM room_types WHERE lower(name) = lower(?)",
                Integer.class, peticion.nombre().trim());
        if (existe != null && existe > 0) {
            throw ApiException.conflicto("CATEGORIA_EXISTENTE", "Ya existe una categoría de habitación con ese nombre.");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO room_types(name, description, capacity, base_price, weekend_adjustment_pct, status)
                VALUES(?, ?, ?, ?, 0.00, 'ACTIVO')
                RETURNING id""", Long.class,
                peticion.nombre().trim(),
                peticion.descripcion().trim(),
                peticion.capacidad(),
                peticion.precioBase());
        return obtener(id);
    }

    @Transactional
    public TipoHabitacionAdmin actualizar(long id, com.villaserena.api.catalogo.dto.EditarTipoHabitacionPeticion peticion) {
        var actual = obtener(id);
        if (peticion.nombre() != null && !peticion.nombre().isBlank() && !peticion.nombre().trim().equalsIgnoreCase(actual.nombre())) {
            Integer existe = jdbc.queryForObject(
                    "SELECT count(*) FROM room_types WHERE lower(name) = lower(?) AND id <> ?",
                    Integer.class, peticion.nombre().trim(), id);
            if (existe != null && existe > 0) {
                throw ApiException.conflicto("CATEGORIA_EXISTENTE", "Ya existe una categoría de habitación con ese nombre.");
            }
        }
        int nuevaCapacidad = peticion.capacidad() != null ? peticion.capacidad() : actual.capacidad();
        if (peticion.capacidad() != null && peticion.capacidad() < actual.capacidad()) {
            Integer activas = jdbc.queryForObject("""
                    SELECT count(*) FROM reservations
                    WHERE room_type_id = ? AND status IN ('CONFIRMADA', 'EN_ESTADIA', 'PENDIENTE_PAGO')
                      AND guest_count > ?""", Integer.class, id, nuevaCapacidad);
            if (activas != null && activas > 0) {
                throw ApiException.conflicto("RESERVAS_ACTIVAS", "El cambio de capacidad afecta reservas activas.");
            }
        }
        String nombre = peticion.nombre() != null && !peticion.nombre().isBlank() ? peticion.nombre().trim() : actual.nombre();
        String descripcion = peticion.descripcion() != null && !peticion.descripcion().isBlank() ? peticion.descripcion().trim() : actual.descripcion();
        BigDecimal precio = peticion.precioBase() != null ? peticion.precioBase() : actual.precioBase();

        jdbc.update("""
                UPDATE room_types
                SET name = ?, description = ?, capacity = ?, base_price = ?, version = version + 1
                WHERE id = ?""", nombre, descripcion, nuevaCapacidad, precio, id);
        return obtener(id);
    }

    @Transactional
    public TipoHabitacionAdmin cambiarEstado(long id, com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion peticion) {
        var actual = obtener(id);
        if ("INACTIVO".equalsIgnoreCase(peticion.estado())) {
            Integer activas = jdbc.queryForObject("""
                    SELECT count(*) FROM reservations
                    WHERE room_type_id = ? AND status IN ('CONFIRMADA', 'EN_ESTADIA', 'PENDIENTE_PAGO')""",
                    Integer.class, id);
            if (activas != null && activas > 0) {
                throw ApiException.conflicto("RESERVAS_ACTIVAS", "El tipo de habitación tiene reservas activas.");
            }
        }
        jdbc.update("UPDATE room_types SET status = ?, version = version + 1 WHERE id = ?",
                peticion.estado().toUpperCase(java.util.Locale.ROOT), id);
        return obtener(id);
    }

    @Transactional
    public TipoHabitacionAdmin ajustarFinSemana(long id, com.villaserena.api.catalogo.dto.AjusteFinSemanaPeticion peticion) {
        obtener(id);
        if (peticion.ajusteFinSemanaPct().compareTo(new BigDecimal("-100")) <= 0) {
            throw ApiException.datosInvalidos("El ajuste de fin de semana debe ser mayor a -100%.",
                    List.of(new DetalleError("ajusteFinSemanaPct", "El ajuste debe ser mayor a -100%.")));
        }
        jdbc.update("UPDATE room_types SET weekend_adjustment_pct = ?, version = version + 1 WHERE id = ?",
                peticion.ajusteFinSemanaPct(), id);
        return obtener(id);
    }

    public List<TipoHabitacionAdmin> listar() {
        return listar(null);
    }

    public List<TipoHabitacionAdmin> listar(String estado) {
        Map<Long, List<FotoCatalogo>> todasLasFotos = jdbc.query("""
                SELECT room_type_id, object_key, is_primary, sort_order
                FROM room_type_photos
                ORDER BY room_type_id, is_primary DESC, sort_order, id""",
                (rs, i) -> Map.entry(rs.getLong("room_type_id"),
                        new FotoCatalogo(rs.getString("object_key"),
                                urlPublica(rs.getString("object_key")),
                                rs.getBoolean("is_primary"),
                                rs.getInt("sort_order"))))
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        String sql = """
                SELECT id, name AS "nombre", description AS "descripcion", capacity AS "capacidad",
                       base_price AS "precioBase", weekend_adjustment_pct AS "ajusteFinSemanaPct",
                       status AS "estado"
                FROM room_types""" + (estado != null && !estado.isBlank() ? " WHERE status = ?" : "") + " ORDER BY id";

        Object[] params = estado != null && !estado.isBlank() ? new Object[]{estado.toUpperCase(java.util.Locale.ROOT)} : new Object[]{};

        return jdbc.query(sql,
                (rs, i) -> new TipoHabitacionAdmin(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getInt("capacidad"),
                        rs.getBigDecimal("precioBase"),
                        rs.getBigDecimal("ajusteFinSemanaPct"),
                        rs.getString("estado"),
                        todasLasFotos.getOrDefault(rs.getLong("id"), List.of())),
                params);
    }

    private List<FotoCatalogo> fotosDeTipo(long tipoId) {
        return jdbc.query("""
                SELECT object_key, is_primary, sort_order
                FROM room_type_photos
                WHERE room_type_id = ?
                ORDER BY is_primary DESC, sort_order, id""",
                (rs, i) -> new FotoCatalogo(
                        rs.getString("object_key"),
                        urlPublica(rs.getString("object_key")),
                        rs.getBoolean("is_primary"),
                        rs.getInt("sort_order")),
                tipoId);
    }

    private String urlPublica(String clave) {
        String base = propiedades.archivos().urlPublica();
        if (base == null || base.isBlank()) {
            return clave.startsWith("/") ? clave : "/" + clave;
        }
        return base.endsWith("/") ? base + clave : base + "/" + clave;
    }
}
