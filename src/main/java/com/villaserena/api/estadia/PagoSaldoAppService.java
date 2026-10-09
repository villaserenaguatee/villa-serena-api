package com.villaserena.api.estadia;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.Cuenta;
import com.villaserena.api.reservas.CuentaRepository;
import com.villaserena.api.reservas.CuentaService;
import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.MetodoPago;
import com.villaserena.api.reservas.Pago;
import com.villaserena.api.reservas.PagoRepository;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.dto.PagoIniciado;
import com.villaserena.api.reservas.stripe.PagoStripeService;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pago del saldo desde la app (HU-HUE-16): solo EN_ESTADIA, de 00:00 a 12:00 del día
 * de salida y sin pedido en camino. Crea una sesión de Stripe Checkout por el saldo
 * completo; el webhook existente (sesión pagada) registra el pago APROBADO.
 */
@Service
public class PagoSaldoAppService {

    private static final Duration DURACION_SESION = Duration.ofMinutes(31);

    private final CheckoutService checkout;
    private final CuentaRepository cuentas;
    private final CuentaService cuentaService;
    private final PagoRepository pagos;
    private final PasarelaStripe stripe;
    private final Clock reloj;
    private final String urlRetorno;

    public PagoSaldoAppService(CheckoutService checkout, CuentaRepository cuentas, CuentaService cuentaService,
            PagoRepository pagos, PasarelaStripe stripe, Clock reloj, PropiedadesVillaSerena propiedades) {
        this.checkout = checkout;
        this.cuentas = cuentas;
        this.cuentaService = cuentaService;
        this.pagos = pagos;
        this.stripe = stripe;
        this.reloj = reloj;
        this.urlRetorno = propiedades.stripe().urlRetornoApp();
    }

    /** @return la sesión de pago, o vacío si el saldo ya es 0 (no se crea sesión). */
    @Transactional
    public Optional<PagoIniciado> iniciar(Reserva reserva) {
        if (reserva.getEstado() != EstadoReserva.EN_ESTADIA) {
            throw ApiException.conflicto("ESTADO_INVALIDO", "Solo se paga el saldo durante la estadía.");
        }
        if (!checkout.dentroDeVentanaApp(reserva)) {
            throw ApiException.conflicto("FUERA_DE_HORARIO",
                    "El pago desde la app es de 00:00 a 12:00 del día de salida.");
        }
        if (checkout.hayPedidoEnCamino(reserva.getId())) {
            throw ApiException.conflicto("PEDIDO_EN_CAMINO",
                    "Hay un pedido de room service en camino; espera a que se entregue.");
        }
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        var saldo = cuentaService.saldo(cuenta.getId());
        if (saldo.signum() <= 0) {
            return Optional.empty();
        }
        PasarelaStripe.Sesion sesion = stripe.crearSesion(
                "Saldo de la reserva " + reserva.getCodigo() + " — Hotel Villa Serena", saldo,
                urlRetorno + "?codigo=" + reserva.getCodigo(), reloj.instant().plus(DURACION_SESION),
                Map.of(PagoStripeService.META_CODIGO, reserva.getCodigo(),
                        PagoStripeService.META_TIPO, PagoStripeService.TIPO_SALDO));
        Pago pago = new Pago();
        pago.setCuentaId(cuenta.getId());
        pago.setMetodo(MetodoPago.STRIPE);
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setMonto(saldo);
        pago.setStripeSessionId(sesion.id());
        pago.setCreadoEn(reloj.instant());
        pagos.save(pago);
        OffsetDateTime expira = sesion.expiraEn() == null ? null : OffsetDateTime.ofInstant(sesion.expiraEn(),
                reloj.getZone());
        return Optional.of(new PagoIniciado(sesion.url(), expira));
    }
}
