package com.villaserena.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.auth.dto.CambiarContrasenaPeticion;
import com.villaserena.api.auth.dto.EmpleadoSesion;
import com.villaserena.api.auth.dto.LoginPeticion;
import com.villaserena.api.auth.dto.SesionEmpleado;
import com.villaserena.api.auth.dto.Tokens;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.config.PropiedadesVillaSerena;

/** Acceso del personal: HU-EMP-01 y HU-EMP-02. */
@Service
public class AuthService {

    private final EmpleadoRepository empleados;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock reloj;
    private final int intentosMaximos;
    private final Duration duracionBloqueo;

    public AuthService(EmpleadoRepository empleados, TokenService tokens, PasswordEncoder passwordEncoder,
            Clock reloj, PropiedadesVillaSerena propiedades) {
        this.empleados = empleados;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.reloj = reloj;
        this.intentosMaximos = propiedades.login().intentosMaximos();
        this.duracionBloqueo = Duration.ofMinutes(propiedades.login().bloqueoMinutos());
    }

    /**
     * Correo y contraseña. 5 intentos fallidos seguidos bloquean 15 minutos; el
     * contador se reinicia al entrar. Un empleado INACTIVO recibe el mismo mensaje
     * genérico. Los intentos fallidos se guardan aunque la respuesta sea un error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SesionEmpleado login(LoginPeticion peticion) {
        Instant ahora = reloj.instant();
        Empleado empleado = empleados.findByCorreoIgnoreCase(peticion.correo().trim())
                .orElseThrow(AuthService::credencialesInvalidas);

        if (empleado.estaBloqueado(ahora)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CUENTA_BLOQUEADA",
                    "Cuenta bloqueada por intentos fallidos. Intenta de nuevo en 15 minutos.");
        }
        if (!passwordEncoder.matches(peticion.contrasena(), empleado.getContrasenaHash())) {
            int intentos = empleado.getIntentosFallidos() + 1;
            if (intentos >= intentosMaximos) {
                empleado.setBloqueadoHasta(ahora.plus(duracionBloqueo));
                intentos = 0;
            }
            empleado.setIntentosFallidos(intentos);
            throw credencialesInvalidas();
        }
        if (!empleado.estaActivo()) {
            throw credencialesInvalidas();
        }

        empleado.setIntentosFallidos(0);
        empleado.setBloqueadoHasta(null);
        return sesion(empleado);
    }

    /** Rota el refresh. El estado del empleado se revisa aquí, no en cada petición (VIS-04). */
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens renovar(String refreshToken) {
        RefreshToken anterior = tokens.consumirRefresh(refreshToken);
        if (anterior.getTipoUsuario() == TipoUsuario.HUESPED) {
            return tokens.emitirParaHuesped(anterior.getHuespedId());
        }
        Empleado empleado = empleados.findById(anterior.getEmpleadoId())
                .filter(Empleado::estaActivo)
                .orElseThrow(TokenService::sesionVencida);
        return tokens.emitirParaEmpleado(empleado);
    }

    @Transactional
    public void cerrarSesion(String refreshToken, long empleadoId) {
        tokens.revocar(refreshToken, TipoUsuario.EMPLEADO, empleadoId);
    }

    /**
     * Cambia la contraseña (temporal o propia). Revoca todas las sesiones anteriores y
     * emite tokens nuevos sin la marca de contraseña temporal.
     */
    @Transactional
    public SesionEmpleado cambiarContrasena(long empleadoId, CambiarContrasenaPeticion peticion) {
        Empleado empleado = empleados.findById(empleadoId).orElseThrow(TokenService::sesionVencida);

        if (!passwordEncoder.matches(peticion.contrasenaActual(), empleado.getContrasenaHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTRASENA_ACTUAL_INCORRECTA",
                    "La contraseña actual es incorrecta.",
                    List.of(new DetalleError("contrasenaActual", "La contraseña actual es incorrecta.")));
        }
        if (!peticion.contrasenaNueva().equals(peticion.confirmacion())) {
            throw ApiException.datosInvalidos("La confirmación no coincide con la nueva contraseña.",
                    List.of(new DetalleError("confirmacion", "No coincide con la nueva contraseña.")));
        }
        List<DetalleError> reglas = reglasIncumplidas(peticion.contrasenaNueva(), peticion.contrasenaActual());
        if (!reglas.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTRASENA_INVALIDA",
                    "La nueva contraseña no cumple las reglas.", reglas);
        }

        empleado.setContrasenaHash(passwordEncoder.encode(peticion.contrasenaNueva()));
        empleado.setDebeCambiarContrasena(false);
        tokens.revocarTodosDelEmpleado(empleadoId);
        return sesion(empleado);
    }

    @Transactional(readOnly = true)
    public EmpleadoSesion yo(long empleadoId) {
        return empleados.findById(empleadoId).map(EmpleadoSesion::de).orElseThrow(TokenService::sesionVencida);
    }

    /** Reglas de HU-EMP-02: al menos 8 caracteres, una letra, un número y distinta de la actual. */
    static List<DetalleError> reglasIncumplidas(String nueva, String actual) {
        List<DetalleError> reglas = new ArrayList<>();
        if (nueva.length() < 8) {
            reglas.add(new DetalleError("contrasenaNueva", "Debe tener al menos 8 caracteres."));
        }
        if (nueva.chars().noneMatch(Character::isLetter)) {
            reglas.add(new DetalleError("contrasenaNueva", "Debe tener al menos una letra."));
        }
        if (nueva.chars().noneMatch(Character::isDigit)) {
            reglas.add(new DetalleError("contrasenaNueva", "Debe tener al menos un número."));
        }
        if (nueva.equals(actual)) {
            reglas.add(new DetalleError("contrasenaNueva", "Debe ser distinta de la contraseña actual."));
        }
        return reglas;
    }

    private SesionEmpleado sesion(Empleado empleado) {
        return SesionEmpleado.de(tokens.emitirParaEmpleado(empleado), EmpleadoSesion.de(empleado));
    }

    private static ApiException credencialesInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos.");
    }
}
