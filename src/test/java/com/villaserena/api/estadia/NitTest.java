package com.villaserena.api.estadia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NitTest {

    @Test
    void aceptaNitsValidosYConsumidorFinal() {
        assertThat(Nit.esValido("4817205-7")).isTrue(); // NIT del hotel en los datos iniciales
        assertThat(Nit.esValido("48172057")).isTrue();
        assertThat(Nit.esValido("6-K")).isTrue();
        assertThat(Nit.esValido("6k")).isTrue();
        assertThat(Nit.esValido("CF")).isTrue();
        assertThat(Nit.esValido(" cf ")).isTrue();
    }

    @Test
    void rechazaNitsInvalidos() {
        assertThat(Nit.esValido("4817205-8")).isFalse();
        assertThat(Nit.esValido("6-1")).isFalse();
        assertThat(Nit.esValido("ABC")).isFalse();
        assertThat(Nit.esValido("")).isFalse();
        assertThat(Nit.esValido(null)).isFalse();
    }
}
