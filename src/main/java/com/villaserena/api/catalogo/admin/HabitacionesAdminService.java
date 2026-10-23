package com.villaserena.api.catalogo.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.catalogo.dto.CrearHabitacionPeticion;
import com.villaserena.api.catalogo.dto.EditarHabitacionPeticion;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import com.villaserena.api.catalogo.dto.HabitacionAdmin;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

@Service
public class HabitacionesAdminService {

    private final JdbcTemplate jdbc;

    public HabitacionesAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<HabitacionAdmin> listar(String estado, Long tipoHabitacionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, number AS "numero", floor AS "piso", room_type_id AS "tipoHabitacionId",
                       status AS "estado", occupancy AS "ocupacion", condition AS "condicion"
                FROM rooms WHERE 1=1""");
        List<Object> params = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND status = ?");
            params.add(estado.toUpperCase(Locale.ROOT));
        }
        if (tipoHabitacionId != null) {
            sql.append(" AND room_type_id = ?");
            params.add(tipoHabitacionId);
        }
        sql.append(" ORDER BY floor, number");
        return jdbc.query(sql.toString(),
                (rs, i) -> new HabitacionAdmin(
                        rs.getLong("id"),
                        rs.getString("numero"),
                        rs.getInt("piso"),
                        rs.getLong("tipoHabitacionId"),
                        rs.getString("estado"),
                        rs.getString("ocupacion"),
                        rs.getString("condicion")),
                params.toArray());
    }

    public HabitacionAdmin obtener(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id, number AS "numero", floor AS "piso", room_type_id AS "tipoHabitacionId",
                           status AS "estado", occupancy AS "ocupacion", condition AS "condicion"
                    FROM rooms WHERE id = ?""",
                    (rs, i) -> new HabitacionAdmin(
                            rs.getLong("id"),
                            rs.getString("numero"),
                            rs.getInt("piso"),
                            rs.getLong("tipoHabitacionId"),
                            rs.getString("estado"),
                            rs.getString("ocupacion"),
                            rs.getString("condicion")),
                    id);
        } catch (EmptyResultDataAccessException e) {
            throw ApiException.noEncontrado();
        }
    }

    @Transactional
    public HabitacionAdmin crear(CrearHabitacionPeticion peticion) {
        validarTipoActivo(peticion.tipoHabitacionId());
        validarNumeroUnico(peticion.numero().trim(), null);

        Long id = jdbc.queryForObject("""
                INSERT INTO rooms(number, floor, room_type_id, status, occupancy, condition)
                VALUES(?, ?, ?, 'ACTIVO', 'LIBRE', 'LIMPIA')
                RETURNING id""", Long.class,
                peticion.numero().trim(),
                peticion.piso(),
                peticion.tipoHabitacionId());
        return obtener(id);
    }

    @Transactional
    public HabitacionAdmin actualizar(long id, EditarHabitacionPeticion peticion) {
        var actual = obtener(id);
        String numero = peticion.numero() != null && !peticion.numero().isBlank() ? peticion.numero().trim() : actual.numero();
        int piso = peticion.piso() != null ? peticion.piso() : actual.piso();
        long tipoId = peticion.tipoHabitacionId() != null ? peticion.tipoHabitacionId() : actual.tipoHabitacionId();

        if (peticion.numero() != null && !peticion.numero().isBlank() && !peticion.numero().trim().equals(actual.numero())) {
            validarNumeroUnico(peticion.numero().trim(), id);
        }
        if (peticion.tipoHabitacionId() != null && peticion.tipoHabitacionId() != actual.tipoHabitacionId()) {
            validarTipoActivo(peticion.tipoHabitacionId());
            validarSinReservasActivas(id, "La habitación tiene reservas activas y no se puede cambiar de tipo.");
        }

        jdbc.update("""
                UPDATE rooms
                SET number = ?, floor = ?, room_type_id = ?, version = version + 1
                WHERE id = ?""", numero, piso, tipoId, id);
        return obtener(id);
    }

    @Transactional
    public HabitacionAdmin cambiarEstado(long id, EstadoCatalogoPeticion peticion) {
        obtener(id);
        if ("INACTIVO".equalsIgnoreCase(peticion.estado())) {
            validarSinReservasActivas(id, "La habitación tiene reservas activas y no se puede desactivar.");
        }
        jdbc.update("UPDATE rooms SET status = ?, version = version + 1 WHERE id = ?",
                peticion.estado().toUpperCase(Locale.ROOT), id);
        return obtener(id);
    }

    private void validarTipoActivo(long tipoId) {
        Integer activos = jdbc.queryForObject(
                "SELECT count(*) FROM room_types WHERE id = ? AND status = 'ACTIVO'", Integer.class, tipoId);
        if (activos == null || activos != 1) {
            throw ApiException.datosInvalidos("Selecciona un tipo de habitación activo.",
                    List.of(new DetalleError("tipoHabitacionId", "El tipo debe existir y estar activo.")));
        }
    }

    private void validarNumeroUnico(String numero, Long idExcluido) {
        String sql = "SELECT count(*) FROM rooms WHERE number = ?" + (idExcluido != null ? " AND id <> ?" : "");
        Object[] params = idExcluido != null ? new Object[]{numero, idExcluido} : new Object[]{numero};
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        if (count != null && count > 0) {
            throw ApiException.conflicto("HABITACION_EXISTENTE", "Ese número de habitación ya existe.");
        }
    }

    private void validarSinReservasActivas(long habitacionId, String mensaje) {
        Integer activas = jdbc.queryForObject("""
                SELECT count(*) FROM reservations
                WHERE room_id = ? AND status IN ('CONFIRMADA', 'EN_ESTADIA', 'PENDIENTE_PAGO')""",
                Integer.class, habitacionId);
        if (activas != null && activas > 0) {
            throw ApiException.conflicto("RESERVAS_ACTIVAS", mensaje);
        }
    }
}
