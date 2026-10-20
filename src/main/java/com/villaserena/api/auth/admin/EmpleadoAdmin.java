package com.villaserena.api.auth.admin;

import com.villaserena.api.auth.AreaMyl;
import com.villaserena.api.auth.Empleado;
import com.villaserena.api.auth.EstadoEmpleado;
import com.villaserena.api.auth.RolEmpleado;

public record EmpleadoAdmin(Long id, String nombreCompleto, String correo, String telefono,
        RolEmpleado rol, AreaMyl area, EstadoEmpleado estado) {

    public static EmpleadoAdmin de(Empleado empleado) {
        return new EmpleadoAdmin(empleado.getId(), empleado.getNombreCompleto(), empleado.getCorreo(),
                empleado.getTelefono(), empleado.getRol(), empleado.getArea(), empleado.getEstado());
    }
}
