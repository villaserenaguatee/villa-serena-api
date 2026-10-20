package com.villaserena.api.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Registra también los rechazos que ocurren antes de llegar a un controlador. */
@Component
@Profile({"local", "dev"})
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RegistroHttp extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RegistroHttp.class);

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        boolean completada = false;
        try {
            cadena.doFilter(peticion, respuesta);
            completada = true;
        } finally {
            long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            log.info("HTTP {} {} -> {} ({} ms)", peticion.getMethod(),
                    peticion.getRequestURI().replace('\r', '_').replace('\n', '_'),
                    completada ? respuesta.getStatus() : 500, duracion);
        }
    }
}
