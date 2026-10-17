package com.villaserena.api.app;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.app.dto.DispositivoPushPeticion;
import com.villaserena.api.app.dto.DispositivoPushRegistrado;
import com.villaserena.api.app.dto.ReservaAppDetalle;
import com.villaserena.api.app.dto.ReservaAppResumen;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.reservas.ReservaService;

import jakarta.validation.Valid;

/** Mis reservas y teléfonos para push (HU-HUE-09, HU-HUE-17). */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('HUESPED')")
public class AppReservasController {

    private final MisReservasService reservas;
    private final DispositivosPushService dispositivos;

    public AppReservasController(MisReservasService reservas, DispositivosPushService dispositivos) {
        this.reservas = reservas;
        this.dispositivos = dispositivos;
    }

    @GetMapping("/reservas")
    public List<ReservaAppResumen> mias() {
        return reservas.mias(UsuarioActual.id());
    }

    @GetMapping("/reservas/{codigo}")
    public ReservaAppDetalle mia(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return reservas.mia(codigo, UsuarioActual.id());
    }

    @PostMapping("/dispositivos-push")
    public ResponseEntity<DispositivoPushRegistrado> registrar(@Valid @RequestBody DispositivoPushPeticion p) {
        DispositivosPushService.Resultado resultado = dispositivos.registrar(UsuarioActual.id(), p.tokenExpo());
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new DispositivoPushRegistrado(resultado.id()));
    }

    @DeleteMapping("/dispositivos-push/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable long id) {
        dispositivos.eliminar(UsuarioActual.id(), id);
    }
}
