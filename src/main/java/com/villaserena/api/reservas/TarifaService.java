package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.villaserena.api.reservas.dto.Cotizacion;
import com.villaserena.api.reservas.dto.PrecioNoche;

/**
 * Precio de la estadía (RN-TAR-001, 002, 006): cada noche = precio base ×
 * (1 + % temporada vigente) × (1 + % fin de semana si es viernes o sábado),
 * redondeada a 2 decimales; el total es la suma de las noches.
 */
@Service
public class TarifaService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;

    public TarifaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Cotizacion cotizar(TipoHabitacionInfo tipo, LocalDate entrada, LocalDate salida) {
        List<Temporada> temporadas = temporadasDelRango(tipo.id(), entrada, salida);
        List<PrecioNoche> desglose = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate noche = entrada; noche.isBefore(salida); noche = noche.plusDays(1)) {
            Temporada temporada = vigente(temporadas, noche);
            boolean finDeSemana = esFinDeSemana(noche);
            BigDecimal precio = tipo.precioBase();
            if (temporada != null) {
                precio = precio.multiply(factor(temporada.ajustePct()));
            }
            if (finDeSemana) {
                precio = precio.multiply(factor(tipo.ajusteFinSemanaPct()));
            }
            precio = precio.setScale(2, RoundingMode.HALF_UP);
            desglose.add(new PrecioNoche(noche, precio, temporada == null ? null : temporada.nombre(), finDeSemana));
            total = total.add(precio);
        }
        return new Cotizacion(desglose.size(), total.setScale(2, RoundingMode.HALF_UP), List.copyOf(desglose));
    }

    /** Viernes y sábado (PAR-13). */
    static boolean esFinDeSemana(LocalDate noche) {
        return noche.getDayOfWeek() == DayOfWeek.FRIDAY || noche.getDayOfWeek() == DayOfWeek.SATURDAY;
    }

    private static BigDecimal factor(BigDecimal porcentaje) {
        return BigDecimal.ONE.add(porcentaje.divide(CIEN));
    }

    private static Temporada vigente(List<Temporada> temporadas, LocalDate noche) {
        return temporadas.stream()
                .filter(t -> !noche.isBefore(t.inicio()) && !noche.isAfter(t.fin()))
                .findFirst()
                .orElse(null);
    }

    private List<Temporada> temporadasDelRango(long tipoId, LocalDate entrada, LocalDate salida) {
        return jdbc.query("""
                SELECT t.nombre, t.fecha_inicio, t.fecha_fin, t.ajuste_pct
                FROM temporadas t
                WHERE t.fecha_inicio < ? AND t.fecha_fin >= ?
                  AND (t.aplica_a_todos OR EXISTS (
                        SELECT 1 FROM temporada_tipos_habitacion tt
                        WHERE tt.temporada_id = t.id AND tt.tipo_habitacion_id = ?))
                ORDER BY t.fecha_inicio""",
                (rs, i) -> new Temporada(rs.getString(1), rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalDate.class), rs.getBigDecimal(4)),
                salida, entrada, tipoId);
    }

    private record Temporada(String nombre, LocalDate inicio, LocalDate fin, BigDecimal ajustePct) {
    }
}
