package com.villaserena.api.auth.dto;

import com.villaserena.api.auth.AreaMyl;
import com.villaserena.api.auth.Empleado;
import com.villaserena.api.auth.RolEmpleado;

/** Datos básicos del empleado autenticado (esquema {@code EmpleadoSesion}). */
public record EmpleadoSesion(Long id, String nombre, String correo, RolEmpleado rol, AreaMyl area,
        boolean debeCambiarContrasena) {

    public static EmpleadoSesion de(Empleado e) {
        return new EmpleadoSesion(e.getId(), e.getNombreCompleto(), e.getCorreo(), e.getRol(), e.getArea(),
                e.isDebeCambiarContrasena());
    }
}
