package com.villaserena.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LogsPerfilesTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RegistroHttp.class)
            .withPropertyValues("spring.config.import=");

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev"})
    void desarrolloActivaDiagnosticoYRegistroHttp(String perfil) {
        contexto.withPropertyValues("spring.profiles.active=" + perfil).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(RegistroHttp.class);
            assertThat(ctx.getEnvironment().getProperty("logging.level.com.villaserena.api")).isEqualTo("DEBUG");
            assertThat(ctx.getEnvironment().getProperty("logging.level.root")).isEqualTo("INFO");
        });
    }

    @Test
    void defaultLocalTambienActivaRegistroHttp() {
        contexto.withPropertyValues("spring.profiles.active=").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(RegistroHttp.class);
            assertThat(ctx.getEnvironment().getDefaultProfiles()).containsExactly("local");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "test"})
    void otrosPerfilesNoActivanDiagnosticoDeDesarrollo(String perfil) {
        contexto.withPropertyValues("spring.profiles.active=" + perfil).run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(RegistroHttp.class);
            assertThat(ctx.getEnvironment().getProperty("logging.level.com.villaserena.api")).isNull();
        });
    }
}
