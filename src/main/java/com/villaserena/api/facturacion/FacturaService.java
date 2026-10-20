package com.villaserena.api.facturacion;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.TipoNotificacion;

/**
 * Emisión de la factura de demostración (OBJ-4B; HU-REC-15). El check-out la llama
 * dentro de su transacción: si algo falla, el check-out completo se deshace
 * (RN-RES-022). La consulta y el PDF están en {@link FacturaConsultaService}.
 */
@Service
public class FacturaService implements EmisorFactura {

    private final JdbcTemplate jdbc;
    private final OutboxService outbox;
    private final FacturaConsultaService consultas;
    private final Clock reloj;

    public FacturaService(JdbcTemplate jdbc, OutboxService outbox, FacturaConsultaService consultas, Clock reloj) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.consultas = consultas;
        this.reloj = reloj;
    }

    /**
     * Una factura por cuenta, con el siguiente número de la serie fija: la fila de la
     * serie se bloquea mientras se toma el número, así dos check-outs a la vez no
     * repiten ni saltan números (RN-FAC-004). Copia los datos fiscales del hotel para
     * que la reimpresión no cambie si después se edita la configuración. Encola el
     * correo con el PDF en la misma transacción: si el check-out se deshace, el correo
     * también; si el envío falla, el check-out sigue válido y el Outbox reintenta.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FacturaEmitida emitir(long cuentaId, String nit, String nombreComprador) {
        if (jdbc.queryForObject("SELECT count(*)  FROM invoices WHERE account_id = ?", Integer.class, cuentaId) > 0) {
            throw ApiException.conflicto("FACTURA_EXISTENTE", "Esta cuenta ya tiene su factura.");
        }
        Serie serie = jdbc.query("SELECT id, series AS \"serie\", last_number AS \"ultimo_numero\" FROM invoice_series ORDER BY id LIMIT 1 FOR UPDATE",
                (rs, i) -> new Serie(rs.getLong(1), rs.getString(2), rs.getInt(3))).stream().findFirst()
                .orElseThrow(() -> ApiException.conflicto("SERIE_NO_CONFIGURADA",
                        "No hay una serie de facturación configurada."));
        if (jdbc.queryForObject("SELECT count(*)  FROM hotel_configuration", Integer.class) == 0) {
            throw ApiException.conflicto("HOTEL_NO_CONFIGURADO", "Faltan los datos fiscales del hotel.");
        }
        BigDecimal total = jdbc.queryForObject(
                "SELECT coalesce(sum(total), 0)  FROM charges WHERE account_id = ? AND status = 'VIGENTE'",
                BigDecimal.class, cuentaId);
        int numero = serie.ultimoNumero() + 1;
        jdbc.update("UPDATE invoice_series SET last_number = ? WHERE id = ?", numero, serie.id());
        long id = jdbc.queryForObject("""
                INSERT INTO invoices (account_id, series_id, number, issued_at, buyer_tax_id, buyer_name, total,
                                      trade_name, legal_name, hotel_tax_id, tax_address, address)
                SELECT ?, ?, ?, ?, ?, ?, ?, trade_name, legal_name, tax_id, tax_address, address
                FROM hotel_configuration WHERE id = 1
                RETURNING id""", Long.class,
                cuentaId, serie.id(), numero, Timestamp.from(reloj.instant()), nit, nombreComprador, total);

        FacturaDetalle detalle = consultas.detalle(id);
        encolarCorreo(detalle, cuentaId);
        return new FacturaEmitida(id, detalle);
    }

    private void encolarCorreo(FacturaDetalle f, long cuentaId) {
        jdbc.query("""
                SELECT h.email AS "correo", h.full_name AS "nombre_completo" FROM accounts cu
                JOIN reservations r ON r.id = cu.reservation_id JOIN guests h ON h.id = r.guest_id
                WHERE cu.id = ?""", rs -> {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("facturaId", f.id());
            datos.put("serieNumero", f.serie() + " " + f.numero());
            datos.put("codigoReserva", f.codigoReserva());
            datos.put("nombreHuesped", rs.getString(2));
            datos.put("nombreComprador", f.comprador().nombreComprador());
            datos.put("nit", f.comprador().nit());
            datos.put("total", "Q " + f.total().toPlainString());
            outbox.encolar(TipoNotificacion.CORREO_FACTURA, rs.getString(1), datos);
        }, cuentaId);
    }

    private record Serie(long id, String serie, int ultimoNumero) {
    }
}
