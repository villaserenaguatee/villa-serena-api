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

/** Huésped principal (tabla {@code huespedes}, V1). Se identifica por su correo (RN-RES-019). */
@Entity
@Table(name = "huespedes")
@Getter
@Setter
public class Huesped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    /** Intentos fallidos seguidos del código de acceso (PAR-15). */
    @Column(name = "otp_intentos_fallidos", nullable = false)
    private int otpIntentosFallidos;

    @Column(name = "otp_bloqueado_hasta")
    private Instant otpBloqueadoHasta;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    public boolean otpBloqueado(Instant ahora) {
        return otpBloqueadoHasta != null && otpBloqueadoHasta.isAfter(ahora);
    }
}
