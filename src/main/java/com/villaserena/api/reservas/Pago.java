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

/** Pago de una cuenta (tabla {@code payments}). Los pagos en línea guardan el checkout y el identificador del cobro. */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoPago estado;

    @Column(name = "amount", nullable = false)
    private BigDecimal monto;

    @Column(name = "reference")
    private String referencia;

    @Column(name = "checkout_id")
    private String checkoutId;

    @Column(name = "external_payment_id")
    private String pagoExternoId;

    @Column(name = "recorded_by_employee_id")
    private Long registradoPorEmpleadoId;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "approved_at")
    private Instant aprobadoEn;

    @Column(name = "refunded_at")
    private Instant reembolsadoEn;
}
