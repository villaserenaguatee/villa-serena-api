package com.villaserena.api.reservas;

import java.math.BigDecimal;
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

/** Cargo de una cuenta (tabla {@code charges}). Nunca se borra; se anula con motivo. */
@Entity
@Table(name = "charges")
@Getter
@Setter
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TipoCargo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_category")
    private CategoriaServicio categoriaServicio;

    @Column(name = "concept", nullable = false)
    private String concepto;

    @Column(name = "quantity", nullable = false)
    private int cantidad;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoCargo estado = EstadoCargo.VIGENTE;

    @Column(name = "order_id")
    private Long pedidoId;

    @Column(name = "created_by_employee_id")
    private Long creadoPorEmpleadoId;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "void_reason")
    private String motivoAnulacion;

    @Column(name = "voided_by_employee_id")
    private Long anuladoPorEmpleadoId;

    @Column(name = "voided_at")
    private Instant anuladoEn;
}
