package com.villaserena.api.reservas.stripe;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.Cuenta;
import com.villaserena.api.reservas.CuentaRepository;
import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.MetodoPago;
import com.villaserena.api.reservas.Pago;
import com.villaserena.api.reservas.PagoRepository;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.dto.EstadoReservaPublico;
import com.villaserena.api.reservas.dto.PagoIniciado;

/**
 * Pago en línea con Stripe Checkout (HU-HUE-06; documento 14, AD-17).
 * <ul>
 * <li>Una sola sesión por reserva, por el 100 % del total (RN-PAG-003, RN-PAG-006).</li>
 * <li>El pago se confirma solo por el webhook, con firma e idempotencia (RN-PAG-001, 002).</li>
 * <li>Las sesiones llevan en su metadata el tipo de pago: {@code RESERVA} (web) o
 * {@code SALDO} (pago del saldo desde la app, OBJ-4A).</li>
 * </ul>
 */
@Service
public class PagoStripeService {

    public static final String META_CODIGO = "codigoReserva";
    public static final String META_TIPO = "tipoPago";
    public static final String TIPO_RESERVA = "RESERVA";
    public static final String TIPO_SALDO = "SALDO";

    static final String EVENTO_PAGADA = "checkout.session.completed";
    static final String EVENTO_VENCIDA = "checkout.session.expired";

    /** Stripe exige que una sesión dure al menos 30 minutos. */
    private static final Duration DURACION_MINIMA_SESION = Duration.ofMinutes(31);

    private static final Logger log = LoggerFactory.getLogger(PagoStripeService.class);

    private final PasarelaStripe stripe;
    private final ReservaRepository reservas;
    private final ReservaService reservaService;
    private final CuentaRepository cuentas;
    private final PagoRepository pagos;
    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final Duration plazoPago;
    private final String webUrl;

    public PagoStripeService(PasarelaStripe stripe, ReservaRepository reservas, ReservaService reservaService,
            CuentaRepository cuentas, PagoRepository pagos, JdbcTemplate jdbc, Clock reloj,
            PropiedadesVillaSerena propiedades) {
        this.stripe = stripe;
        this.reservas = reservas;
        this.reservaService = reservaService;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.plazoPago = Duration.ofMinutes(propiedades.reservas().minutosPago());
        this.webUrl = propiedades.webUrl();
    }

    /** Momento en que una reserva web sin pagar se cancela sola (PAR-06). */
    public Instant vencimientoPago(Reserva reserva) {
        return reserva.getCreadaEn().plus(plazoPago);
    }

    /**
     * Inicia (o retoma) el pago de una reserva web PENDIENTE_PAGO. Si ya hay una sesión
     * abierta devuelve la misma; si no, crea una nueva y el pago queda PENDIENTE.
     */
    @Transactional
    public PagoIniciado iniciarPagoReserva(String codigo) {
        Reserva reserva = reservaService.porCodigo(codigo);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            throw ApiException.conflicto("RESERVA_NO_PENDIENTE", "La reserva ya no está pendiente de pago.");
        }
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow(ApiException::noEncontrado);
        OffsetDateTime vence = OffsetDateTime.ofInstant(vencimientoPago(reserva), reloj.getZone());

        Optional<Pago> pendiente = pagos.findFirstByCuentaIdAndMetodoAndEstado(cuenta.getId(), MetodoPago.STRIPE,
                EstadoPago.PENDIENTE);
        if (pendiente.isPresent()) {
            PasarelaStripe.Sesion sesion = stripe.obtenerSesion(pendiente.get().getStripeSessionId());
            if (sesion.abierta()) {
                return new PagoIniciado(sesion.url(), vence);
            }
        }

        PasarelaStripe.Sesion sesion = stripe.crearSesion(
                "Reserva " + reserva.getCodigo() + " — Hotel Villa Serena",
                reserva.getTotal(),
                webUrl + "/reserva/resultado?codigo=" + reserva.getCodigo(),
                reloj.instant().plus(DURACION_MINIMA_SESION),
                Map.of(META_CODIGO, reserva.getCodigo(), META_TIPO, TIPO_RESERVA));
        Pago pago = new Pago();
        pago.setCuentaId(cuenta.getId());
        pago.setMetodo(MetodoPago.STRIPE);
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setMonto(reserva.getTotal());
        pago.setStripeSessionId(sesion.id());
        pago.setCreadoEn(reloj.instant());
        pagos.save(pago);
        return new PagoIniciado(sesion.url(), vence);
    }

    /** Estado público para la página de resultado (VIS-03): sin datos personales. */
    @Transactional(readOnly = true)
    public EstadoReservaPublico estadoPublico(String codigo) {
        Reserva reserva = reservaService.porCodigo(codigo);
        EstadoPago estadoPago = cuentas.findByReservaId(reserva.getId())
                .flatMap(c -> pagos.findFirstByCuentaIdAndMetodoOrderByCreadoEnDesc(c.getId(), MetodoPago.STRIPE))
                .map(Pago::getEstado)
                .orElse(null);
        return new EstadoReservaPublico(reserva.getCodigo(), reserva.getEstado(), estadoPago,
                reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO);
    }

    /**
     * Procesa un aviso de Stripe ya verificado. Es idempotente: el id del evento se
     * guarda en {@code stripe_eventos} en la misma transacción; si ya estaba, no se
     * hace nada. Solo interesan la sesión pagada y la vencida.
     */
    @Transactional
    public void procesarEvento(PasarelaStripe.Evento evento) {
        int nuevo = jdbc.update("INSERT INTO stripe_eventos (evento_id, tipo) VALUES (?, ?) ON CONFLICT DO NOTHING",
                evento.id(), evento.tipo());
        if (nuevo == 0 || evento.sesion() == null) {
            return;
        }
        switch (evento.tipo()) {
            case EVENTO_PAGADA -> {
                if (evento.sesion().pagada()) {
                    aprobar(evento.sesion(), Responsable.STRIPE);
                }
            }
            case EVENTO_VENCIDA -> marcarFallido(evento.sesion().id());
            default -> {
                // Otros eventos se ignoran (se responden 200).
            }
        }
    }

    /**
     * Cancela una reserva web cuyo plazo de pago venció (UC-15, RN-RES-012). Antes
     * consulta Stripe: si la sesión ya estaba pagada, la confirma en lugar de
     * cancelarla. Si no, vence la sesión para que ya no se pueda pagar.
     */
    @Transactional
    public void vencerReserva(long reservaId) {
        Reserva reserva = reservas.findById(reservaId).orElse(null);
        if (reserva == null || reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            return;
        }
        Cuenta cuenta = cuentas.findByReservaId(reserva.getId()).orElseThrow();
        Optional<Pago> pendiente = pagos.findFirstByCuentaIdAndMetodoAndEstado(cuenta.getId(), MetodoPago.STRIPE,
                EstadoPago.PENDIENTE);
        if (pendiente.isPresent()) {
            PasarelaStripe.Sesion sesion = stripe.expirarSesion(pendiente.get().getStripeSessionId());
            if (sesion.pagada()) {
                aprobar(sesion, Responsable.SISTEMA);
                return;
            }
            marcarFallido(sesion.id());
        }
        reservaService.cancelar(reserva, Responsable.SISTEMA, "Pago no completado en el plazo de 30 minutos");
    }

    private void aprobar(PasarelaStripe.Sesion sesion, Responsable responsable) {
        Optional<Pago> encontrado = pagos.bloquearPorSesionStripe(sesion.id());
        if (encontrado.isEmpty()) {
            log.warn("Sesión de Stripe {} sin pago registrado; se ignora", sesion.id());
            return;
        }
        Pago pago = encontrado.get();
        if (pago.getEstado() == EstadoPago.APROBADO) {
            return;
        }
        pago.setEstado(EstadoPago.APROBADO);
        pago.setAprobadoEn(reloj.instant());
        pago.setStripePaymentIntentId(sesion.paymentIntentId());

        Cuenta cuenta = cuentas.findById(pago.getCuentaId()).orElseThrow();
        Reserva reserva = reservas.findById(cuenta.getReservaId()).orElseThrow();
        if (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
            reservaService.confirmarPagoWeb(reserva, responsable);
        } else if (reserva.getEstado() == EstadoReserva.CANCELADA) {
            log.warn("La reserva {} ya estaba cancelada cuando Stripe aprobó el pago", reserva.getCodigo());
        }
    }

    private void marcarFallido(String sesionId) {
        pagos.bloquearPorSesionStripe(sesionId)
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
                .ifPresent(p -> p.setEstado(EstadoPago.FALLIDO));
    }
}
