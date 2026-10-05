package com.villaserena.api.reservas.stripe;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;

/**
 * Cada minuto cancela las reservas web que siguen PENDIENTE_PAGO después del plazo
 * (30 minutos; RN-RES-012). Cada reserva se procesa en su propia transacción: si
 * Stripe falla con una, se reintenta en la siguiente vuelta sin afectar a las demás.
 */
@Component
public class VencimientoReservasJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoReservasJob.class);

    private final ReservaRepository reservas;
    private final PagoStripeService pagos;
    private final Clock reloj;
    private final Duration plazo;

    public VencimientoReservasJob(ReservaRepository reservas, PagoStripeService pagos, Clock reloj,
            PropiedadesVillaSerena propiedades) {
        this.reservas = reservas;
        this.pagos = pagos;
        this.reloj = reloj;
        this.plazo = Duration.ofMinutes(propiedades.reservas().minutosPago());
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void cancelarVencidas() {
        for (Reserva reserva : reservas.findByEstadoAndCanalAndCreadaEnBefore(EstadoReserva.PENDIENTE_PAGO,
                CanalReserva.DIRECTO_WEB, reloj.instant().minus(plazo))) {
            try {
                pagos.vencerReserva(reserva.getId());
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la reserva {}: {}", reserva.getCodigo(), e.getMessage());
            }
        }
    }
}
