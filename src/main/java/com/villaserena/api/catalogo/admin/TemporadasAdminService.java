package com.villaserena.api.catalogo.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.catalogo.dto.TemporadaAdmin;
import com.villaserena.api.catalogo.dto.TemporadaPeticion;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

@Service
public class TemporadasAdminService {

    private static final long LOCK_ID = 1648021L;

    private final JdbcTemplate jdbc;

    public TemporadasAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TemporadaAdmin> listar() {
        return jdbc.query("""
                SELECT id, name AS "nombre", start_date AS "inicio", end_date AS "fin",
                       adjustment_pct AS "ajustePct", applies_to_all AS "aplicaATodos"
                FROM seasons
                WHERE active = true
                ORDER BY start_date, id
                """, (rs, i) -> {
            long id = rs.getLong("id");
            String nombre = rs.getString("nombre");
            LocalDate inicio = rs.getObject("inicio", LocalDate.class);
            LocalDate fin = rs.getObject("fin", LocalDate.class);
            BigDecimal ajustePct = rs.getBigDecimal("ajustePct");
            boolean aplicaATodos = rs.getBoolean("aplicaATodos");

            List<Long> tipos = aplicaATodos
                    ? Collections.emptyList()
                    : jdbc.queryForList(
                            "SELECT room_type_id FROM season_room_types WHERE season_id = ? ORDER BY room_type_id",
                            Long.class, id);

            return new TemporadaAdmin(id, nombre, inicio, fin, ajustePct, tipos);
        });
    }

    @Transactional(readOnly = true)
    public TemporadaAdmin obtener(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id, name AS "nombre", start_date AS "inicio", end_date AS "fin",
                           adjustment_pct AS "ajustePct", applies_to_all AS "aplicaATodos"
                    FROM seasons
                    WHERE id = ? AND active = true
                    """, (rs, i) -> {
                String nombre = rs.getString("nombre");
                LocalDate inicio = rs.getObject("inicio", LocalDate.class);
                LocalDate fin = rs.getObject("fin", LocalDate.class);
                BigDecimal ajustePct = rs.getBigDecimal("ajustePct");
                boolean aplicaATodos = rs.getBoolean("aplicaATodos");

                List<Long> tipos = aplicaATodos
                        ? Collections.emptyList()
                        : jdbc.queryForList(
                                "SELECT room_type_id FROM season_room_types WHERE season_id = ? ORDER BY room_type_id",
                                Long.class, id);

                return new TemporadaAdmin(id, nombre, inicio, fin, ajustePct, tipos);
            }, id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public TemporadaAdmin crear(TemporadaPeticion peticion) {
        validarPeticion(peticion);
        List<Long> tipos = normalizarTipos(peticion.tipoHabitacionIds());
        boolean aplicaATodos = tipos.isEmpty();

        validarTraslapes(peticion.inicio(), peticion.fin(), tipos, aplicaATodos, null);

        Long id = jdbc.queryForObject("""
                INSERT INTO seasons(name, start_date, end_date, adjustment_pct, applies_to_all, active)
                VALUES (?, ?, ?, ?, ?, true)
                RETURNING id
                """, Long.class,
                peticion.nombre().trim(),
                peticion.inicio(),
                peticion.fin(),
                peticion.ajustePct(),
                aplicaATodos);

        if (!aplicaATodos && id != null) {
            for (Long tipoId : tipos) {
                jdbc.update("INSERT INTO season_room_types(season_id, room_type_id) VALUES (?, ?)", id, tipoId);
            }
        }

        return obtener(id);
    }

    @Transactional
    public TemporadaAdmin editar(long id, TemporadaPeticion peticion) {
        obtener(id); // Verifica existencia o 404
        validarPeticion(peticion);
        List<Long> tipos = normalizarTipos(peticion.tipoHabitacionIds());
        boolean aplicaATodos = tipos.isEmpty();

        validarTraslapes(peticion.inicio(), peticion.fin(), tipos, aplicaATodos, id);

        jdbc.update("""
                UPDATE seasons
                SET name = ?, start_date = ?, end_date = ?, adjustment_pct = ?, applies_to_all = ?, version = version + 1
                WHERE id = ?
                """,
                peticion.nombre().trim(),
                peticion.inicio(),
                peticion.fin(),
                peticion.ajustePct(),
                aplicaATodos,
                id);

        jdbc.update("DELETE FROM season_room_types WHERE season_id = ?", id);
        if (!aplicaATodos) {
            for (Long tipoId : tipos) {
                jdbc.update("INSERT INTO season_room_types(season_id, room_type_id) VALUES (?, ?)", id, tipoId);
            }
        }

        return obtener(id);
    }

    @Transactional
    public void eliminar(long id) {
        obtener(id); // Verifica existencia o 404
        jdbc.update("DELETE FROM seasons WHERE id = ?", id);
    }

    private void validarPeticion(TemporadaPeticion p) {
        if (p.fin().isBefore(p.inicio())) {
            throw ApiException.datosInvalidos("La fecha final debe ser igual o posterior a la inicial.",
                    List.of(new DetalleError("fin", "La fecha final debe ser igual o posterior a la inicial.")));
        }
        if (p.ajustePct().compareTo(BigDecimal.valueOf(-100)) <= 0) {
            throw ApiException.datosInvalidos("El ajuste debe ser mayor a -100%.",
                    List.of(new DetalleError("ajustePct", "El ajuste debe ser mayor a -100%.")));
        }
    }

    private List<Long> normalizarTipos(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tipos = ids.stream().distinct().toList();
        for (Long tipoId : tipos) {
            Integer existe = jdbc.queryForObject(
                    "SELECT count(*) FROM room_types WHERE id = ?", Integer.class, tipoId);
            if (existe == null || existe == 0) {
                throw ApiException.datosInvalidos("Categoría de habitación inexistente: " + tipoId,
                        List.of(new DetalleError("tipoHabitacionIds", "Categoría no encontrada: " + tipoId)));
            }
        }
        return tipos;
    }

    private void validarTraslapes(LocalDate inicio, LocalDate fin, List<Long> tipos, boolean aplicaATodos, Long idExcluido) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, LOCK_ID);

        String sql = """
                SELECT id, applies_to_all AS "aplica_a_todos"
                FROM seasons
                WHERE active = true AND start_date <= ? AND end_date >= ?
                """ + (idExcluido != null ? " AND id <> ?" : "");

        Object[] params = idExcluido != null
                ? new Object[]{fin, inicio, idExcluido}
                : new Object[]{fin, inicio};

        var solapadas = jdbc.queryForList(sql, params);

        for (var fila : solapadas) {
            Long otraId = ((Number) fila.get("id")).longValue();
            boolean otraAplicaATodos = Boolean.TRUE.equals(fila.get("aplica_a_todos"));

            if (aplicaATodos || otraAplicaATodos) {
                throw ApiException.conflicto("TEMPORADA_SOLAPADA",
                        "Ya existe una temporada activa que se solapa en esas fechas.");
            }

            List<Long> tiposOtra = jdbc.queryForList(
                    "SELECT room_type_id FROM season_room_types WHERE season_id = ?", Long.class, otraId);

            boolean interseccion = !Collections.disjoint(tipos, tiposOtra);
            if (interseccion) {
                throw ApiException.conflicto("TEMPORADA_SOLAPADA",
                        "Hay temporadas superpuestas para una misma categoría de habitación.");
            }
        }
    }
}
