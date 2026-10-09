package com.villaserena.api.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * JWT propios firmados con HS256 y la clave {@code JWT_SECRET} del .env.
 * <p>
 * Claims: {@code sub} (id), {@code tipo} (EMPLEADO o HUESPED), {@code rol},
 * {@code area} (solo MANTENIMIENTO_LIMPIEZA) y {@code debeCambiarContrasena}.
 * Cada token se convierte en las autoridades {@code ROLE_<rol>} y, si hay área,
 * {@code AREA_<area>}, para usarlas con {@code @PreAuthorize}.
 */
@Configuration
public class JwtConfig {

    public static final String CLAIM_TIPO = "tipo";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_AREA = "area";
    public static final String CLAIM_DEBE_CAMBIAR = "debeCambiarContrasena";

    @Bean
    SecretKey claveJwt(PropiedadesVillaSerena propiedades) {
        String secreto = propiedades.jwt().secreto();
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException("Falta JWT_SECRET en el .env (ver .env.example).");
        }
        byte[] bytes = Base64.getDecoder().decode(secreto);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes en Base64.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey claveJwt) {
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> autoridades = new ArrayList<>();
            String rol = jwt.getClaimAsString(CLAIM_ROL);
            if (rol != null) {
                autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
            }
            String area = jwt.getClaimAsString(CLAIM_AREA);
            if (area != null) {
                autoridades.add(new SimpleGrantedAuthority("AREA_" + area));
            }
            return List.copyOf(autoridades);
        });
        return convertidor;
    }
}
