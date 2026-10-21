package com.villaserena.api.app;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Teléfono registrado para recibir push (tabla {@code push_devices}). */
@Entity
@Table(name = "push_devices")
@Getter
@Setter
public class DispositivoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false)
    private Long huespedId;

    @Column(name = "expo_token", nullable = false)
    private String tokenExpo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant creadoEn;
}
