package com.villaserena.api.canal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Canal externo autorizado (tabla {@code canales}): BOOKING o EXPEDIA. La clave
 * nunca se guarda en claro, solo su SHA-256 (RN-CM-001). Los hashes los pone
 * Josué en V6 desde el {@code .env}, así que las claves no están en Git.
 */
@Entity
@Table(name = "canales")
@Getter
public class Canal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "clave_hash", nullable = false)
    private String claveHash;
}
