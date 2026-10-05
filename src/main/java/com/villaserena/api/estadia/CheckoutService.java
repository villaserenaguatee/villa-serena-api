package com.villaserena.api.estadia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.estadia.dto.CheckoutResultado;
import com.villaserena.api.estadia.dto.PagoRecepcionPeticion;
import com.villaserena.api.estadia.dto.VistaCheckout;
import com.villaserena.api.facturacion.EmisorFactura;
import com.villaserena.api.notificaciones.HabitacionEventos;
import com.villaserena.api.reservas.Cuenta;
import com.villaserena.api.reservas.CuentaRepository;
import com.villaserena.api.reservas.CuentaService;
import com.villaserena.api.reservas.EstadoCuenta;
import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.MetodoPago;
import com.villaserena.api.reservas.Pago;
import com.villaserena.api.reservas.PagoRepository;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.reservas.ReservaService;

/**
 * Check-out (HU-REC-14, HU-HUE-16). Es el mismo servicio para Recepción y para la
 * app, y se completa en una sola transacción (RN-RES-022): si algo falla (por
 * ejemplo, la factura), no cambia nada y en Recepción tampoco se registra el pago.
 */
@Service
public class CheckoutService {

    /** Hora fija de check-out (PAR-02); también es el fin de la ventana de la app (PAR-17). */
    public static final LocalTime HORA_CHECK_OUT = LocalTime.of(12, 0);

    public enum Origen {
        RECEPCION, APP
    }

    private final ReservaRepository reservas;
    private final ReservaService reservaService;
    private final CuentaRepository cuentas;
    private final CuentaService cuentaService;
    private final PagoRepository pagos;
    private final HistorialEstadoService historial;
    private final ObjectProvider<EmisorFactura> emisorFactura;
    private final ObjectProvider<HabitacionEventos> eventos;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public CheckoutService(ReservaRepository reservas, ReservaService reservaService, CuentaRepository cuentas,
            CuentaService cuentaService, PagoRepository pagos, HistorialEstadoService historial,
            ObjectProvider<EmisorFactura> emisorFactura, ObjectProvider<HabitacionEventos> eventos,
            JdbcTemplate jdbc, Clock reloj) {
        this.reservas = reservas;
        this.reservaService = reservaService;
        this.cuentas = cuentas;
        this.cuentaService = cuentaService;
        this.pagos = pagos;
        this.historial = historial;
        this.emisorFactura = emisorFactura;
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Reserva por código; con {@code huespedId}, solo si es del huésped (si no, 404). */
    @Transactional(readOnly = true)
    public Reserva reserva(String codigo, Long huespedId) {
        return reservas.findByCodigo(codigo)
                .filter(r -> huespedId == null || r.getHuespedId() == huespedId.longValue())
                .orElseThrow(ApiException::noEncontrado);
    }

    /** Condiciones del check-out, sin efectos. */
    @Transactional(readOnly = true)
    public VistaCheckout vista(Reserva reserva, Origen origen) {
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        BigDecimal saldo = cuentaService.saldo(cuenta.getId());
        boolean enCamino = hayPedidoEnCamino(reserva.getId());
        List<String> bloqueos = new ArrayList<>();
        if (reserva.getEstado() != EstadoReserva.EN_ESTADIA) {
            bloqueos.add("La reserva no está en estadía.");
        }
        if (enCamino) {
            bloqueos.add("Hay un pedido de room service en camino.");
        }
        if (saldo.signum() < 0) {
            bloqueos.add("La cuenta tiene saldo a favor; revisa los pagos antes del check-out.");
        }
        if (origen == Origen.APP) {
            if (!dentroDeVentanaApp(reserva)) {
                bloqueos.add("El check-out desde la app es de 00:00 a 12:00 del día de salida.");
            }
            if (saldo.signum() > 0) {
                bloqueos.add("Paga el saldo pendiente antes del check-out.");
            }
        }
        String nombre = jdbc.queryForObject("SELECT nombre_completo FROM huespedes WHERE id = ?", String.class,
                reserva.getHuespedId());
        return new VistaCheckout(reserva.getCodigo(), saldo, nombre, enCamino, pedidosACancelar(reserva.getId()),
                bloqueos.isEmpty(), bloqueos);
    }

    /**
     * Confirma el check-out:
     * <ol>
     * <li>Recepción: si hay saldo, UN solo pago por el total (EFECTIVO, TARJETA u OTRO).
     * App: el saldo ya debe ser 0 (pagado con Stripe).</li>
     * <li>Factura (Hugo), reserva FINALIZADA y cuenta CERRADA.</li>
     * <li>Habitación LIBRE + SUCIA, o FUERA_DE_SERVICIO si tiene una incidencia que
     * impide su uso.</li>
     * <li>Pedidos NUEVO o EN_PREPARACION → CANCELADO sin cargo; solicitudes PENDIENTE o
     * EN_PROCESO → CANCELADA; se borran los tokens de push del huésped.</li>
     * </ol>
     */
    @Transactional
    public CheckoutResultado hacerCheckout(Reserva reserva, Origen origen, String nit, String nombreComprador,
            PagoRecepcionPeticion pagoRecepcion, boolean aceptaCancelarPedidos, Responsable responsable) {
        if (reserva.getEstado() != EstadoReserva.EN_ESTADIA) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "Solo se puede hacer check-out de reservas en estadía.");
        }
        if (hayPedidoEnCamino(reserva.getId())) {
            throw ApiException.conflicto("PEDIDO_EN_CAMINO",
                    "Hay un pedido de room service en camino; espera a que se entregue.");
        }
        if (!Nit.esValido(nit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NIT_INVALIDO", "El NIT no es válido. Escribe CF si no tiene.",
                    List.of(new DetalleError("nit", "NIT inválido.")));
        }
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        BigDecimal saldo = cuentaService.saldo(cuenta.getId());
        if (saldo.signum() < 0) {
            throw ApiException.conflicto("SALDO_NEGATIVO",
                    "La cuenta tiene saldo a favor; revisa los pagos antes del check-out.");
        }

        if (origen == Origen.APP) {
            if (!dentroDeVentanaApp(reserva)) {
                throw ApiException.conflicto("FUERA_DE_HORARIO",
                        "El check-out desde la app es de 00:00 a 12:00 del día de salida.");
            }
            if (saldo.signum() > 0) {
                throw ApiException.conflicto("SALDO_PENDIENTE", "Paga el saldo pendiente antes del check-out.");
            }
            if (!pedidosACancelar(reserva.getId()).isEmpty() && !aceptaCancelarPedidos) {
                throw ApiException.conflicto("CONFIRMAR_CANCELACION_PEDIDOS",
                        "Tienes pedidos sin entregar: confirma que se cancelarán sin cargo.");
            }
        } else if (saldo.signum() > 0) {
            registrarPagoRecepcion(cuenta, saldo, pagoRecepcion, responsable);
        }

        EmisorFactura emisor = emisorFactura.getIfAvailable();
        if (emisor == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FACTURACION_NO_DISPONIBLE",
                    "La facturación todavía no está disponible; no se hizo el check-out.");
        }
        EmisorFactura.FacturaEmitida factura = emisor.emitir(cuenta.getId(), Nit.normalizar(nit),
                nombreComprador.trim());

        reservaService.cambiarEstado(reserva, EstadoReserva.FINALIZADA, responsable, null);
        cuenta.setEstado(EstadoCuenta.CERRADA);
        cuenta.setCerradaEn(reloj.instant());
        cuentas.save(cuenta);
        liberarHabitacion(reserva, responsable);
        cancelarPendientes(reserva.getId());
        jdbc.update("DELETE FROM dispositivos_push WHERE huesped_id = ?", reserva.getHuespedId());

        return new CheckoutResultado(reserva.getCodigo(), EstadoReserva.FINALIZADA, EstadoCuenta.CERRADA,
                BigDecimal.ZERO.setScale(2), factura.detalle());
    }

    /** Ventana de la app: desde las 00:00 del día de salida hasta las 12:00 (PAR-17). */
    public boolean dentroDeVentanaApp(Reserva reserva) {
        ZonedDateTime ahora = ZonedDateTime.now(reloj);
        LocalDate salida = reserva.getFechaSalida();
        return ahora.toLocalDate().equals(salida) && ahora.toLocalTime().isBefore(HORA_CHECK_OUT);
    }

    public boolean hayPedidoEnCamino(long reservaId) {
        Integer enCamino = jdbc.queryForObject(
                "SELECT count(*) FROM pedidos WHERE reserva_id = ? AND estado = 'EN_CAMINO'", Integer.class,
                reservaId);
        return enCamino != null && enCamino > 0;
    }

    private List<Long> pedidosACancelar(long reservaId) {
        return jdbc.queryForList(
                "SELECT id FROM pedidos WHERE reserva_id = ? AND estado IN ('NUEVO', 'EN_PREPARACION') ORDER BY id",
                Long.class, reservaId);
    }

    private void registrarPagoRecepcion(Cuenta cuenta, BigDecimal saldo, PagoRecepcionPeticion peticion,
            Responsable responsable) {
        if (peticion == null || peticion.metodo() == null) {
            throw ApiException.datosInvalidos("Hay saldo pendiente: indica el método de pago.",
                    List.of(new DetalleError("pago.metodo", "Elige EFECTIVO, TARJETA u OTRO.")));
        }
        if (peticion.metodo() != MetodoPago.EFECTIVO && peticion.metodo() != MetodoPago.TARJETA
                && peticion.metodo() != MetodoPago.OTRO) {
            throw ApiException.datosInvalidos("En Recepción el pago es en efectivo, tarjeta u otro.",
                    List.of(new DetalleError("pago.metodo", "Elige EFECTIVO, TARJETA u OTRO.")));
        }
        Pago pago = new Pago();
        pago.setCuentaId(cuenta.getId());
        pago.setMetodo(peticion.metodo());
        pago.setEstado(EstadoPago.APROBADO);
        pago.setMonto(saldo);
        pago.setReferencia(peticion.referencia() == null || peticion.referencia().isBlank() ? null
                : peticion.referencia().trim());
        pago.setRegistradoPorEmpleadoId(responsable.empleadoId());
        pago.setCreadoEn(reloj.instant());
        pago.setAprobadoEn(reloj.instant());
        pagos.saveAndFlush(pago);
    }

    /** LIBRE + SUCIA, o FUERA_DE_SERVICIO si hay una incidencia sin resolver que impide su uso (RN-HAB-008). */
    private void liberarHabitacion(Reserva reserva, Responsable responsable) {
        long habitacionId = reserva.getHabitacionId();
        String condicionAnterior = jdbc.queryForObject(
                "SELECT condicion FROM habitaciones WHERE id = ? FOR UPDATE", String.class, habitacionId);
        Integer impideUso = jdbc.queryForObject("""
                SELECT count(*) FROM incidencias
                WHERE habitacion_id = ? AND impide_uso AND estado <> 'RESUELTA'""", Integer.class, habitacionId);
        String condicionNueva = impideUso != null && impideUso > 0 ? "FUERA_DE_SERVICIO" : "SUCIA";
        jdbc.update("""
                UPDATE habitaciones SET ocupacion = 'LIBRE', condicion = ?, limpieza_empleado_id = NULL
                WHERE id = ?""", condicionNueva, habitacionId);
        historial.registrar(TipoEntidadHistorial.HABITACION_OCUPACION, habitacionId, "OCUPADA", "LIBRE",
                responsable, null);
        if (!condicionNueva.equals(condicionAnterior)) {
            historial.registrar(TipoEntidadHistorial.HABITACION_CONDICION, habitacionId, condicionAnterior,
                    condicionNueva, responsable, null);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventos.ifAvailable(e -> e.habitacionCambio(habitacionId));
            }
        });
    }

    /** Pedidos sin entregar y solicitudes abiertas se cancelan sin cargo (RN-RS-011, RN-LIM-009). */
    private void cancelarPendientes(long reservaId) {
        String motivo = "Cancelado por el check-out";
        for (var fila : jdbc.queryForList(
                "SELECT id, estado FROM pedidos WHERE reserva_id = ? AND estado IN ('NUEVO', 'EN_PREPARACION')",
                reservaId)) {
            long id = ((Number) fila.get("id")).longValue();
            jdbc.update("UPDATE pedidos SET estado = 'CANCELADO' WHERE id = ?", id);
            historial.registrar(TipoEntidadHistorial.PEDIDO, id, (String) fila.get("estado"), "CANCELADO",
                    Responsable.SISTEMA, motivo);
        }
        for (var fila : jdbc.queryForList(
                "SELECT id, estado FROM solicitudes WHERE reserva_id = ? AND estado IN ('PENDIENTE', 'EN_PROCESO')",
                reservaId)) {
            long id = ((Number) fila.get("id")).longValue();
            jdbc.update("UPDATE solicitudes SET estado = 'CANCELADA' WHERE id = ?", id);
            historial.registrar(TipoEntidadHistorial.SOLICITUD, id, (String) fila.get("estado"), "CANCELADA",
                    Responsable.SISTEMA, motivo);
        }
    }
}
