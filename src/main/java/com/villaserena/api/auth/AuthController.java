package com.villaserena.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.dto.CambiarContrasenaPeticion;
import com.villaserena.api.auth.dto.EmpleadoSesion;
import com.villaserena.api.auth.dto.LoginPeticion;
import com.villaserena.api.auth.dto.RefreshPeticion;
import com.villaserena.api.auth.dto.SesionEmpleado;
import com.villaserena.api.auth.dto.Tokens;

import jakarta.validation.Valid;

/** /api/v1/auth — acceso del personal (HU-EMP-01, HU-EMP-02). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String PERSONAL = "hasAnyRole('ADMIN','RECEPCION','ROOM_SERVICE','MANTENIMIENTO_LIMPIEZA')";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public SesionEmpleado login(@Valid @RequestBody LoginPeticion peticion) {
        return authService.login(peticion);
    }

    @PostMapping("/renovar")
    public Tokens renovar(@Valid @RequestBody RefreshPeticion peticion) {
        return authService.renovar(peticion.refreshToken());
    }

    @PostMapping("/cerrar-sesion")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<Void> cerrarSesion(@Valid @RequestBody RefreshPeticion peticion) {
        authService.cerrarSesion(peticion.refreshToken(), UsuarioActual.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cambiar-contrasena")
    @PreAuthorize(PERSONAL)
    public SesionEmpleado cambiarContrasena(@Valid @RequestBody CambiarContrasenaPeticion peticion) {
        return authService.cambiarContrasena(UsuarioActual.id(), peticion);
    }

    @GetMapping("/yo")
    @PreAuthorize(PERSONAL)
    public EmpleadoSesion yo() {
        return authService.yo(UsuarioActual.id());
    }
}
