package com.villaserena.api.auth.admin;

public record ContrasenaRestablecida(String contrasenaTemporal) {
    @Override
    public String toString() {
        return "ContrasenaRestablecida[contrasenaTemporal=PROTEGIDA]";
    }
}
