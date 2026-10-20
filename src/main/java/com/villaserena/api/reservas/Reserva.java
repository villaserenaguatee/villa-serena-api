package com.villaserena.api.reservas;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/** Reserva (tabla {@code reservations}, V2). El EXCLUDE de la base impide traslapes por habitación. */
@Entity
@Table(name = "reservations")
@Getter
@Setter
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private String codigo;

    @Column(name = "guest_id", nullable = false)
    private Long huespedId;

    @Column(name = "room_type_id", nullable = false)
    private Long tipoHabitacionId;

    @Column(name = "room_id")
    private Long habitacionId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate fechaEntrada;

    @Column(name = "checkout_date", nullable = false)
    private LocalDate fechaSalida;

    @Column(name = "estimated_arrival_time")
    // JDBC 4.2 conserva la hora local sin aplicar hibernate.jdbc.time_zone=UTC.
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    private LocalTime horaEstimadaLlegada;

    @Column(name = "guest_count", nullable = false)
    private int numeroHuespedes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoReserva estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private CanalReserva canal;

    @Column(name = "external_identifier")
    private String identificadorExterno;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "created_by_employee_id")
    private Long creadaPorEmpleadoId;

    @Column(name = "created_at", nullable = false)
    private Instant creadaEn;

    @Column(name = "room_assigned_at")
    private Instant habitacionAsignadaEn;

    @Column(name = "room_assigned_by_employee_id")
    private Long habitacionAsignadaPorEmpleadoId;

    public int noches() {
        return (int) ChronoUnit.DAYS.between(fechaEntrada, fechaSalida);
    }
}
