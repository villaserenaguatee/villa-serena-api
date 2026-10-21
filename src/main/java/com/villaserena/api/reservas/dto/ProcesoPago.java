package com.villaserena.api.reservas.dto;

import java.time.OffsetDateTime;
import com.villaserena.api.reservas.EstadoPago;

/** Estado observado; el POST siempre vuelve a validar la acción antes de cobrar. */
public record ProcesoPago(EstadoPago estadoPago, EstadoCheckout estadoCheckout, AccionPago accionPago,
        boolean puedeReintentar, String urlPago, OffsetDateTime expiraEn) {
    public enum EstadoCheckout { SIN_CHECKOUT, ABIERTO, PROCESANDO, PAGADO, VENCIDO, NO_VERIFICADO }
    public enum AccionPago { INICIAR, CONTINUAR, ESPERAR, REINTENTAR, NINGUNA }
}
