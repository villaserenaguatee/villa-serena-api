package com.villaserena.api.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mientras el empleado tenga la contraseña temporal ({@code debeCambiarContrasena}
 * en su JWT), solo puede cambiarla, cerrar sesión o consultar /yo. Todo lo demás
 * responde 403 {@code CONTRASENA_TEMPORAL} (documento 14, sección 6.1, punto 7).
 */
public class FiltroContrasenaTemporal extends OncePerRequestFilter {

    private static final Set<String> RUTAS_PERMITIDAS = Set.of(
            "/api/v1/auth/cambiar-contrasena",
            "/api/v1/auth/cerrar-sesion",
            "/api/v1/auth/yo");

    private final RespuestaErrorSeguridad respuestaError;

    public FiltroContrasenaTemporal(RespuestaErrorSeguridad respuestaError) {
        this.respuestaError = respuestaError;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(JwtConfig.CLAIM_DEBE_CAMBIAR))
                && !RUTAS_PERMITIDAS.contains(peticion.getRequestURI())) {
            respuestaError.escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, "CONTRASENA_TEMPORAL",
                    "Debes cambiar tu contraseña temporal antes de continuar.");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }
}
