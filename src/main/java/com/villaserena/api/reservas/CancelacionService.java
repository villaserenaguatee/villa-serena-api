package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.reservas.dto.VistaPreviaCancelacion;
import com.villaserena.api.reservas.dto.VistaPreviaCancelacion.Resultado;
import com.villaserena.api.reservas.stripe.ErrorStripe;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Cancelación desde Recepción (HU-REC-05): solo reservas CONFIRMADA que no sean de
 * canal externo, con motivo. Reembolso total por Stripe si faltan 48 h o más para las
 * 15:00 del día de llegada (America/Guatemala) y hubo pago en línea; si no, nada.
 */
@Service
public class CancelacionService {

    /** Hora fija de check-in (PAR-01). */
    public static final LocalTime HORA_CHECK_IN = LocalTime.of(15, 0);
    /** Umbral de reembolso total (PAR-07). */
    public static final Duration UMBRAL_REEMBOLSO = Duration.ofHours(48);

    private final ReservaService reservaService;
    private final CuentaRepository cuentas;
    private final PagoRepository pagos;
    private final PasarelaStripe stripe;
    private final Clock reloj;

    public CancelacionService(ReservaService reservaService, CuentaRepository cuentas, PagoRepository pagos,
            PasarelaStripe stripe, Clock reloj) {
        this.reservaService = reservaService;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.stripe = stripe;
        this.reloj = reloj;
    }

    /** Dice qué pasará con el dinero, sin cambiar nada. */
    @Transactional(readOnly = true)
    public VistaPreviaCancelacion vistaPrevia(String codigo) {
        Reserva reserva = reservaService.porCodigo(codigo);
        validarCancelable(reserva);
        return calcular(reserva, pagosStripeAprobados(reserva));
    }

    /**
     * Cancela la reserva. Si corresponde reembolso, primero lo pide a Stripe: si Stripe
     * lo rechaza, la reserva no se cancela (RN-CAN-008).
     */
    @Transactional
    public Reserva cancelar(String codigo, String motivo, long empleadoId) {
        Reserva reserva = reservaService.porCodigo(codigo);
        validarCancelable(reserva);
        List<Pago> stripeAprobados = pagosStripeAprobados(reserva);
        if (calcular(reserva, stripeAprobados).resultado() == Resultado.REEMBOLSO_TOTAL) {
            for (Pago pago : stripeAprobados) {
                try {
                    stripe.reembolsar(pago.getStripePaymentIntentId());
                } catch (ErrorStripe e) {
                    throw ApiException.conflicto("REEMBOLSO_RECHAZADO",
                            "Stripe rechazó el reembolso; la reserva no se canceló.");
                }
                pago.setEstado(EstadoPago.REEMBOLSADO);
                pago.setReembolsadoEn(reloj.instant());
            }
        }
        reservaService.cancelar(reserva, Responsable.empleado(empleadoId), motivo.trim());
        return reserva;
    }

    private VistaPreviaCancelacion calcular(Reserva reserva, List<Pago> stripeAprobados) {
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow();
        boolean tienePagos = pagos.findByCuentaIdOrderByCreadoEn(cuenta.getId()).stream()
                .anyMatch(p -> p.getEstado() == EstadoPago.APROBADO);
        if (!tienePagos) {
            return new VistaPreviaCancelacion(Resultado.SIN_PAGOS, BigDecimal.ZERO.setScale(2));
        }
        ZonedDateTime llegada = reserva.getFechaEntrada().atTime(HORA_CHECK_IN).atZone(reloj.getZone());
        boolean aTiempo = !Duration.between(reloj.instant(), llegada.toInstant()).minus(UMBRAL_REEMBOLSO).isNegative();
        if (aTiempo && !stripeAprobados.isEmpty()) {
            BigDecimal monto = stripeAprobados.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new VistaPreviaCancelacion(Resultado.REEMBOLSO_TOTAL, monto.setScale(2));
        }
        return new VistaPreviaCancelacion(Resultado.SIN_REEMBOLSO, BigDecimal.ZERO.setScale(2));
    }

    private List<Pago> pagosStripeAprobados(Reserva reserva) {
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow();
        return pagos.findByCuentaIdOrderByCreadoEn(cuenta.getId()).stream()
                .filter(p -> p.getMetodo() == MetodoPago.STRIPE && p.getEstado() == EstadoPago.APROBADO)
                .toList();
    }

    private static void validarCancelable(Reserva reserva) {
        if (reserva.getCanal().esExterno()) {
            throw ApiException.conflicto("CANAL_NO_CANCELABLE", "Las reservas de canal no se cancelan desde el sistema.");
        }
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "Solo se pueden cancelar reservas confirmadas.");
        }
    }
}
