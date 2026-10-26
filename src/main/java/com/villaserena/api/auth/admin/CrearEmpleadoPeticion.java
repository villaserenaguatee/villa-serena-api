package com.villaserena.api.auth.admin;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.villaserena.api.auth.AreaMyl;
import com.villaserena.api.auth.RolEmpleado;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearEmpleadoPeticion(
        @NotBlank @Size(max = 150) String nombreCompleto,
        @NotBlank @Email @Size(max = 150) String correo,
        @NotBlank @Size(max = 30) String telefono,
        @NotNull RolEmpleado rol, AreaMyl area,
        @Email @Size(min = 1, max = 150) @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+") String correoPersonal) {

    public CrearEmpleadoPeticion(String nombreCompleto, String correo, String telefono, RolEmpleado rol, AreaMyl area) {
        this(nombreCompleto, correo, telefono, rol, area, null);
    }

    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
