package com.villaserena.api.reservas.pagos;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.Pago;
import com.villaserena.api.reservas.dto.ProcesoPago;
import com.villaserena.api.reservas.dto.ProcesoPago.AccionPago;
import com.villaserena.api.reservas.dto.ProcesoPago.EstadoCheckout;

/** Lee la pasarela sin confirmar pagos ni modificar reservas. */
@Service
public class ConsultaProcesoPago {
    private final PasarelaPagos pasarela;
    private final Clock reloj;

    public ConsultaProcesoPago(PasarelaPagos pasarela, Clock reloj) {
        this.pasarela = pasarela;
        this.reloj = reloj;
    }

    public ProcesoPago consultar(Optional<Pago> ultimo, boolean habilitado, Instant limite) {
        boolean vigente = habilitado && reloj.instant().isBefore(limite);
        if (ultimo.isEmpty()) {
            return respuesta(null, EstadoCheckout.SIN_CHECKOUT,
                    vigente ? AccionPago.INICIAR : AccionPago.NINGUNA, null, null);
        }
        Pago pago = ultimo.get();
        if (pago.getEstado() != EstadoPago.PENDIENTE) {
            EstadoCheckout estado = pago.getEstado() == EstadoPago.APROBADO
                    || pago.getEstado() == EstadoPago.REEMBOLSADO ? EstadoCheckout.PAGADO : EstadoCheckout.VENCIDO;
            return respuesta(pago.getEstado(), estado,
                    vigente && pago.getEstado() == EstadoPago.FALLIDO ? AccionPago.REINTENTAR : AccionPago.NINGUNA,
                    null, null);
        }
        final PasarelaPagos.Sesion sesion;
        try {
            sesion = pasarela.obtenerSesion(pago.getCheckoutId());
        } catch (ErrorPasarela e) {
            return respuesta(pago.getEstado(), EstadoCheckout.NO_VERIFICADO,
                    habilitado ? AccionPago.ESPERAR : AccionPago.NINGUNA, null, null);
        }
        if (sesion.pagada()) {
            return respuesta(pago.getEstado(), EstadoCheckout.PAGADO,
                    habilitado ? AccionPago.ESPERAR : AccionPago.NINGUNA, null, null);
        }
        if ("payment_in_progress".equals(sesion.estado())) {
            return respuesta(pago.getEstado(), EstadoCheckout.PROCESANDO,
                    habilitado ? AccionPago.ESPERAR : AccionPago.NINGUNA, null, null);
        }
        Instant expira = sesion.expiraEn() == null || limite.isBefore(sesion.expiraEn()) ? limite : sesion.expiraEn();
        if (sesion.abierta() && (sesion.expiraEn() == null || reloj.instant().isBefore(sesion.expiraEn()))) {
            // Sin enlace no se puede prometer al cliente que podrá continuar.
            boolean continuar = vigente && sesion.url() != null && !sesion.url().isBlank();
            return respuesta(pago.getEstado(), EstadoCheckout.ABIERTO,
                    continuar ? AccionPago.CONTINUAR : vigente ? AccionPago.ESPERAR : AccionPago.NINGUNA,
                    continuar ? sesion.url() : null, expira);
        }
        if ("expired".equals(sesion.estado())) {
            return respuesta(pago.getEstado(), EstadoCheckout.VENCIDO,
                    vigente ? AccionPago.REINTENTAR : AccionPago.NINGUNA, null, expira);
        }
        // Unpaid con fecha vencida aún requiere que la pasarela confirme el vencimiento.
        return respuesta(pago.getEstado(), EstadoCheckout.NO_VERIFICADO,
                habilitado ? AccionPago.ESPERAR : AccionPago.NINGUNA, null, expira);
    }

    private ProcesoPago respuesta(EstadoPago pago, EstadoCheckout estado, AccionPago accion, String url, Instant expira) {
        return new ProcesoPago(pago, estado, accion, accion == AccionPago.REINTENTAR, url,
                expira == null ? null : OffsetDateTime.ofInstant(expira, reloj.getZone()));
    }
}
