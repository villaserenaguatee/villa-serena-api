package com.villaserena.api.reservas;

import java.math.BigDecimal;

/**
 * Datos de un cargo adicional. {@code pedidoId} solo para ROOM_SERVICE (un cargo por
 * pedido) y {@code categoria} solo para SERVICIO (cargo manual de Recepción).
 */
public record NuevoCargo(TipoCargo tipo, CategoriaServicio categoria, String concepto, int cantidad,
        BigDecimal precioUnitario, Long pedidoId) {

    public static NuevoCargo roomService(long pedidoId, String concepto, BigDecimal total) {
        return new NuevoCargo(TipoCargo.ROOM_SERVICE, null, concepto, 1, total, pedidoId);
    }
}
