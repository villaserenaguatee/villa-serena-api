package com.villaserena.api.comun;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra cada cambio de estado en {@code historial_estados} (RG-EST-02).
 * Se llama dentro de la misma transacción del cambio.
 */
@Service
public class HistorialEstadoService {

    private final JdbcTemplate jdbc;

    public HistorialEstadoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(TipoEntidadHistorial entidad, long idEntidad, Enum<?> anterior, Enum<?> nuevo,
            Responsable responsable, String motivo) {
        registrar(entidad, idEntidad, anterior == null ? null : anterior.name(), nuevo.name(), responsable, motivo);
    }

    /** Variante con los estados como texto (por ejemplo, la ocupación de una habitación: LIBRE, OCUPADA). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(TipoEntidadHistorial entidad, long idEntidad, String anterior, String nuevo,
            Responsable responsable, String motivo) {
        jdbc.update("""
                INSERT INTO status_history
                    (entity_type, entity_id, previous_status, new_status, responsible_type, responsible_id, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                entidad.name(), idEntidad, anterior, nuevo,
                responsable.tipo().name(), responsable.empleadoId(), motivo);
    }
}
