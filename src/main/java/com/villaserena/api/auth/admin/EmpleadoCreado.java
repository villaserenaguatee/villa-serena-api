package com.villaserena.api.auth.admin;

public record EmpleadoCreado(EmpleadoAdmin empleado, String contrasenaTemporal) {
    @Override
    public String toString() {
        return "EmpleadoCreado[contrasenaTemporal=PROTEGIDA]";
    }
}
