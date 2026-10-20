package com.villaserena.api.auth.admin;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.villaserena.api.auth.AreaMyl;
import com.villaserena.api.auth.RolEmpleado;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class EditarEmpleadoPeticion {
    @Size(min = 1, max = 150) @Pattern(regexp = "(?s).*\\S.*")
    private String nombreCompleto;
    @Size(min = 1, max = 30) @Pattern(regexp = "(?s).*\\S.*")
    private String telefono;
    private RolEmpleado rol;
    private AreaMyl area;
    @Getter(lombok.AccessLevel.NONE)
    private boolean areaInformada;

    public boolean areaInformada() { return areaInformada; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setNombreCompleto(String valor) { nombreCompleto = valor; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTelefono(String valor) { telefono = valor; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRol(RolEmpleado valor) { rol = valor; }

    public void setArea(AreaMyl valor) { area = valor; areaInformada = true; }

    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
