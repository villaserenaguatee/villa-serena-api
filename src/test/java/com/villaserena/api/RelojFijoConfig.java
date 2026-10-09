package com.villaserena.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj fijo para las pruebas: lunes 5 de octubre de 2026, 12:00 en America/Guatemala. */
@TestConfiguration(proxyBeanMethods = false)
public class RelojFijoConfig {

    public static final ZoneId ZONA = ZoneId.of("America/Guatemala");
    public static final Instant AHORA = Instant.parse("2026-10-05T18:00:00Z");

    @Bean
    @Primary
    Clock relojFijo() {
        return Clock.fixed(AHORA, ZONA);
    }
}
