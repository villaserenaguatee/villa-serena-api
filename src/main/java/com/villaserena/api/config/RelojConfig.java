package com.villaserena.api.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj único del API en America/Guatemala (AD-19). Los servicios lo inyectan para
 * calcular "hoy", la regla de 48 h, el check-in y el check-out; en las pruebas se
 * reemplaza por un reloj fijo.
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj(PropiedadesVillaSerena propiedades) {
        return Clock.system(ZoneId.of(propiedades.zonaHoraria()));
    }
}
