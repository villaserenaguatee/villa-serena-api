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

    public List<TipoHabitacionAdmin> listar() {
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

        return jdbc.query("""
                SELECT id, name AS "nombre", description AS "descripcion", capacity AS "capacidad",
                       base_price AS "precioBase", weekend_adjustment_pct AS "ajusteFinSemanaPct",
                       status AS "estado"
                FROM room_types ORDER BY id""",
                (rs, i) -> new TipoHabitacionAdmin(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("descripcion"),
                        rs.getInt("capacidad"),
                        rs.getBigDecimal("precioBase"),
                        rs.getBigDecimal("ajusteFinSemanaPct"),
                        rs.getString("estado"),
                        todasLasFotos.getOrDefault(rs.getLong("id"), List.of())));
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
