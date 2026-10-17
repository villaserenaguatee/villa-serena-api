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

/** Pago de una cuenta (tabla {@code pagos}). Los de Stripe guardan su sesión y su payment intent. */
@Entity
@Table(name = "pagos")
@Getter
@Setter
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Column(nullable = false)
    private BigDecimal monto;

    private String referencia;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "registrado_por_empleado_id")
    private Long registradoPorEmpleadoId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    @Column(name = "reembolsado_en")
    private Instant reembolsadoEn;
}
