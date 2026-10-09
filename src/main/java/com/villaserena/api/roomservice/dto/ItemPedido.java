package com.villaserena.api.roomservice.dto;

import java.math.BigDecimal;

/** Línea de un pedido con su subtotal (esquema {@code ItemPedido}). */
public record ItemPedido(long itemId, String nombre, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
}
