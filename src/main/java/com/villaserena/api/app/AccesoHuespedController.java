package com.villaserena.api.app;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.app.dto.CodigoAccesoPeticion;
import com.villaserena.api.app.dto.MensajeGenerico;
import com.villaserena.api.app.dto.VerificarCodigoPeticion;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.auth.dto.RefreshPeticion;
import com.villaserena.api.auth.dto.Tokens;

import jakarta.validation.Valid;

/**
 * Acceso del huésped a la app (HU-HUE-08). Solicitar y verificar el código son
 * públicos (están en RUTAS_PUBLICAS); cerrar sesión necesita el token del huésped.
 * La renovación usa el /auth/renovar común, que ya distingue huésped de empleado.
 */
@RestController
@RequestMapping("/api/v1/app")
public class AccesoHuespedController {

    private final AccesoHuespedService acceso;

    public AccesoHuespedController(AccesoHuespedService acceso) {
        this.acceso = acceso;
    }

    @PostMapping("/acceso/solicitar-codigo")
    public MensajeGenerico solicitarCodigo(@Valid @RequestBody CodigoAccesoPeticion peticion) {
        acceso.solicitarCodigo(peticion.correo());
        return new MensajeGenerico(AccesoHuespedService.MENSAJE_GENERICO);
    }

    @PostMapping("/acceso/verificar-codigo")
    public Tokens verificarCodigo(@Valid @RequestBody VerificarCodigoPeticion peticion) {
        return acceso.verificarCodigo(peticion.correo(), peticion.codigo());
    }

    @PostMapping("/cerrar-sesion")
    @PreAuthorize("hasRole('HUESPED')")
    public void cerrarSesion(@Valid @RequestBody RefreshPeticion peticion) {
        acceso.cerrarSesion(peticion.refreshToken(), UsuarioActual.id());
    }
}
