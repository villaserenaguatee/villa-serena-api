package com.villaserena.api.catalogo.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.catalogo.dto.Indicadores;
import com.villaserena.api.catalogo.dto.OcupacionHoy;
import com.villaserena.api.catalogo.dto.ReservasPorCanal;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

@Service
public class IndicadoresAdminService {

    public static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public IndicadoresAdminService(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Indicadores consultar(LocalDate inicio, LocalDate fin) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_GUATEMALA));
        LocalDate fechaInicio = inicio;
        LocalDate fechaFin = fin;

        if (fechaInicio == null && fechaFin == null) {
            fechaInicio = hoy.withDayOfMonth(1);
            fechaFin = hoy.withDayOfMonth(hoy.lengthOfMonth());
        } else if (fechaInicio == null) {
            fechaInicio = fechaFin.withDayOfMonth(1);
        } else if (fechaFin == null) {
            fechaFin = fechaInicio.withDayOfMonth(fechaInicio.lengthOfMonth());
        }

        if (fechaFin.isBefore(fechaInicio)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERIODO_INVALIDO",
                    "La fecha de inicio no puede ser posterior a la fecha de fin.",
                    List.of(new DetalleError("inicio", "La fecha de inicio no puede ser posterior a la fecha de fin.")));
        }

        OffsetDateTime desde = fechaInicio.atStartOfDay(ZONA_GUATEMALA).toOffsetDateTime();
        OffsetDateTime hasta = fechaFin.plusDays(1).atStartOfDay(ZONA_GUATEMALA).toOffsetDateTime();

        // 1. Ocupación de hoy
        Integer activas = jdbc.queryForObject(
                "SELECT count(*) FROM rooms WHERE status = 'ACTIVO'", Integer.class);
        Integer ocupadas = jdbc.queryForObject(
                "SELECT count(*) FROM rooms WHERE status = 'ACTIVO' AND occupancy = 'OCUPADA'", Integer.class);

        int totalActivas = activas != null ? activas : 0;
        int totalOcupadas = ocupadas != null ? ocupadas : 0;
        double porcentaje = 0.0;
        if (totalActivas > 0) {
            porcentaje = BigDecimal.valueOf(totalOcupadas * 100.0 / totalActivas)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        OcupacionHoy ocupacionHoy = new OcupacionHoy(hoy, totalOcupadas, totalActivas, porcentaje);

        // 2. Ingresos de pagos APROBADO en el rango
        BigDecimal ingresos = jdbc.queryForObject("""
                SELECT coalesce(sum(amount), 0.00)
                FROM payments
                WHERE status = 'APROBADO' AND created_at >= ? AND created_at < ?
                """, BigDecimal.class, desde, hasta);

        if (ingresos == null) {
            ingresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            ingresos = ingresos.setScale(2, RoundingMode.HALF_UP);
        }

        // 3. Reservas por canal en el rango (por creación, sin importar estado)
        Map<String, Long> conteos = new HashMap<>();
        conteos.put("DIRECTO_WEB", 0L);
        conteos.put("RECEPCION", 0L);
        conteos.put("BOOKING", 0L);
        conteos.put("EXPEDIA", 0L);

        jdbc.query("""
                SELECT channel, count(*) AS cantidad
                FROM reservations
                WHERE created_at >= ? AND created_at < ?
                GROUP BY channel
                """, rs -> {
                    String canal = rs.getString("channel");
                    long cant = rs.getLong("cantidad");
                    conteos.put(canal, cant);
                }, desde, hasta);

        List<ReservasPorCanal> reservasPorCanal = List.of(
                new ReservasPorCanal("DIRECTO_WEB", conteos.getOrDefault("DIRECTO_WEB", 0L)),
                new ReservasPorCanal("RECEPCION", conteos.getOrDefault("RECEPCION", 0L)),
                new ReservasPorCanal("BOOKING", conteos.getOrDefault("BOOKING", 0L)),
                new ReservasPorCanal("EXPEDIA", conteos.getOrDefault("EXPEDIA", 0L))
        );

        return new Indicadores(fechaInicio, fechaFin, ocupacionHoy, ingresos, reservasPorCanal);
    }
}
