package com.villaserena.api.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security del API: sin sesión de servidor, JWT propio en
 * {@code Authorization: Bearer} y permisos por rol con {@code @PreAuthorize}
 * (documento 09, sección 7).
 * <p>
 * <b>Cómo aplicar la matriz del documento 09 (sección 7.2) en tus endpoints:</b>
 * <pre>
 * ✅  @PreAuthorize("hasRole('RECEPCION')")                  rol permitido
 * 👁  @PreAuthorize("hasRole('ADMIN')") solo en métodos GET  solo lectura
 * 👤  @PreAuthorize("hasRole('HUESPED')") y en el servicio comparar el dueño con
 *     UsuarioActual.id(); si no es suyo, lanzar ApiException.noEncontrado() (404)
 * ⚠️  @PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and hasAnyAuthority('AREA_LIMPIEZA','AREA_AMBAS')")
 *     o la condición de estado/empleado a cargo en el servicio
 * —   no se incluye el rol: Spring responde 403 ACCESO_DENEGADO
 * </pre>
 * Las rutas nuevas que deban ser públicas se agregan en {@link #RUTAS_PUBLICAS}
 * (avisar a Pablo).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Rutas sin JWT (documento 09, sección 7.3). Stripe y el canal tienen su propia protección. */
    static final String[] RUTAS_PUBLICAS = {
            "/api/v1/auth/login",
            "/api/v1/auth/renovar",
            "/api/v1/publico/**",
            "/api/v1/pagos/stripe/webhook",
            "/api/v1/canal/**",
            "/api/v1/app/acceso/**",
            // El WebSocket se autentica en el mensaje CONNECT, no por HTTP
            // (ver AutenticacionStomp): aquí solo se permite abrir la conexión.
            "/ws",
            "/ws/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    @Bean
    SecurityFilterChain filtroSeguridad(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            RespuestaErrorSeguridad respuestaError) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((peticion, respuesta, e) -> respuestaError.escribir(respuesta,
                                HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                                "Debes iniciar sesión para continuar."))
                        .accessDeniedHandler((peticion, respuesta, e) -> respuestaError.escribir(respuesta,
                                HttpServletResponse.SC_FORBIDDEN, "ACCESO_DENEGADO", "Acceso denegado.")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((peticion, respuesta, e) -> respuestaError.escribir(respuesta,
                                HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                                "Debes iniciar sesión para continuar."))
                        .accessDeniedHandler((peticion, respuesta, e) -> respuestaError.escribir(respuesta,
                                HttpServletResponse.SC_FORBIDDEN, "ACCESO_DENEGADO", "Acceso denegado.")))
                .addFilterAfter(new FiltroContrasenaTemporal(respuestaError), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS: solo los orígenes propios (en el hito, http://localhost:3000). La app no depende de CORS. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(PropiedadesVillaSerena propiedades) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(propiedades.cors().origenes());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
