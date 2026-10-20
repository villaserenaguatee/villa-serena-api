package com.villaserena.api.huespedes;

import java.time.Instant;

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

/** Huésped principal (tabla {@code guests}, V1). Se identifica por su correo (RN-RES-019). */
@Entity
@Table(name = "guests")
@Getter
@Setter
public class Huesped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String nombreCompleto;

    @Column(name = "email", nullable = false)
    private String correo;

    @Column(name = "phone", nullable = false)
    private String telefono;

    @Column(name = "nationality", length = 60)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "document_number", nullable = false)
    private String numeroDocumento;

    @Column(name = "passport_issuing_country")
    private String paisEmisorPasaporte;

    /** Intentos fallidos seguidos del código de acceso (PAR-15). */
    @Column(name = "otp_failed_attempts", nullable = false)
    private int otpIntentosFallidos;

    @Column(name = "otp_locked_until")
    private Instant otpBloqueadoHasta;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant creadoEn;

    public boolean otpBloqueado(Instant ahora) {
        return otpBloqueadoHasta != null && otpBloqueadoHasta.isAfter(ahora);
    }
}
