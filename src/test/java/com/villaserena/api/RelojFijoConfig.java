package com.villaserena.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj de las pruebas: arranca el lunes 5 de octubre de 2026 a las 12:00 en
 * America/Guatemala y se puede mover con {@link RelojPrueba#fijar(Instant)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelojFijoConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Guatemala");
    public static final Instant AHORA = Instant.parse("2026-10-05T18:00:00Z");

    @Bean
    @Primary
    RelojPrueba relojPrueba() {
        return new RelojPrueba();
    }

    public static class RelojPrueba extends Clock {

        private volatile Instant ahora = AHORA;

        public void fijar(Instant instante) {
            this.ahora = instante;
        }

        public void reiniciar() {
            this.ahora = AHORA;
        }

        @Override
        public ZoneId getZone() {
            return ZONA;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(ahora, zona);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
