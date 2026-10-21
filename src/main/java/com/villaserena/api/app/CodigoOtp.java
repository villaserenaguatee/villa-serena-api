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
 * Código de acceso del huésped (tabla {@code otp_codes}). Solo se guarda su hash
 * (PAR-14): si alguien leyera la base, no podría entrar con lo que ve. Vence en 10
 * minutos y sirve una sola vez.
 */
@Entity
@Table(name = "otp_codes")
@Getter
@Setter
public class CodigoOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false)
    private Long huespedId;

    @Column(name = "code_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiraEn;

    @Column(name = "used_at")
    private Instant usadoEn;

    /** Con el reloj del API, como {@code expiraEn}: el límite de reenvío los compara con él. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    public boolean sirve(Instant ahora) {
        return usadoEn == null && expiraEn.isAfter(ahora);
    }
}
