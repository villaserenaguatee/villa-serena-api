package com.villaserena.api.reservas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.dto.Cotizacion;
import com.villaserena.api.reservas.dto.TipoHabitacionPublico;

/**
 * Disponibilidad por tipo (RN-RES-008): en cada noche, habitaciones ACTIVO del tipo
 * que no estén FUERA_DE_SERVICIO − reservas del tipo en PENDIENTE_PAGO, CONFIRMADA o
 * EN_ESTADIA que ocupan esa noche (con o sin habitación). Un tipo solo se ofrece si
 * tiene cupo en todas las noches. La usan la web, Recepción y el canal.
 */
@Service
public class DisponibilidadService {

    public static final int MAX_NOCHES = 30;
    public static final int MAX_DIAS_ANTICIPACION = 365;

    private final JdbcTemplate jdbc;
    private final TarifaService tarifas;
    private final Clock reloj;
    private final String urlPublica;

    public DisponibilidadService(JdbcTemplate jdbc, TarifaService tarifas, Clock reloj,
            PropiedadesVillaSerena propiedades) {
        this.jdbc = jdbc;
        this.tarifas = tarifas;
        this.reloj = reloj;
        this.urlPublica = propiedades.archivos().urlPublica();
    }

    /** Opción disponible con su cotización y las habitaciones que quedan (solo Recepción ve este dato). */
    public record Opcion(TipoHabitacionInfo tipo, TipoHabitacionPublico tipoPublico, int restantes,
            Cotizacion cotizacion) {
    }

    /** Busca los tipos activos con capacidad suficiente y cupo en todas las noches. */
    public List<Opcion> buscar(LocalDate entrada, LocalDate salida, int huespedes) {
        validarEstadia(entrada, salida, huespedes);
        Map<Long, Integer> cupos = cuposPorTipo(entrada, salida, null);
        Map<Long, List<String>> fotos = fotosPorTipo();
        List<Opcion> opciones = new ArrayList<>();
        for (TipoHabitacionInfo tipo : tiposActivos()) {
            int restantes = cupos.getOrDefault(tipo.id(), 0);
            if (tipo.capacidad() < huespedes || restantes <= 0) {
                continue;
            }
            opciones.add(new Opcion(tipo, publico(tipo, fotos.getOrDefault(tipo.id(), List.of())), restantes,
                    tarifas.cotizar(tipo, entrada, salida)));
        }
        return opciones;
    }

    /** Cupo de un tipo en el rango; 0 si no hay habitaciones del tipo. */
    public int cupo(long tipoId, LocalDate entrada, LocalDate salida) {
        return cuposPorTipo(entrada, salida, tipoId).getOrDefault(tipoId, 0);
    }

    /**
     * Valida las reglas de fechas y huéspedes (RN-RES-005, RN-RES-006): 1 a 30
     * noches, sin fechas pasadas y entrada a no más de 365 días de hoy
     * (America/Guatemala).
     */
    public void validarEstadia(LocalDate entrada, LocalDate salida, int huespedes) {
        LocalDate hoy = LocalDate.now(reloj);
        if (entrada.isBefore(hoy)) {
            throw fechasInvalidas("entrada", "La fecha de entrada no puede ser pasada.");
        }
        if (!salida.isAfter(entrada)) {
            throw fechasInvalidas("salida", "La salida debe ser posterior a la entrada.");
        }
        if (ChronoUnit.DAYS.between(entrada, salida) > MAX_NOCHES) {
            throw fechasInvalidas("salida", "La estadía debe ser de 1 a 30 noches.");
        }
        if (entrada.isAfter(hoy.plusDays(MAX_DIAS_ANTICIPACION))) {
            throw fechasInvalidas("entrada", "La entrada no puede ser a más de 365 días de hoy.");
        }
        if (huespedes < 1) {
            throw ApiException.datosInvalidos("Los datos enviados no son válidos.",
                    List.of(new DetalleError("huespedes", "Debe haber al menos un huésped.")));
        }
    }

    public Optional<TipoHabitacionInfo> tipo(long tipoId) {
        return jdbc.query(SELECT_TIPO + " WHERE id = ?", (rs, i) -> mapear(rs), tipoId).stream().findFirst();
    }

    /** Bloquea la fila del tipo para que dos reservas simultáneas no tomen el mismo cupo. */
    public Optional<TipoHabitacionInfo> bloquearTipo(long tipoId) {
        return jdbc.query(SELECT_TIPO + " WHERE id = ? FOR UPDATE", (rs, i) -> mapear(rs), tipoId).stream()
                .findFirst();
    }

    public TipoHabitacionPublico publico(TipoHabitacionInfo tipo) {
        return publico(tipo, fotosPorTipo().getOrDefault(tipo.id(), List.of()));
    }

    /** Catálogo público (HU-HUE-02): solo los tipos ACTIVO, con sus fotos (la principal primero). */
    public List<TipoHabitacionPublico> catalogo() {
        Map<Long, List<String>> fotos = fotosPorTipo();
        return tiposActivos().stream().map(t -> publico(t, fotos.getOrDefault(t.id(), List.of()))).toList();
    }

    private static final String SELECT_TIPO = """
            SELECT id, nombre, descripcion, capacidad, precio_base, ajuste_fin_semana_pct, estado
            FROM tipos_habitacion""";

    private List<TipoHabitacionInfo> tiposActivos() {
        return jdbc.query(SELECT_TIPO + " WHERE estado = 'ACTIVO' ORDER BY precio_base, id", (rs, i) -> mapear(rs));
    }

    private static TipoHabitacionInfo mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TipoHabitacionInfo(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getInt("capacidad"), rs.getBigDecimal("precio_base"), rs.getBigDecimal("ajuste_fin_semana_pct"),
                "ACTIVO".equals(rs.getString("estado")));
    }

    /** Cupo mínimo de cada tipo en las noches del rango. Si {@code tipoId} no es nulo, solo ese tipo. */
    private Map<Long, Integer> cuposPorTipo(LocalDate entrada, LocalDate salida, Long tipoId) {
        String sql = """
                WITH noches AS (
                    SELECT generate_series(?::date, ?::date - 1, interval '1 day')::date AS d
                ),
                capacidad AS (
                    SELECT tipo_habitacion_id AS tipo, count(*) AS n
                    FROM habitaciones
                    WHERE estado = 'ACTIVO' AND condicion <> 'FUERA_DE_SERVICIO'
                    GROUP BY tipo_habitacion_id
                ),
                ocupadas AS (
                    SELECT r.tipo_habitacion_id AS tipo, n.d, count(*) AS c
                    FROM reservas r
                    JOIN noches n ON r.fecha_entrada <= n.d AND r.fecha_salida > n.d
                    WHERE r.estado IN ('PENDIENTE_PAGO', 'CONFIRMADA', 'EN_ESTADIA')
                    GROUP BY r.tipo_habitacion_id, n.d
                )
                SELECT t.id,
                       coalesce(c.n, 0) - coalesce((SELECT max(o.c) FROM ocupadas o WHERE o.tipo = t.id), 0) AS cupo
                FROM tipos_habitacion t
                LEFT JOIN capacidad c ON c.tipo = t.id
                WHERE (?::bigint IS NULL OR t.id = ?::bigint)""";
        return jdbc.query(sql, (rs, i) -> Map.entry(rs.getLong(1), rs.getInt(2)), entrada, salida, tipoId, tipoId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Long, List<String>> fotosPorTipo() {
        return jdbc.query("""
                SELECT tipo_habitacion_id, clave_objeto FROM fotos_tipo_habitacion
                ORDER BY tipo_habitacion_id, es_principal DESC, orden, id""",
                (rs, i) -> Map.entry(rs.getLong(1), urlPublica + "/" + rs.getString(2)))
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static TipoHabitacionPublico publico(TipoHabitacionInfo tipo, List<String> fotos) {
        return new TipoHabitacionPublico(tipo.id(), tipo.nombre(), tipo.descripcion(), tipo.capacidad(),
                tipo.precioBase(), fotos);
    }

    private static ApiException fechasInvalidas(String campo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "FECHAS_INVALIDAS", mensaje,
                List.of(new DetalleError(campo, mensaje)));
    }
}
