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

/** Cargo de una cuenta (tabla {@code cargos}). Nunca se borra; se anula con motivo. */
@Entity
@Table(name = "cargos")
@Getter
@Setter
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCargo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_servicio")
    private CategoriaServicio categoriaServicio;

    @Column(nullable = false)
    private String concepto;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCargo estado = EstadoCargo.VIGENTE;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "creado_por_empleado_id")
    private Long creadoPorEmpleadoId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    @Column(name = "anulado_por_empleado_id")
    private Long anuladoPorEmpleadoId;

    @Column(name = "anulado_en")
    private Instant anuladoEn;
}
