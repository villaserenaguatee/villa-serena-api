package com.villaserena.api.hotel;

import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;

/** Servicio de gestión de amenidades y red Wi-Fi para huéspedes (HU-ADM-11, HU-HUE-18). */
@Service
public class AmenidadesWifiService {

    private final JdbcTemplate jdbc;
    private final PropiedadesVillaSerena propiedades;

    public AmenidadesWifiService(JdbcTemplate jdbc, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.propiedades = propiedades;
    }

    @Transactional(readOnly = true)
    public List<AmenidadAdmin> listarAdmin() {
        return jdbc.query("""
                SELECT id, name, description, schedule, location, sort_order, photo_key, status
                FROM hotel_amenities
                ORDER BY sort_order ASC, id ASC
                """, (rs, i) -> new AmenidadAdmin(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("photo_key"),
                rs.getString("schedule"),
                rs.getString("location"),
                rs.getInt("sort_order"),
                rs.getString("status")
        ));
    }

    @Transactional(readOnly = true)
    public AmenidadAdmin obtenerAdmin(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id, name, description, schedule, location, sort_order, photo_key, status
                    FROM hotel_amenities
                    WHERE id = ?
                    """, (rs, i) -> new AmenidadAdmin(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("photo_key"),
                    rs.getString("schedule"),
                    rs.getString("location"),
                    rs.getInt("sort_order"),
                    rs.getString("status")
            ), id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public AmenidadAdmin crearAdmin(AmenidadPeticion peticion) {
        if (peticion.nombre() == null || peticion.nombre().isBlank()) {
            throw ApiException.datosInvalidos("El nombre es obligatorio.",
                    List.of(new DetalleError("nombre", "El nombre es obligatorio.")));
        }
        if (peticion.orden() != null && peticion.orden() < 0) {
            throw ApiException.datosInvalidos("El orden no puede ser negativo.",
                    List.of(new DetalleError("orden", "El orden no puede ser negativo.")));
        }

        Long id = jdbc.queryForObject("""
                INSERT INTO hotel_amenities (name, description, schedule, location, sort_order, photo_key, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO')
                RETURNING id
                """, Long.class,
                peticion.nombre().trim(),
                peticion.descripcion() != null ? peticion.descripcion().trim() : "",
                peticion.horario() != null ? peticion.horario().trim() : "",
                peticion.ubicacion() != null ? peticion.ubicacion().trim() : "",
                peticion.orden() != null ? peticion.orden() : 0,
                peticion.fotoClave() != null && !peticion.fotoClave().isBlank() ? peticion.fotoClave().trim() : null
        );

        return obtenerAdmin(id);
    }

    @Transactional
    public AmenidadAdmin editarAdmin(long id, EditarAmenidadPeticion peticion) {
        AmenidadAdmin actual = obtenerAdmin(id);

        String nuevoNombre = actual.nombre();
        if (peticion.nombre() != null) {
            if (peticion.nombre().isBlank()) {
                throw ApiException.datosInvalidos("El nombre no puede estar vacío.",
                        List.of(new DetalleError("nombre", "El nombre no puede estar vacío.")));
            }
            nuevoNombre = peticion.nombre().trim();
        }

        String nuevaDescripcion = peticion.descripcion() != null ? peticion.descripcion().trim() : actual.descripcion();
        String nuevoHorario = peticion.horario() != null ? peticion.horario().trim() : actual.horario();
        String nuevaUbicacion = peticion.ubicacion() != null ? peticion.ubicacion().trim() : actual.ubicacion();

        int nuevoOrden = actual.orden();
        if (peticion.orden() != null) {
            if (peticion.orden() < 0) {
                throw ApiException.datosInvalidos("El orden no puede ser negativo.",
                        List.of(new DetalleError("orden", "El orden no puede ser negativo.")));
            }
            nuevoOrden = peticion.orden();
        }

        // Si se envía fotoClave (incluso null explícito), actualiza fotoClave
        String nuevaFotoClave = peticion.fotoClave() != null && !peticion.fotoClave().isBlank()
                ? peticion.fotoClave().trim()
                : (peticion.fotoClave() == null ? null : actual.fotoClave());

        jdbc.update("""
                UPDATE hotel_amenities
                SET name = ?, description = ?, schedule = ?, location = ?, sort_order = ?, photo_key = ?
                WHERE id = ?
                """,
                nuevoNombre,
                nuevaDescripcion,
                nuevoHorario,
                nuevaUbicacion,
                nuevoOrden,
                nuevaFotoClave,
                id);

        return obtenerAdmin(id);
    }

    @Transactional
    public AmenidadAdmin cambiarEstadoAdmin(long id, String nuevoEstado) {
        obtenerAdmin(id); // Valida existencia
        if (!"ACTIVO".equals(nuevoEstado) && !"INACTIVO".equals(nuevoEstado)) {
            throw ApiException.datosInvalidos("El estado debe ser ACTIVO o INACTIVO.",
                    List.of(new DetalleError("estado", "El estado debe ser ACTIVO o INACTIVO.")));
        }

        jdbc.update("UPDATE hotel_amenities SET status = ? WHERE id = ?", nuevoEstado, id);
        return obtenerAdmin(id);
    }

    @Transactional(readOnly = true)
    public List<AmenidadApp> listarHuesped() {
        return jdbc.query("""
                SELECT id, name, description, schedule, location, sort_order, photo_key
                FROM hotel_amenities
                WHERE status = 'ACTIVO'
                ORDER BY sort_order ASC, id ASC
                """, (rs, i) -> new AmenidadApp(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("schedule"),
                rs.getString("location"),
                rs.getInt("sort_order"),
                resolverFotoUrl(rs.getString("photo_key"))
        ));
    }

    @Transactional
    public Wifi consultarWifi() {
        try {
            return jdbc.queryForObject("""
                    SELECT network_name, password FROM hotel_wifi WHERE id = 1
                    """, (rs, i) -> new Wifi(rs.getString("network_name"), rs.getString("password")));
        } catch (EmptyResultDataAccessException e) {
            jdbc.update("""
                    INSERT INTO hotel_wifi (id, network_name, password)
                    VALUES (1, 'VillaSerena-Huespedes', 'EjemploWifi2026')
                    ON CONFLICT (id) DO NOTHING
                    """);
            return new Wifi("VillaSerena-Huespedes", "EjemploWifi2026");
        }
    }

    @Transactional
    public Wifi actualizarWifi(Wifi peticion) {
        if (peticion.nombreRed() == null || peticion.nombreRed().isBlank()) {
            throw ApiException.datosInvalidos("El nombre de la red es obligatorio.",
                    List.of(new DetalleError("nombreRed", "El nombre de la red es obligatorio.")));
        }
        if (peticion.contrasena() == null || peticion.contrasena().isBlank()) {
            throw ApiException.datosInvalidos("La contraseña es obligatoria.",
                    List.of(new DetalleError("contrasena", "La contraseña es obligatoria.")));
        }

        jdbc.update("""
                INSERT INTO hotel_wifi (id, network_name, password)
                VALUES (1, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET network_name = EXCLUDED.network_name, password = EXCLUDED.password
                """, peticion.nombreRed().trim(), peticion.contrasena().trim());

        return new Wifi(peticion.nombreRed().trim(), peticion.contrasena().trim());
    }

    private String resolverFotoUrl(String photoKey) {
        if (photoKey == null || photoKey.isBlank()) {
            return null;
        }
        String base = propiedades.archivos().urlPublica();
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.replaceAll("/+$", "") + "/" + photoKey.replaceAll("^/+", "");
    }
}
