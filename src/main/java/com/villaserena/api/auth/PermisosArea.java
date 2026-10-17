package com.villaserena.api.auth;

import org.springframework.stereotype.Component;

/**
 * Permisos por área de Mantenimiento/Limpieza (documento 09 §3.6), para usar en
 * {@code @PreAuthorize} como {@code @areas.limpieza()}. AMBAS cumple las dos áreas.
 */
@Component("areas")
public class PermisosArea {

    public boolean limpieza() {
        return tiene(AreaMyl.LIMPIEZA);
    }

    public boolean mantenimiento() {
        return tiene(AreaMyl.MANTENIMIENTO);
    }

    private static boolean tiene(AreaMyl area) {
        return UsuarioActual.area().map(a -> a == area || a == AreaMyl.AMBAS).orElse(false);
    }
}
