package com.villaserena.api.config;

import java.net.URI;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Evita arrancar con perfiles retirados o mezclar configuraciones de entornos. */
@Component
public class EntornoConfig {
    public EntornoConfig(Environment entorno) {
        String[] activos = entorno.getActiveProfiles();
        String[] perfiles = activos.length == 0 ? entorno.getDefaultProfiles() : activos;
        if (perfiles.length != 1 || !Set.of("local", "dev", "prod", "test").contains(perfiles[0])) {
            throw new IllegalStateException("Selecciona un único perfil: local, dev o prod (test solo para pruebas).");
        }
        if ("prod".equals(perfiles[0])) {
            validarOrigen(entorno.getRequiredProperty("WEB_URL"));
            for (String origen : entorno.getRequiredProperty("CORS_ORIGENES").split(",", -1)) {
                validarOrigen(origen.trim());
            }
        }
    }

    private static void validarOrigen(String valor) {
        URI uri = URI.create(valor);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalStateException("WEB_URL y CORS_ORIGENES requieren orígenes HTTPS válidos en producción.");
        }
    }
}
