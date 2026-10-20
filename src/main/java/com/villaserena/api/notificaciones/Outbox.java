package com.villaserena.api.notificaciones;

import java.time.Instant;

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

/**
 * Aviso por enviar (tabla {@code outbox}). Se guarda en la misma transacción del
 * cambio de estado que lo origina, así que el envío nunca deshace una reserva
 * (RN-NOT-003). {@code payload} es un JSON con los datos que necesita la plantilla.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TipoNotificacion tipo;

    @Column(name = "recipient", nullable = false)
    private String destinatario;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(name = "attempts", nullable = false)
    private int intentos;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant proximoIntentoEn;

    @Column(name = "last_error")
    private String ultimoError;

    @Column(name = "created_at", nullable = false)
    private Instant creadoEn;

    @Column(name = "sent_at")
    private Instant enviadoEn;
}
