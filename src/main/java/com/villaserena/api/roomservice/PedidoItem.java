package com.villaserena.api.roomservice;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Línea de un pedido (tabla {@code order_items}) con el precio congelado al
 * momento de pedir (RN-RS-006): si el menú cambia después, el pedido no cambia.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
public class PedidoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long pedidoId;

    @Column(name = "menu_item_id", nullable = false)
    private Long itemMenuId;

    @Column(name = "quantity", nullable = false)
    private int cantidad;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal precioUnitario;
}
