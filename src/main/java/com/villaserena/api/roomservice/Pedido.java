package com.villaserena.api.roomservice;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de Room Service (tabla {@code orders}). El "Pedido #n" del cargo es el
 * id. El motivo de cancelación no se guarda aquí: vive en {@code historial_estados}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoPedido estado = EstadoPedido.NUEVO;

    @Column(name = "notes")
    private String notas;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;
}
