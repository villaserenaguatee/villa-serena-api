package com.villaserena.api.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import com.villaserena.api.comun.ApiException;

class ContactoCorreoConfigTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sin-correo", "uno@example.test,dos@example.test",
            "Equipo <operacion@example.test>", "grupo:uno@example.test;",
            "operacion@example.test\r\nBcc:otro@example.test", "operacion@example.test\n"})
    void invalidoDeshabilitaSinRevelarElValor(String valor) {
        ApiException error = assertThrows(ApiException.class,
                () -> new ContactoCorreoConfig(valor).exigirDestinatario());
        assertThat(error.getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getCodigo()).isEqualTo("CONTACTO_NO_DISPONIBLE");
        assertThat(error.getMessage()).isEqualTo("El envío de mensajes no está disponible temporalmente.");
    }

    @Test
    void aceptaSoloBuzonExplicitamenteConfigurado() {
        assertThat(new ContactoCorreoConfig("  operacion@example.test  ").exigirDestinatario())
                .isEqualTo("operacion@example.test");
        assertThrows(ApiException.class,
                () -> new ContactoCorreoConfig("a".repeat(255) + "@example.test").exigirDestinatario());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "prod", "test"})
    void ausenciaPermiteCrearElComponenteEnCadaEntorno(String perfil) {
        new ApplicationContextRunner().withUserConfiguration(ContactoCorreoConfig.class)
                .withPropertyValues("spring.profiles.active=" + perfil)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThrows(ApiException.class,
                            () -> context.getBean(ContactoCorreoConfig.class).exigirDestinatario());
                });
    }
}
