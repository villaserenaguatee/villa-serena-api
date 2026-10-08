package com.villaserena.api.reservas;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.huespedes.HuespedService;
import com.villaserena.api.reservas.dto.CalendarioReservas;
import com.villaserena.api.reservas.dto.HabitacionReferencia;
import com.villaserena.api.reservas.dto.HuespedReferencia;
import com.villaserena.api.reservas.dto.PaginaReservas;
import com.villaserena.api.reservas.dto.ReservaCalendario;
import com.villaserena.api.reservas.dto.ReservaResumen;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;

/**
 * Consultas de Recepción sobre reservas (OBJ-2A): búsqueda con filtros y paginación
 * (HU-REC-06, HU-CM-02) y datos del Gantt (HU-REC-08). Solo lectura; "hoy" se
 * calcula en America/Guatemala con el reloj del API.
 */
@Service
public class BusquedaReservasService {

    /** Filtros rápidos de la búsqueda. */
    public enum FiltroRapido {
        LLEGAN_HOY, SALEN_HOY
    }

    /** Filtros combinables de la búsqueda; los nulos no se aplican. */
    public record Filtros(String texto, String codigo, LocalDate desde, LocalDate hasta, EstadoReserva estado,
            CanalReserva canal, FiltroRapido rapido) {
    }

    public static final int TAMANO_MAXIMO = 100;

    /** Saldo = cargos VIGENTE − pagos APROBADO de la cuenta (igual que CuentaService). */
    private static final String SALDO = """
            COALESCE((SELECT sum(cg.total) FROM cargos cg JOIN cuentas c ON c.id = cg.cuenta_id
                      WHERE c.reserva_id = r.id AND cg.estado = 'VIGENTE'), 0)
            - COALESCE((SELECT sum(p.monto) FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id
                        WHERE c.reserva_id = r.id AND p.estado = 'APROBADO'), 0)""";

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public BusquedaReservasService(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PaginaReservas buscar(Filtros f, int page, int size) {
        if (page < 0 || size < 1 || size > TAMANO_MAXIMO) {
            throw ApiException.datosInvalidos("La paginación no es válida.",
                    List.of(new DetalleError(page < 0 ? "page" : "size",
                            page < 0 ? "Debe ser 0 o mayor." : "Debe estar entre 1 y " + TAMANO_MAXIMO + ".")));
        }
        validarRango(f.desde(), f.hasta());
        if (f.codigo() != null && !f.codigo().isBlank()) {
            ReservaService.validarCodigo(f.codigo().trim().toUpperCase());
        }

        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> parametros = new ArrayList<>();
        if (f.texto() != null && !f.texto().isBlank()) {
            String patron = "%" + HuespedService.escaparLike(f.texto().trim()) + "%";
            where.append(" AND (h.nombre_completo ILIKE ? OR h.numero_documento ILIKE ?)");
            parametros.add(patron);
            parametros.add(patron);
        }
        if (f.codigo() != null && !f.codigo().isBlank()) {
            where.append(" AND r.codigo = ?");
            parametros.add(f.codigo().trim().toUpperCase());
        }
        if (f.desde() != null) {
            where.append(" AND r.fecha_salida > ?");
            parametros.add(f.desde());
        }
        if (f.hasta() != null) {
            where.append(" AND r.fecha_entrada <= ?");
            parametros.add(f.hasta());
        }
        if (f.estado() != null) {
            where.append(" AND r.estado = ?");
            parametros.add(f.estado().name());
        }
        if (f.canal() != null) {
            where.append(" AND r.canal = ?");
            parametros.add(f.canal().name());
        }
        if (f.rapido() == FiltroRapido.LLEGAN_HOY) {
            where.append(" AND r.estado = 'CONFIRMADA' AND r.fecha_entrada = ?");
            parametros.add(LocalDate.now(reloj));
        } else if (f.rapido() == FiltroRapido.SALEN_HOY) {
            where.append(" AND r.estado = 'EN_ESTADIA' AND r.fecha_salida = ?");
            parametros.add(LocalDate.now(reloj));
        }

        String desde = """
                FROM reservas r
                JOIN huespedes h ON h.id = r.huesped_id
                JOIN tipos_habitacion t ON t.id = r.tipo_habitacion_id
                LEFT JOIN habitaciones hab ON hab.id = r.habitacion_id""" + where;
        long total = jdbc.queryForObject("SELECT count(*) " + desde, Long.class, parametros.toArray());

        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(size);
        conPagina.add((long) page * size);
        List<ReservaResumen> contenido = jdbc.query("""
                SELECT r.codigo, h.id, h.nombre_completo, r.fecha_entrada, r.fecha_salida, r.numero_huespedes,
                       t.id, t.nombre, hab.id, hab.numero, hab.piso, r.estado, r.canal, r.identificador_externo,
                       r.total, (%s) AS saldo
                """.formatted(SALDO) + desde + " ORDER BY r.fecha_entrada, r.codigo LIMIT ? OFFSET ?",
                (rs, i) -> {
                    LocalDate entrada = rs.getObject(4, LocalDate.class);
                    LocalDate salida = rs.getObject(5, LocalDate.class);
                    HabitacionReferencia habitacion = rs.getObject(9) == null ? null
                            : new HabitacionReferencia(rs.getLong(9), rs.getString(10), rs.getInt(11));
                    return new ReservaResumen(rs.getString(1), new HuespedReferencia(rs.getLong(2), rs.getString(3)),
                            entrada, salida, (int) (salida.toEpochDay() - entrada.toEpochDay()), rs.getInt(6),
                            new TipoHabitacionReferencia(rs.getLong(7), rs.getString(8)), habitacion,
                            EstadoReserva.valueOf(rs.getString(12)), CanalReserva.valueOf(rs.getString(13)),
                            rs.getString(14), rs.getBigDecimal(15).setScale(2),
                            rs.getBigDecimal(16).setScale(2));
                }, conPagina.toArray());
        int totalPaginas = (int) ((total + size - 1) / size);
        return new PaginaReservas(contenido, page, size, total, totalPaginas);
    }

    /**
     * Gantt: habitaciones activas agrupadas por tipo y las reservas que se cruzan con
     * el rango, sin las CANCELADA. Las que no tienen habitación van en "Sin asignar".
     */
    @Transactional(readOnly = true)
    public CalendarioReservas calendario(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        Map<Long, CalendarioReservas.Grupo> grupos = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t.id, t.nombre, h.id, h.numero, h.piso
                FROM habitaciones h JOIN tipos_habitacion t ON t.id = h.tipo_habitacion_id
                WHERE h.estado = 'ACTIVO'
                ORDER BY t.id, h.piso, h.numero""", rs -> {
            TipoHabitacionReferencia tipo = new TipoHabitacionReferencia(rs.getLong(1), rs.getString(2));
            grupos.computeIfAbsent(tipo.id(), id -> new CalendarioReservas.Grupo(tipo, new ArrayList<>()))
                    .habitaciones().add(new HabitacionReferencia(rs.getLong(3), rs.getString(4), rs.getInt(5)));
        });
        List<ReservaCalendario> reservas = jdbc.query("""
                SELECT r.codigo, h.nombre_completo, r.fecha_entrada, r.fecha_salida, r.estado, r.canal,
                       r.tipo_habitacion_id, r.habitacion_id
                FROM reservas r JOIN huespedes h ON h.id = r.huesped_id
                WHERE r.estado <> 'CANCELADA' AND r.fecha_salida > ? AND r.fecha_entrada <= ?
                ORDER BY r.fecha_entrada, r.codigo""",
                (rs, i) -> new ReservaCalendario(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class),
                        EstadoReserva.valueOf(rs.getString(5)), CanalReserva.valueOf(rs.getString(6)),
                        rs.getLong(7), rs.getObject(8) == null ? null : rs.getLong(8)),
                desde, hasta);
        return new CalendarioReservas(desde, hasta, List.copyOf(grupos.values()), reservas);
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw ApiException.datosInvalidos("La fecha final no puede ser anterior a la inicial.",
                    List.of(new DetalleError("hasta", "Debe ser igual o posterior a desde.")));
        }
    }
}
