package com.villaserena.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void decodificaClaveValidaDeAlMenos32Bytes() {
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) raw[i] = (byte) (i + 1);
        String base64 = Base64.getEncoder().encodeToString(raw);

        PropiedadesVillaSerena props = propiedadesConSecreto(base64);
        SecretKey key = config.claveJwt(props);

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).isEqualTo(raw);
    }

    @Test
    void rechazaSecretoNuloVacioOPlaceholderSinResolver() {
        assertThatThrownBy(() -> config.claveJwt(propiedadesConSecreto(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta JWT_SECRET");

        assertThatThrownBy(() -> config.claveJwt(propiedadesConSecreto("   ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta JWT_SECRET");

        assertThatThrownBy(() -> config.claveJwt(propiedadesConSecreto("${JWT_SECRET}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta JWT_SECRET");
    }

    @Test
    void rechazaBase64InvalidoConMensajeClaro() {
        assertThatThrownBy(() -> config.claveJwt(propiedadesConSecreto("invalido$con$dolares")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET debe ser una cadena Base64 válida");
    }

    @Test
    void rechazaClaveConMenosDe32Bytes() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> config.claveJwt(propiedadesConSecreto(corta)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe tener al menos 32 bytes");
    }

    private static PropiedadesVillaSerena propiedadesConSecreto(String secreto) {
        return new PropiedadesVillaSerena(
                "America/Guatemala",
                null,
                new PropiedadesVillaSerena.Jwt(secreto, 15, 7),
                null,
                null,
                "http://localhost:3000",
                null,
                null,
                null,
                null
        );
    }
}
