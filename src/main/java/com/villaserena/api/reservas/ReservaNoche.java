package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Precio fijo de cada noche (tabla {@code reserva_noches}); las reservas de canal no tienen filas. */
@Entity
@Table(name = "reserva_noches")
@Getter
@Setter
public class ReservaNoche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(name = "temporada_nombre")
    private String temporadaNombre;

    @Column(name = "fin_de_semana", nullable = false)
    private boolean finDeSemana;
}
