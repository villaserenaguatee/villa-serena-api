package com.villaserena.api.reservas;

import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de la cuenta de una reserva. */
@Service
public class CuentaService {

    private final JdbcTemplate jdbc;

    public CuentaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saldo = cargos VIGENTE − pagos APROBADO (RN-PAG-021). */
    @Transactional(readOnly = true)
    public BigDecimal saldo(long cuentaId) {
        BigDecimal saldo = jdbc.queryForObject("""
                SELECT coalesce((SELECT sum(total) FROM cargos WHERE cuenta_id = ? AND estado = 'VIGENTE'), 0)
                     - coalesce((SELECT sum(monto) FROM pagos WHERE cuenta_id = ? AND estado = 'APROBADO'), 0)""",
                BigDecimal.class, cuentaId, cuentaId);
        return saldo == null ? BigDecimal.ZERO.setScale(2) : saldo.setScale(2);
    }
}
