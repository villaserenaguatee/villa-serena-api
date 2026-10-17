package com.villaserena.api.reservas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.dto.HuespedAdicionalPeticion;
import com.villaserena.api.reservas.dto.HuespedAdicionalVista;

/**
 * Huéspedes adicionales (HU-REC-02): solo en reservas CONFIRMADA o EN_ESTADIA, y el
 * principal más los adicionales no supera los huéspedes de la reserva. No tienen
 * acceso a la app.
 */
@Service
public class HuespedAdicionalService {

    private final JdbcTemplate jdbc;

    public HuespedAdicionalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public HuespedAdicionalVista agregar(String codigo, HuespedAdicionalPeticion p) {
        // Bloquea la reserva para que dos registros a la vez no superen el máximo.
        DatosReserva reserva = jdbc.query(
                "SELECT id, estado, numero_huespedes FROM reservas WHERE codigo = ? FOR UPDATE",
                (rs, i) -> new DatosReserva(rs.getLong(1), EstadoReserva.valueOf(rs.getString(2)), rs.getInt(3)),
                codigo).stream().findFirst().orElseThrow(ApiException::noEncontrado);
        if (reserva.estado() != EstadoReserva.CONFIRMADA && reserva.estado() != EstadoReserva.EN_ESTADIA) {
            throw ApiException.conflicto("ESTADO_INVALIDO",
                    "Solo se agregan huéspedes a reservas confirmadas o en estadía.");
        }
        int adicionales = jdbc.queryForObject("SELECT count(*) FROM huespedes_adicionales WHERE reserva_id = ?",
                Integer.class, reserva.id());
        if (1 + adicionales >= reserva.numeroHuespedes()) {
            throw ApiException.conflicto("HUESPEDES_EXCEDIDOS", "La reserva es para " + reserva.numeroHuespedes()
                    + (reserva.numeroHuespedes() == 1 ? " huésped" : " huéspedes")
                    + " y ya están todos registrados.");
        }
        long id = jdbc.queryForObject("""
                INSERT INTO huespedes_adicionales
                    (reserva_id, nombre_completo, tipo_documento, numero_documento, nacionalidad)
                VALUES (?, ?, ?, ?, ?) RETURNING id""", Long.class,
                reserva.id(), p.nombreCompleto().trim(), p.tipoDocumento().name(), p.numeroDocumento().trim(),
                p.nacionalidad().trim());
        return new HuespedAdicionalVista(id, p.nombreCompleto().trim(), p.tipoDocumento(),
                p.numeroDocumento().trim(), p.nacionalidad().trim());
    }

    private record DatosReserva(long id, EstadoReserva estado, int numeroHuespedes) {
    }
}
