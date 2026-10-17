package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

/** Reserva (tabla {@code reservas}, V2). El EXCLUDE de la base impide traslapes por habitación. */
@Entity
@Table(name = "reservas")
@Getter
@Setter
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String codigo;

    @Column(name = "huesped_id", nullable = false)
    private Long huespedId;

    @Column(name = "tipo_habitacion_id", nullable = false)
    private Long tipoHabitacionId;

    @Column(name = "habitacion_id")
    private Long habitacionId;

    @Column(name = "fecha_entrada", nullable = false)
    private LocalDate fechaEntrada;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDate fechaSalida;

    @Column(name = "numero_huespedes", nullable = false)
    private int numeroHuespedes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalReserva canal;

    @Column(name = "identificador_externo")
    private String identificadorExterno;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "creada_por_empleado_id")
    private Long creadaPorEmpleadoId;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "habitacion_asignada_en")
    private Instant habitacionAsignadaEn;

    @Column(name = "habitacion_asignada_por_empleado_id")
    private Long habitacionAsignadaPorEmpleadoId;

    public int noches() {
        return (int) ChronoUnit.DAYS.between(fechaEntrada, fechaSalida);
    }
}
