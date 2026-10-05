package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.huespedes.Huesped;
import com.villaserena.api.huespedes.HuespedService;
import com.villaserena.api.notificaciones.ConfirmacionReservaNotifier;
import com.villaserena.api.reservas.dto.Cotizacion;
import com.villaserena.api.reservas.dto.PrecioNoche;

/**
 * Crea, confirma y cancela reservas. Es el mismo servicio para la web (OBJ-1B),
 * Recepción (OBJ-2B) y el canal (Hugo, OBJ-1C): las reglas no se duplican.
 */
@Service
public class ReservaService {

    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ReservaRepository reservas;
    private final ReservaNocheRepository noches;
    private final CuentaRepository cuentas;
    private final PagoRepository pagos;
    private final CargoService cargos;
    private final DisponibilidadService disponibilidad;
    private final TarifaService tarifas;
    private final HuespedService huespedes;
    private final HistorialEstadoService historial;
    private final ObjectProvider<ConfirmacionReservaNotifier> notificador;
    private final Clock reloj;

    public ReservaService(ReservaRepository reservas, ReservaNocheRepository noches, CuentaRepository cuentas,
            PagoRepository pagos, CargoService cargos, DisponibilidadService disponibilidad, TarifaService tarifas,
            HuespedService huespedes, HistorialEstadoService historial,
            ObjectProvider<ConfirmacionReservaNotifier> notificador, Clock reloj) {
        this.reservas = reservas;
        this.noches = noches;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.cargos = cargos;
        this.disponibilidad = disponibilidad;
        this.tarifas = tarifas;
        this.huespedes = huespedes;
        this.historial = historial;
        this.notificador = notificador;
        this.reloj = reloj;
    }

    /**
     * Crea la reserva con su cuenta ABIERTA y el cargo por alojamiento (RN-PAG-009).
     * <ul>
     * <li>Revalida la disponibilidad dentro de la transacción, con la fila del tipo
     * bloqueada (RN-RES-001).</li>
     * <li>Web: nace PENDIENTE_PAGO. Recepción y canal: nacen CONFIRMADA (RN-RES-011).</li>
     * <li>Canal: el alojamiento es el monto del canal, en una sola línea, con un pago
     * APROBADO método CANAL (RN-TAR-010, RN-PAG-008).</li>
     * </ul>
     */
    @Transactional
    public Reserva crear(SolicitudReserva solicitud) {
        disponibilidad.validarEstadia(solicitud.entrada(), solicitud.salida(), solicitud.numeroHuespedes());
        TipoHabitacionInfo tipo = disponibilidad.bloquearTipo(solicitud.tipoHabitacionId())
                .filter(TipoHabitacionInfo::activo)
                .orElseThrow(() -> ApiException.datosInvalidos("El tipo de habitación no existe o no está activo.",
                        List.of(new DetalleError("tipoHabitacionId", "No existe o no está activo."))));
        if (tipo.capacidad() < solicitud.numeroHuespedes()) {
            throw ApiException.datosInvalidos("El número de huéspedes supera la capacidad del tipo de habitación.",
                    List.of(new DetalleError("numeroHuespedes",
                            "La capacidad máxima es de " + tipo.capacidad() + " huéspedes.")));
        }
        if (disponibilidad.cupo(tipo.id(), solicitud.entrada(), solicitud.salida()) <= 0) {
            throw ApiException.conflicto("SIN_DISPONIBILIDAD",
                    "Ya no hay habitaciones disponibles de ese tipo para esas fechas.");
        }

        boolean externo = solicitud.canal().esExterno();
        Cotizacion cotizacion = externo ? null : tarifas.cotizar(tipo, solicitud.entrada(), solicitud.salida());
        BigDecimal total = externo ? solicitud.montoCanal().setScale(2, RoundingMode.HALF_UP) : cotizacion.total();
        Huesped huesped = huespedes.obtenerOCrear(solicitud.huesped());
        Instant ahora = reloj.instant();

        Reserva reserva = new Reserva();
        reserva.setCodigo(nuevoCodigo());
        reserva.setHuespedId(huesped.getId());
        reserva.setTipoHabitacionId(tipo.id());
        reserva.setFechaEntrada(solicitud.entrada());
        reserva.setFechaSalida(solicitud.salida());
        reserva.setNumeroHuespedes(solicitud.numeroHuespedes());
        reserva.setCanal(solicitud.canal());
        reserva.setIdentificadorExterno(externo ? solicitud.identificadorExterno() : null);
        reserva.setEstado(solicitud.canal() == CanalReserva.DIRECTO_WEB
                ? EstadoReserva.PENDIENTE_PAGO
                : EstadoReserva.CONFIRMADA);
        reserva.setTotal(total);
        reserva.setCreadaPorEmpleadoId(solicitud.responsable().empleadoId());
        reserva.setCreadaEn(ahora);
        reservas.saveAndFlush(reserva);

        if (!externo) {
            for (PrecioNoche n : cotizacion.desglose()) {
                ReservaNoche noche = new ReservaNoche();
                noche.setReservaId(reserva.getId());
                noche.setFecha(n.fecha());
                noche.setPrecio(n.precio());
                noche.setTemporadaNombre(n.temporada());
                noche.setFinDeSemana(n.finDeSemana());
                noches.save(noche);
            }
        }

        Cuenta cuenta = new Cuenta();
        cuenta.setReservaId(reserva.getId());
        cuenta.setAbiertaEn(ahora);
        cuentas.save(cuenta);
        cargos.registrarAlojamiento(cuenta.getId(), conceptoAlojamiento(tipo.nombre(), reserva.noches()), total);

        if (externo) {
            Pago pago = new Pago();
            pago.setCuentaId(cuenta.getId());
            pago.setMetodo(MetodoPago.CANAL);
            pago.setEstado(EstadoPago.APROBADO);
            pago.setMonto(total);
            pago.setReferencia(solicitud.identificadorExterno());
            pago.setCreadoEn(ahora);
            pago.setAprobadoEn(ahora);
            pagos.save(pago);
        }

        historial.registrar(TipoEntidadHistorial.RESERVA, reserva.getId(), null, reserva.getEstado(),
                solicitud.responsable(), null);
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            notificarConfirmacion(reserva);
        }
        return reserva;
    }

    /** PENDIENTE_PAGO → CONFIRMADA al aprobarse el pago en línea (RN-PAG-001). */
    @Transactional
    public void confirmarPagoWeb(Reserva reserva, Responsable responsable) {
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            return;
        }
        cambiarEstado(reserva, EstadoReserva.CONFIRMADA, responsable, null);
        notificarConfirmacion(reserva);
    }

    /**
     * Cancela la reserva: CANCELADA y cuenta CERRADA tal como está; la habitación y el
     * cupo quedan libres porque una reserva cancelada ya no ocupa (RN-CAN-013). El
     * reembolso, si corresponde, lo decide quien llama antes de cancelar.
     */
    @Transactional
    public void cancelar(Reserva reserva, Responsable responsable, String motivo) {
        cambiarEstado(reserva, EstadoReserva.CANCELADA, responsable, motivo);
        cuentas.findByReservaId(reserva.getId()).ifPresent(cuenta -> {
            cuenta.setEstado(EstadoCuenta.CERRADA);
            cuenta.setCerradaEn(reloj.instant());
        });
    }

    @Transactional(readOnly = true)
    public Reserva porCodigo(String codigo) {
        return reservas.findByCodigo(codigo).orElseThrow(ApiException::noEncontrado);
    }

    /** Cambia el estado y lo registra en el historial (RG-EST-02). */
    @Transactional
    public void cambiarEstado(Reserva reserva, EstadoReserva nuevo, Responsable responsable, String motivo) {
        EstadoReserva anterior = reserva.getEstado();
        reserva.setEstado(nuevo);
        reservas.save(reserva);
        historial.registrar(TipoEntidadHistorial.RESERVA, reserva.getId(), anterior, nuevo, responsable, motivo);
    }

    public static void validarCodigo(String codigo) {
        if (codigo == null || !codigo.matches("^VS-[A-Z0-9]{6}$")) {
            throw ApiException.datosInvalidos("El código de reserva no es válido.",
                    List.of(new DetalleError("codigo", "Debe tener el formato VS-XXXXXX.")));
        }
    }

    private void notificarConfirmacion(Reserva reserva) {
        notificador.ifAvailable(n -> n.notificar(reserva.getId()));
    }

    private static String conceptoAlojamiento(String tipo, int noches) {
        return "Alojamiento " + tipo + " (" + noches + (noches == 1 ? " noche)" : " noches)");
    }

    /** Código único no secuencial, por ejemplo VS-7K2M9Q (RN-RES-009). */
    private String nuevoCodigo() {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder codigo = new StringBuilder("VS-");
            for (int i = 0; i < 6; i++) {
                codigo.append(ALFABETO_CODIGO.charAt(ALEATORIO.nextInt(ALFABETO_CODIGO.length())));
            }
            if (!reservas.existsByCodigo(codigo.toString())) {
                return codigo.toString();
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "No se pudo generar el código de la reserva. Intenta de nuevo.");
    }
}
