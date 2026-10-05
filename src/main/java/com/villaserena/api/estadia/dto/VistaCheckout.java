package com.villaserena.api.estadia.dto;

import java.math.BigDecimal;
import java.util.List;

/** Condiciones del check-out, sin efectos (esquema {@code VistaCheckout}). */
public record VistaCheckout(String codigoReserva, BigDecimal saldo, String nombreCompradorSugerido,
        boolean pedidoEnCamino, List<Long> pedidosACancelar, boolean puedeConfirmar, List<String> motivosBloqueo) {
}
