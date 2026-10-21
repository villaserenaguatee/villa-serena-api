package com.villaserena.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EntornoConfigTest {
    @Test
    void aceptaCadaEntornoYElDefaultLocal() {
        var env = new MockEnvironment();
        env.setDefaultProfiles("local");
        assertThatCode(() -> new EntornoConfig(env)).doesNotThrowAnyException();
        for (String perfil : new String[]{"local", "dev", "test"}) {
            env.setActiveProfiles(perfil);
            assertThatCode(() -> new EntornoConfig(env)).doesNotThrowAnyException();
        }
    }

    @Test
    void rechazaPerfilesObsoletosDesconocidosYCombinados() {
        var env = new MockEnvironment();
        for (String perfil : new String[]{"demo", "staging", "default", "prdo"}) {
            env.setActiveProfiles(perfil);
            assertThatThrownBy(() -> new EntornoConfig(env)).isInstanceOf(IllegalStateException.class);
        }
        env.setActiveProfiles("local", "prod");
        assertThatThrownBy(() -> new EntornoConfig(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void produccionExigeOrigenesExplicitosHttps() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new EntornoConfig(env)).isInstanceOf(IllegalStateException.class);
        env.withProperty("WEB_URL", "https://hotel.example")
                .withProperty("CORS_ORIGENES", "https://hotel.example,https://admin.example");
        assertThatCode(() -> new EntornoConfig(env)).doesNotThrowAnyException();
        for (String origen : new String[]{"", "*", "http://localhost:3000", "https://hotel.example/ruta"}) {
            env.setProperty("CORS_ORIGENES", origen);
            assertThatThrownBy(() -> new EntornoConfig(env)).isInstanceOf(IllegalStateException.class);
        }
    }
}
