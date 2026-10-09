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

/** Teléfono registrado para recibir push (tabla {@code dispositivos_push}). */
@Entity
@Table(name = "dispositivos_push")
@Getter
@Setter
public class DispositivoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "huesped_id", nullable = false)
    private Long huespedId;

    @Column(name = "token_expo", nullable = false)
    private String tokenExpo;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;
}
