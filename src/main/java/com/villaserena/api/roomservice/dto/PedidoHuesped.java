package com.villaserena.api.roomservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.villaserena.api.roomservice.EstadoPedido;

/** Pedido como lo ve su dueño en la app (esquema {@code PedidoHuesped}). */
public record PedidoHuesped(long id, String codigoReserva, OffsetDateTime creadoEn, EstadoPedido estado,
        List<ItemPedido> items, String notas, BigDecimal total, String motivoCancelacion) {
}
