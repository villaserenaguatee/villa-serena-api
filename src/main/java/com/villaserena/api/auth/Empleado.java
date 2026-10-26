package com.villaserena.api.auth;

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

/** Empleado del hotel (tabla {@code employees}, migración V1). */
@Entity
@Table(name = "employees")
@Getter
@Setter
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String nombreCompleto;

    @Column(name = "email", nullable = false)
    private String correo;

    @Column(name = "personal_email", length = 150)
    private String correoPersonal;

    @Column(name = "phone", nullable = false)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private RolEmpleado rol;

    @Enumerated(EnumType.STRING)
    private AreaMyl area;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EstadoEmpleado estado = EstadoEmpleado.ACTIVO;

    @Column(name = "password_hash", nullable = false)
    private String contrasenaHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean debeCambiarContrasena = true;

    @Column(name = "failed_attempts", nullable = false)
    private int intentosFallidos;

    @Column(name = "locked_until")
    private Instant bloqueadoHasta;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant creadoEn;

    public boolean estaActivo() {
        return estado == EstadoEmpleado.ACTIVO;
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }
}
