package com.villaserena.api.estadia;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.villaserena.api.reservas.pagos.PagoOnlineService;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

/**
 * Pago del saldo desde la app (HU-HUE-16): solo EN_ESTADIA, de 00:00 a 12:00 del día
 * de salida y sin pedido en camino. Crea una sesión de Recurrente Checkout por el saldo
 * completo; el webhook existente (sesión pagada) registra el pago APROBADO.
 */
@Service
public class PagoSaldoAppService {

    private static final Duration DURACION_SESION = Duration.ofMinutes(30);

    private final CheckoutService checkout;
    private final CuentaRepository cuentas;
    private final CuentaService cuentaService;
    private final PagoRepository pagos;
    private final PasarelaPagos pasarela;
    private final Clock reloj;
    private final String urlRetorno;
    private final JdbcTemplate jdbc;

    public PagoSaldoAppService(CheckoutService checkout, CuentaRepository cuentas, CuentaService cuentaService,
            PagoRepository pagos, PasarelaPagos pasarela, Clock reloj, PropiedadesVillaSerena propiedades, JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        this.checkout = checkout;
        this.cuentas = cuentas;
        this.cuentaService = cuentaService;
        this.pagos = pagos;
        this.pasarela = pasarela;
        this.reloj = reloj;
        this.urlRetorno = propiedades.recurrente().urlRetornoApp();
    }

    /** @return la sesión de pago, o vacío si el saldo ya es 0 (no se crea sesión). */
    @Transactional
    public Optional<PagoIniciado> iniciar(Reserva reserva) {
        String estado=jdbc.queryForObject("SELECT estado FROM reservas WHERE id=? FOR UPDATE",String.class,reserva.getId());
        if (!"EN_ESTADIA".equals(estado)) {
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
        var pendiente=pagos.findFirstByCuentaIdAndMetodoAndEstado(cuenta.getId(),MetodoPago.RECURRENTE,EstadoPago.PENDIENTE);
        if(pendiente.isPresent()){
            var actual=pasarela.obtenerSesion(pendiente.get().getCheckoutId());
            if(actual.abierta())return Optional.of(new PagoIniciado(actual.url(),actual.expiraEn()==null?null:OffsetDateTime.ofInstant(actual.expiraEn(),reloj.getZone())));
            if(actual.pagada() || "payment_in_progress".equals(actual.estado()))throw ApiException.conflicto("PAGO_EN_VERIFICACION","El pago se está verificando. Actualiza la cuenta antes de intentarlo de nuevo.");
            pendiente.get().setEstado(EstadoPago.FALLIDO);
            pagos.saveAndFlush(pendiente.get());
        }
        PasarelaPagos.Sesion sesion = pasarela.crearSesion(
                "Saldo de la reserva " + reserva.getCodigo() + " — Hotel Villa Serena", saldo,
                urlRetorno + "?codigo=" + reserva.getCodigo(), reloj.instant().plus(DURACION_SESION),
                Map.of(PagoOnlineService.META_CODIGO, reserva.getCodigo(),
                        PagoOnlineService.META_TIPO, PagoOnlineService.TIPO_SALDO));
        Pago pago = new Pago();
        pago.setCuentaId(cuenta.getId());
        pago.setMetodo(MetodoPago.RECURRENTE);
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setMonto(saldo);
        pago.setCheckoutId(sesion.id());
        pago.setCreadoEn(reloj.instant());
        pagos.save(pago);
        OffsetDateTime expira = sesion.expiraEn() == null ? null : OffsetDateTime.ofInstant(sesion.expiraEn(),
                reloj.getZone());
        return Optional.of(new PagoIniciado(sesion.url(), expira));
    }
}
