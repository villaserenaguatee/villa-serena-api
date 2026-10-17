package com.villaserena.api.reservas;

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

/** Cuenta de la reserva (tabla {@code cuentas}); se crea junto con ella (RN-PAG-009). */
@Entity
@Table(name = "cuentas")
@Getter
@Setter
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCuenta estado = EstadoCuenta.ABIERTA;

    @Column(name = "abierta_en", nullable = false)
    private Instant abiertaEn;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    public boolean estaAbierta() {
        return estado == EstadoCuenta.ABIERTA;
    }
}
