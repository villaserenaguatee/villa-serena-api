package com.villaserena.api.roomservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.villaserena.api.comun.HistorialOperacionVista;
import com.villaserena.api.roomservice.EstadoPedido;

/**
 * Pedido como lo ve Room Service (esquema {@code PedidoRoomService}). El único dato
 * personal permitido es el nombre del huésped: nada de correo, teléfono, documento,
 * cuenta ni pagos.
 */
public record PedidoRoomService(long id, OffsetDateTime creadoEn, EstadoPedido estado, List<ItemPedido> items,
        String notas, BigDecimal total, String motivoCancelacion, HabitacionOperativa habitacion,
        String nombreHuesped, List<HistorialOperacionVista> historial) {
}
