package com.villaserena.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.auth.dto.Tokens;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.JwtConfig;
import com.villaserena.api.config.PropiedadesVillaSerena;

/**
 * Emite y rota los tokens del personal y del huésped (documento 14, sección 6).
 * <ul>
 * <li>Acceso: JWT de 15 minutos, sin datos personales.</li>
 * <li>Refresh: valor aleatorio de 7 días; en la base solo queda su SHA-256. Cada
 * renovación lo revoca y emite otro (rotación).</li>
 * </ul>
 * Carlos (OTP del huésped) usa {@link #emitirParaHuesped(long)}.
 */
@Service
public class TokenService {

    public static final String ROL_HUESPED = "HUESPED";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final Clock reloj;
    private final Duration duracionAcceso;
    private final Duration duracionRefresh;

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, Clock reloj,
            PropiedadesVillaSerena propiedades) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.reloj = reloj;
        this.duracionAcceso = Duration.ofMinutes(propiedades.jwt().accesoMinutos());
        this.duracionRefresh = Duration.ofDays(propiedades.jwt().refreshDias());
    }

    @Transactional
    public Tokens emitirParaEmpleado(Empleado empleado) {
        Instant ahora = reloj.instant();
        JwtClaimsSet.Builder claims = base(empleado.getId(), ahora)
                .claim(JwtConfig.CLAIM_TIPO, TipoUsuario.EMPLEADO.name())
                .claim(JwtConfig.CLAIM_ROL, empleado.getRol().name())
                .claim(JwtConfig.CLAIM_DEBE_CAMBIAR, empleado.isDebeCambiarContrasena());
        if (empleado.getArea() != null) {
            claims.claim(JwtConfig.CLAIM_AREA, empleado.getArea().name());
        }
        RefreshToken refresh = nuevoRefresh(TipoUsuario.EMPLEADO, ahora);
        refresh.setEmpleadoId(empleado.getId());
        return guardarYFirmar(claims.build(), refresh);
    }

    @Transactional
    public Tokens emitirParaHuesped(long huespedId) {
        Instant ahora = reloj.instant();
        JwtClaimsSet claims = base(huespedId, ahora)
                .claim(JwtConfig.CLAIM_TIPO, TipoUsuario.HUESPED.name())
                .claim(JwtConfig.CLAIM_ROL, ROL_HUESPED)
                .build();
        RefreshToken refresh = nuevoRefresh(TipoUsuario.HUESPED, ahora);
        refresh.setHuespedId(huespedId);
        return guardarYFirmar(claims, refresh);
    }

    /**
     * Valida el refresh y lo revoca (rotación). Devuelve el registro para que quien
     * llama emita el par nuevo según el tipo de usuario.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RefreshToken consumirRefresh(String refreshToken) {
        Instant ahora = reloj.instant();
        RefreshToken registro = refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(r -> r.esValido(ahora))
                .orElseThrow(TokenService::sesionVencida);
        registro.setRevocadoEn(ahora);
        return registro;
    }

    /** Revoca un refresh si existe y pertenece al usuario; si no, no hace nada. */
    @Transactional
    public void revocar(String refreshToken, TipoUsuario tipo, long usuarioId) {
        refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(r -> r.getTipoUsuario() == tipo)
                .filter(r -> usuarioId == (tipo == TipoUsuario.EMPLEADO ? r.getEmpleadoId() : r.getHuespedId()))
                .filter(r -> r.getRevocadoEn() == null)
                .ifPresent(r -> r.setRevocadoEn(reloj.instant()));
    }

    @Transactional
    public void revocarTodosDelEmpleado(long empleadoId) {
        refreshTokens.revocarTodosDelEmpleado(empleadoId, reloj.instant());
    }

    @Transactional
    public void revocarTodosDelHuesped(long huespedId) {
        refreshTokens.revocarTodosDelHuesped(huespedId, reloj.instant());
    }

    public static ApiException sesionVencida() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESION_VENCIDA", "Tu sesión venció. Inicia sesión de nuevo.");
    }

    private JwtClaimsSet.Builder base(long id, Instant ahora) {
        return JwtClaimsSet.builder()
                .issuer("villa-serena-api")
                .subject(Long.toString(id))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracionAcceso));
    }

    private RefreshToken nuevoRefresh(TipoUsuario tipo, Instant ahora) {
        RefreshToken refresh = new RefreshToken();
        refresh.setTipoUsuario(tipo);
        refresh.setExpiraEn(ahora.plus(duracionRefresh));
        return refresh;
    }

    private Tokens guardarYFirmar(JwtClaimsSet claims, RefreshToken refresh) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String valorRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refresh.setTokenHash(hash(valorRefresh));
        refreshTokens.save(refresh);

        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String acceso = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new Tokens(acceso, valorRefresh, "Bearer", duracionAcceso.toSeconds());
    }

    static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
