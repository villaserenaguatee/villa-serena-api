package com.villaserena.api.app;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.auth.TipoUsuario;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.auth.dto.Tokens;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.huespedes.Huesped;
import com.villaserena.api.huespedes.HuespedRepository;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.TipoNotificacion;
import com.villaserena.api.reservas.ReservaRepository;

/**
 * Acceso del huésped a la app con un código de 6 dígitos (HU-HUE-08). No hay
 * contraseñas: el huésped prueba que es dueño del correo con el que reservó.
 * <p>
 * Dos cuidados que no son evidentes:
 * <ul>
 * <li>Solicitar el código siempre responde lo mismo, exista o no el correo y haya o
 * no bloqueo. Si respondiera distinto, cualquiera podría averiguar quién se hospeda
 * en el hotel probando correos.</li>
 * <li>Pedir otro código no reinicia los intentos fallidos, porque si no el bloqueo
 * de 5 intentos no serviría de nada: bastaría pedir un código nuevo.</li>
 * </ul>
 */
@Service
public class AccesoHuespedService {

    /** Lo que responde siempre, para no revelar si el correo tiene reservas. */
    public static final String MENSAJE_GENERICO = "Si el correo tiene reservas, recibirás un código para entrar.";

    static final Duration VIGENCIA = Duration.ofMinutes(10);

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AccesoHuespedService.class);

    private final HuespedRepository huespedes;
    private final ReservaRepository reservas;
    private final CodigoOtpRepository codigos;
    private final DispositivoPushRepository dispositivos;
    private final TokenService tokens;
    private final OutboxService outbox;
    private final PasswordEncoder passwordEncoder;
    private final Clock reloj;
    private final int intentosMaximos;
    private final Duration duracionBloqueo;

    public AccesoHuespedService(HuespedRepository huespedes, ReservaRepository reservas, CodigoOtpRepository codigos,
            DispositivoPushRepository dispositivos, TokenService tokens, OutboxService outbox,
            PasswordEncoder passwordEncoder, Clock reloj, PropiedadesVillaSerena propiedades) {
        this.huespedes = huespedes;
        this.reservas = reservas;
        this.codigos = codigos;
        this.dispositivos = dispositivos;
        this.tokens = tokens;
        this.outbox = outbox;
        this.passwordEncoder = passwordEncoder;
        this.reloj = reloj;
        this.intentosMaximos = propiedades.login().intentosMaximos();
        this.duracionBloqueo = Duration.ofMinutes(propiedades.login().bloqueoMinutos());
    }

    /**
     * Genera y encola el código si el correo tiene reservas. Nunca falla por el
     * correo: quien llama recibe el mismo mensaje en todos los casos.
     */
    @Transactional
    public void solicitarCodigo(String correo) {
        Instant ahora = reloj.instant();
        Optional<Huesped> encontrado = huespedes.findByCorreoIgnoreCase(correo.trim());
        if (encontrado.isEmpty()) {
            log.debug("Código de acceso pedido para un correo sin huésped");
            return;
        }
        Huesped huesped = encontrado.get();
        if (!reservas.existsByHuespedId(huesped.getId())) {
            log.debug("Código de acceso pedido para un huésped sin reservas");
            return;
        }
        if (huesped.otpBloqueado(ahora)) {
            // Se responde igual, pero no se manda otro código mientras esté bloqueado.
            log.debug("Código de acceso pedido con bloqueo vigente");
            return;
        }

        String codigo = nuevoCodigo();
        CodigoOtp registro = new CodigoOtp();
        registro.setHuespedId(huesped.getId());
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setExpiraEn(ahora.plus(VIGENCIA));
        codigos.save(registro);

        outbox.encolar(TipoNotificacion.CORREO_OTP, huesped.getCorreo(),
                Map.of("nombreHuesped", huesped.getNombreCompleto(), "codigo", codigo,
                        "minutos", VIGENCIA.toMinutes()));
    }

    /**
     * Verifica el código y entrega los tokens del huésped. Cinco fallos seguidos
     * bloquean 15 minutos. Incorrecto, vencido, usado o bloqueado responden lo mismo
     * (401 {@code CODIGO_INVALIDO}), salvo el bloqueo, que sí se dice para que la
     * app pueda explicar la espera.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens verificarCodigo(String correo, String codigo) {
        Instant ahora = reloj.instant();
        Huesped huesped = huespedes.findByCorreoIgnoreCase(correo.trim())
                .orElseThrow(AccesoHuespedService::codigoInvalido);
        if (huesped.otpBloqueado(ahora)) {
            throw bloqueado();
        }

        List<CodigoOtp> vigentes = codigos.vigentes(huesped.getId(), ahora);
        Optional<CodigoOtp> acertado = vigentes.stream()
                .filter(c -> passwordEncoder.matches(codigo, c.getCodigoHash()))
                .findFirst();
        if (acertado.isEmpty()) {
            fallo(huesped, ahora);
            throw codigoInvalido();
        }

        // Un solo uso, y los otros códigos vigentes dejan de servir: si alguien pidió
        // varios, solo el que usó vale.
        acertado.get().setUsadoEn(ahora);
        vigentes.forEach(c -> c.setUsadoEn(c.getUsadoEn() == null ? ahora : c.getUsadoEn()));
        huesped.setOtpIntentosFallidos(0);
        huesped.setOtpBloqueadoHasta(null);
        return tokens.emitirParaHuesped(huesped.getId());
    }

    /** Cierra la sesión del huésped: revoca el refresh y borra sus teléfonos (RN-NOT-004). */
    @Transactional
    public void cerrarSesion(String refreshToken, long huespedId) {
        tokens.revocar(refreshToken, TipoUsuario.HUESPED, huespedId);
        dispositivos.deleteByHuespedId(huespedId);
        dispositivos.flush();
    }

    private void fallo(Huesped huesped, Instant ahora) {
        int intentos = huesped.getOtpIntentosFallidos() + 1;
        if (intentos >= intentosMaximos) {
            huesped.setOtpBloqueadoHasta(ahora.plus(duracionBloqueo));
            intentos = 0;
        }
        huesped.setOtpIntentosFallidos(intentos);
    }

    private static String nuevoCodigo() {
        return String.format("%06d", ALEATORIO.nextInt(1_000_000));
    }

    private static ApiException codigoInvalido() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CODIGO_INVALIDO",
                "El código no es válido o ya venció. Pide uno nuevo.");
    }

    private ApiException bloqueado() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACCESO_BLOQUEADO",
                "Demasiados intentos. Espera " + duracionBloqueo.toMinutes() + " minutos e inténtalo de nuevo.");
    }
}
