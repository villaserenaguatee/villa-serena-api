package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.reservas.dto.AgregarCargoPeticion;
import com.villaserena.api.reservas.dto.CargoCuentaRecepcion;
import com.villaserena.api.reservas.dto.CuentaHuesped;
import com.villaserena.api.reservas.dto.CuentaRecepcion;
import com.villaserena.api.reservas.dto.PagoCuentaRecepcion;
import com.villaserena.api.reservas.dto.PrecioNoche;

/**
 * Cuenta de la reserva (HU-REC-13, HU-HUE-15): se consulta en cualquier estado.
 * Saldo = cargos VIGENTE − pagos APROBADO (RN-PAG-021).
 */
@Service
public class CuentaService {

    private final ReservaRepository reservas;
    private final CuentaRepository cuentas;
    private final CargoRepository cargos;
    private final CargoService cargoService;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public CuentaService(ReservaRepository reservas, CuentaRepository cuentas, CargoRepository cargos,
            CargoService cargoService, JdbcTemplate jdbc, Clock reloj) {
        this.reservas = reservas;
        this.cuentas = cuentas;
        this.cargos = cargos;
        this.cargoService = cargoService;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Saldo = cargos VIGENTE − pagos APROBADO. Puede ser negativo. */
    @Transactional(readOnly = true)
    public BigDecimal saldo(long cuentaId) {
        return totalCargosVigentes(cuentaId).subtract(totalPagosAprobados(cuentaId)).setScale(2);
    }

    @Transactional(readOnly = true)
    public CuentaRecepcion paraRecepcion(String codigo) {
        return vista(reservas.findByCodigo(codigo).orElseThrow(ApiException::noEncontrado));
    }

    /** Solo las reservas del propio huésped; una ajena responde 404 (VIS-01). */
    @Transactional(readOnly = true)
    public CuentaHuesped paraHuesped(String codigo, long huespedId) {
        Reserva reserva = reservas.findByCodigo(codigo)
                .filter(r -> r.getHuespedId() == huespedId)
                .orElseThrow(ApiException::noEncontrado);
        return CuentaHuesped.de(vista(reserva));
    }

    /** Cargo manual de Recepción (RN-PAG-019): tipo SERVICIO, categoría OTRO. */
    @Transactional
    public CargoCuentaRecepcion agregarCargo(String codigo, AgregarCargoPeticion p, long empleadoId) {
        Cuenta cuenta = cuentaDe(codigo);
        Cargo cargo = cargoService.registrarCargo(cuenta.getId(), new NuevoCargo(TipoCargo.SERVICIO,
                CategoriaServicio.OTRO, p.concepto().trim(), p.cantidad(), p.precioUnitario(), null),
                Responsable.empleado(empleadoId));
        return cargosDe(cuenta.getId()).stream().filter(c -> c.id().equals(cargo.getId())).findFirst().orElseThrow();
    }

    /**
     * Anula un cargo adicional con motivo (RN-PAG-011): solo con la cuenta ABIERTA y
     * nunca el alojamiento. El cargo queda visible como ANULADO y deja de sumar.
     */
    @Transactional
    public CargoCuentaRecepcion anularCargo(String codigo, long cargoId, String motivo, long empleadoId) {
        Cuenta cuenta = cuentaDe(codigo);
        Cargo cargo = cargos.findById(cargoId)
                .filter(c -> c.getCuentaId().equals(cuenta.getId()))
                .orElseThrow(ApiException::noEncontrado);
        if (!cuenta.estaAbierta()) {
            throw ApiException.conflicto("CUENTA_CERRADA", "Con la cuenta cerrada no se anulan cargos.");
        }
        if (cargo.getTipo() == TipoCargo.ALOJAMIENTO) {
            throw ApiException.conflicto("CARGO_NO_ANULABLE", "El cargo por alojamiento no se anula.");
        }
        if (cargo.getEstado() == EstadoCargo.ANULADO) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "El cargo ya está anulado.");
        }
        cargo.setEstado(EstadoCargo.ANULADO);
        cargo.setMotivoAnulacion(motivo.trim());
        cargo.setAnuladoPorEmpleadoId(empleadoId);
        cargo.setAnuladoEn(reloj.instant());
        cargos.saveAndFlush(cargo);
        return cargosDe(cuenta.getId()).stream().filter(c -> c.id().equals(cargoId)).findFirst().orElseThrow();
    }

    private Cuenta cuentaDe(String codigo) {
        Reserva reserva = reservas.findByCodigo(codigo).orElseThrow(ApiException::noEncontrado);
        return cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
    }

    private CuentaRecepcion vista(Reserva reserva) {
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        String nombre = jdbc.queryForObject("SELECT nombre_completo FROM huespedes WHERE id = ?", String.class,
                reserva.getHuespedId());
        List<PrecioNoche> noches = jdbc.query("""
                SELECT fecha, precio, temporada_nombre, fin_de_semana FROM reserva_noches
                WHERE reserva_id = ? ORDER BY fecha""",
                (rs, i) -> new PrecioNoche(rs.getObject(1, java.time.LocalDate.class), rs.getBigDecimal(2),
                        rs.getString(3), rs.getBoolean(4)),
                reserva.getId());
        Long facturaId = jdbc.query("SELECT id FROM facturas WHERE cuenta_id = ?", (rs, i) -> rs.getLong(1),
                cuenta.getId()).stream().findFirst().orElse(null);
        BigDecimal cargosVigentes = totalCargosVigentes(cuenta.getId());
        BigDecimal pagosAprobados = totalPagosAprobados(cuenta.getId());
        return new CuentaRecepcion(reserva.getCodigo(), reserva.getEstado(), cuenta.getEstado(), nombre, noches,
                cargosVigentes, pagosAprobados, cargosVigentes.subtract(pagosAprobados).setScale(2), facturaId,
                cargosDe(cuenta.getId()), pagosDe(cuenta.getId(), reserva.getCanal()));
    }

    private List<CargoCuentaRecepcion> cargosDe(long cuentaId) {
        return jdbc.query("""
                SELECT c.id, c.creado_en, c.tipo, c.concepto, c.cantidad, c.precio_unitario, c.total, c.estado,
                       c.motivo_anulacion, e.nombre_completo, a.nombre_completo
                FROM cargos c
                LEFT JOIN empleados e ON e.id = c.creado_por_empleado_id
                LEFT JOIN empleados a ON a.id = c.anulado_por_empleado_id
                WHERE c.cuenta_id = ?
                ORDER BY c.creado_en, c.id""",
                (rs, i) -> new CargoCuentaRecepcion(rs.getLong(1), fecha(rs.getTimestamp(2)),
                        TipoCargo.valueOf(rs.getString(3)), rs.getString(4), rs.getInt(5), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), EstadoCargo.valueOf(rs.getString(8)), rs.getString(9),
                        rs.getString(10) == null ? "SISTEMA" : rs.getString(10), rs.getString(11)),
                cuentaId);
    }

    private List<PagoCuentaRecepcion> pagosDe(long cuentaId, CanalReserva canal) {
        return jdbc.query("""
                SELECT p.id, p.creado_en, p.metodo, p.monto, p.estado, p.referencia, e.nombre_completo
                FROM pagos p
                LEFT JOIN empleados e ON e.id = p.registrado_por_empleado_id
                WHERE p.cuenta_id = ?
                ORDER BY p.creado_en, p.id""",
                (rs, i) -> {
                    MetodoPago metodo = MetodoPago.valueOf(rs.getString(3));
                    String responsable = rs.getString(7) != null ? rs.getString(7)
                            : metodo == MetodoPago.CANAL ? canal.name() : metodo.name();
                    return new PagoCuentaRecepcion(rs.getLong(1), fecha(rs.getTimestamp(2)), metodo,
                            rs.getBigDecimal(4), EstadoPago.valueOf(rs.getString(5)), rs.getString(6), responsable);
                },
                cuentaId);
    }

    private BigDecimal totalCargosVigentes(long cuentaId) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(total), 0) FROM cargos WHERE cuenta_id = ? AND estado = 'VIGENTE'",
                BigDecimal.class, cuentaId).setScale(2);
    }

    private BigDecimal totalPagosAprobados(long cuentaId) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(monto), 0) FROM pagos WHERE cuenta_id = ? AND estado = 'APROBADO'",
                BigDecimal.class, cuentaId).setScale(2);
    }

    private OffsetDateTime fecha(Timestamp t) {
        return OffsetDateTime.ofInstant(t.toInstant(), reloj.getZone());
    }
}
