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

/**
 * Código de acceso del huésped (tabla {@code codigos_otp}). Solo se guarda su hash
 * (PAR-14): si alguien leyera la base, no podría entrar con lo que ve. Vence en 10
 * minutos y sirve una sola vez.
 */
@Entity
@Table(name = "codigos_otp")
@Getter
@Setter
public class CodigoOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "huesped_id", nullable = false)
    private Long huespedId;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    public boolean sirve(Instant ahora) {
        return usadoEn == null && expiraEn.isAfter(ahora);
    }
}
