package com.villaserena.api.tiemporeal;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Quién puede suscribirse a qué (documento 14, sección 6, y {@code x-websocket} del
 * contrato). Un rol sin permiso no se suscribe, y los destinos que no están en esta
 * lista se rechazan: así nadie puede inventar un destino para espiar otro.
 */
@Component
public class AutorizacionSuscripciones {

    /** Áreas de Mantenimiento y Limpieza que ven limpieza. */
    private static final Set<String> AREAS_LIMPIEZA = Set.of("AREA_LIMPIEZA", "AREA_AMBAS");

    /**
     * @param destino el destino que pide el cliente en el SUBSCRIBE
     * @return si ese usuario puede escuchar ahí
     */
    public boolean puedeSuscribirse(Authentication usuario, String destino) {
        if (usuario == null || destino == null) {
            return false;
        }
        List<String> autoridades = usuario.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return switch (destino) {
            case Destinos.PEDIDOS -> autoridades.contains("ROLE_ROOM_SERVICE");
            // La cola privada la resuelve Spring con el Principal del token: el
            // cliente no elige de quién son los pedidos que recibe.
            case "/user" + Destinos.PEDIDOS_HUESPED, Destinos.PEDIDOS_HUESPED ->
                    autoridades.contains("ROLE_HUESPED");
            case Destinos.SOLICITUDES -> esLimpieza(autoridades);
            case Destinos.HABITACIONES -> autoridades.contains("ROLE_RECEPCION") || esLimpieza(autoridades);
            default -> false;
        };
    }

    private static boolean esLimpieza(List<String> autoridades) {
        return autoridades.contains("ROLE_MANTENIMIENTO_LIMPIEZA")
                && autoridades.stream().anyMatch(AREAS_LIMPIEZA::contains);
    }
}
