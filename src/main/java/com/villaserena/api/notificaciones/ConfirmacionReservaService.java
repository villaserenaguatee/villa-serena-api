package com.villaserena.api.notificaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.config.PropiedadesVillaSerena;

/**
 * Correo de confirmación de reserva (HU-HUE-07, RN-NOT-005). Solo arma los datos y
 * los encola; el envío es del Outbox, así que la reserva queda CONFIRMADA aunque
 * el correo no salga.
 * <p>
 * {@code ReservaService} llama a esta clase a través de
 * {@link ConfirmacionReservaNotifier} en los tres momentos en que una reserva
 * queda confirmada: el webhook de Stripe (web), Recepción y el canal.
 */
@Service
public class ConfirmacionReservaService implements ConfirmacionReservaNotifier {

    /** Horas fijas del hotel (PAR-01 y PAR-02; criterio 2 de HU-HUE-07). */
    private static final String HORA_CHECKIN = "15:00";
    private static final String HORA_CHECKOUT = "12:00";

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.of("es", "GT"));

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionReservaService.class);

    private final JdbcTemplate jdbc;
    private final OutboxService outbox;
    private final String appDescargaUrl;

    public ConfirmacionReservaService(JdbcTemplate jdbc, OutboxService outbox, PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.appDescargaUrl = propiedades.notificaciones().appDescargaUrl();
    }

    /**
     * Encola el correo. Exige la transacción de quien confirma la reserva
     * ({@code MANDATORY}): si esa transacción se deshace, el correo se va con ella.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificar(long reservaId) {
        List<Datos> encontrados = jdbc.query("""
                SELECT r.codigo, r.fecha_entrada, r.fecha_salida, r.numero_huespedes, r.total, r.canal,
                       t.nombre AS tipo, h.nombre_completo, h.correo
                FROM reservas r
                JOIN tipos_habitacion t ON t.id = r.tipo_habitacion_id
                JOIN huespedes h ON h.id = r.huesped_id
                WHERE r.id = ?""",
                (rs, i) -> new Datos(rs.getString("codigo"), rs.getObject("fecha_entrada", LocalDate.class),
                        rs.getObject("fecha_salida", LocalDate.class), rs.getInt("numero_huespedes"),
                        rs.getBigDecimal("total"), rs.getString("canal"), rs.getString("tipo"),
                        rs.getString("nombre_completo"), rs.getString("correo")),
                reservaId);
        if (encontrados.isEmpty()) {
            log.warn("No se encontró la reserva {} para su correo de confirmación", reservaId);
            return;
        }
        Datos datos = encontrados.getFirst();
        outbox.encolar(TipoNotificacion.CORREO_CONFIRMACION, datos.correo(), datos.paraPlantilla(appDescargaUrl));
    }

    private record Datos(String codigo, LocalDate entrada, LocalDate salida, int numeroHuespedes, BigDecimal total,
            String canal, String tipoHabitacion, String nombreHuesped, String correo) {

        /**
         * Lo que ve el huésped (criterios 2 a 4 de HU-HUE-07). Las fechas y el total
         * ya vienen escritos para que la plantilla no tenga que dar formato.
         */
        Map<String, Object> paraPlantilla(String appDescargaUrl) {
            long noches = salida.toEpochDay() - entrada.toEpochDay();
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("codigo", codigo);
            datos.put("nombreHuesped", nombreHuesped);
            datos.put("correo", correo);
            datos.put("entrada", FECHA_LARGA.format(entrada));
            datos.put("salida", FECHA_LARGA.format(salida));
            datos.put("noches", noches);
            datos.put("tipoHabitacion", tipoHabitacion);
            datos.put("numeroHuespedes", numeroHuespedes);
            datos.put("total", "Q " + total.toPlainString());
            datos.put("horaCheckin", HORA_CHECKIN);
            datos.put("horaCheckout", HORA_CHECKOUT);
            // Web y canal cobran por adelantado; Recepción cobra al salir (criterio 3).
            datos.put("pagado", !"RECEPCION".equals(canal));
            datos.put("appDescargaUrl", appDescargaUrl);
            return datos;
        }
    }
}
