package com.villaserena.api.auth.admin;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.villaserena.api.auth.EstadoEmpleado;
import jakarta.validation.constraints.NotNull;

public record EstadoEmpleadoPeticion(@NotNull EstadoEmpleado estado) {
    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
