package com.villaserena.api.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.JwtConfig;

/**
 * Datos del usuario autenticado, leídos del JWT. Úsalo en los servicios para
 * registrar al responsable o verificar la propiedad (👤 del documento 09).
 */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static long id() {
        return Long.parseLong(jwt().getSubject());
    }

    public static TipoUsuario tipo() {
        return TipoUsuario.valueOf(jwt().getClaimAsString(JwtConfig.CLAIM_TIPO));
    }

    public static String rol() {
        return jwt().getClaimAsString(JwtConfig.CLAIM_ROL);
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Debes iniciar sesión para continuar.");
    }
}
