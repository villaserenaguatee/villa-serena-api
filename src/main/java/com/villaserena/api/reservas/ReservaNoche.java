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

/** Precio fijo de cada noche (tabla {@code reservation_nights}); las reservas de canal no tienen filas. */
@Entity
@Table(name = "reservation_nights")
@Getter
@Setter
public class ReservaNoche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservaId;

    @Column(name = "date", nullable = false)
    private LocalDate fecha;

    @Column(name = "price", nullable = false)
    private BigDecimal precio;

    @Column(name = "season_name")
    private String temporadaNombre;

    @Column(name = "is_weekend", nullable = false)
    private boolean finDeSemana;
}
