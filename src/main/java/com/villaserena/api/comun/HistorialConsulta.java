package com.villaserena.api.comun;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lee el historial de una entidad para mostrarlo (RG-EST-02). El responsable se
 * resuelve a un nombre legible: el del empleado si actuó una persona, o el actor
 * (Huésped, Cliente web, Stripe, SISTEMA) si no.
 */
@Service
public class HistorialConsulta {

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public HistorialConsulta(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    public List<HistorialOperacionVista> de(TipoEntidadHistorial entidad, long idEntidad) {
        return jdbc.query("""
                SELECT h.estado_anterior, h.estado_nuevo, h.tipo_responsable, e.nombre_completo, h.fecha, h.motivo
                FROM historial_estados h
                LEFT JOIN empleados e ON e.id = h.id_responsable
                WHERE h.tipo_entidad = ? AND h.id_entidad = ?
                ORDER BY h.fecha, h.id""",
                (rs, i) -> new HistorialOperacionVista(rs.getString(1), rs.getString(2),
                        responsable(rs.getString(3), rs.getString(4)),
                        OffsetDateTime.ofInstant(rs.getObject(5, Timestamp.class).toInstant(), reloj.getZone()),
                        rs.getString(6)),
                entidad.name(), idEntidad);
    }

    private static String responsable(String tipo, String nombreEmpleado) {
        if (nombreEmpleado != null) {
            return nombreEmpleado;
        }
        return switch (TipoResponsable.valueOf(tipo)) {
            case HUESPED -> "Huésped";
            case CLIENTE -> "Cliente web";
            case CANAL -> "Canal";
            case STRIPE -> "Stripe";
            case EMPLEADO, SISTEMA -> "SISTEMA";
        };
    }
}
