package com.villaserena.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.DeserializationFeature;

/** Reject fractions for integer DTO fields instead of silently truncating them. */
@Configuration(proxyBeanMethods = false)
public class JsonNumbersConfiguration {
    @Bean
    public JsonMapperBuilderCustomizer strictJsonIntegers() {
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
