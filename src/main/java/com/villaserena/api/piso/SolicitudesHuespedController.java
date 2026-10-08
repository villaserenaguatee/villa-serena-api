package com.villaserena.api.piso;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.piso.dto.Solicitudes;
import com.villaserena.api.reservas.ReservaService;

import jakarta.validation.Valid;

/** Solicitudes del huésped desde la app, solo de su reserva (HU-HUE-12, 13 y 14). */
@RestController
@RequestMapping("/api/v1/app/reservas/{codigo}")
@PreAuthorize("hasRole('HUESPED')")
public class SolicitudesHuespedController {

    private final SolicitudService solicitudes;

    public SolicitudesHuespedController(SolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @GetMapping("/articulos")
    public List<Solicitudes.ArticuloSolicitable> articulos(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return solicitudes.articulos(codigo, UsuarioActual.id());
    }

    @GetMapping("/solicitudes")
    public List<Solicitudes.DelHuesped> misSolicitudes(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return solicitudes.misSolicitudes(codigo, UsuarioActual.id());
    }

    @PostMapping("/solicitudes/limpieza")
    @ResponseStatus(HttpStatus.CREATED)
    public Solicitudes.DelHuesped pedirLimpieza(@PathVariable String codigo,
            @Valid @RequestBody(required = false) Solicitudes.PedirLimpieza p) {
        ReservaService.validarCodigo(codigo);
        return solicitudes.pedirLimpieza(codigo, UsuarioActual.id(), p == null ? null : p.comentario());
    }

    @PostMapping("/solicitudes/articulos")
    @ResponseStatus(HttpStatus.CREATED)
    public Solicitudes.DelHuesped pedirArticulos(@PathVariable String codigo,
            @Valid @RequestBody Solicitudes.PedirArticulos p) {
        ReservaService.validarCodigo(codigo);
        return solicitudes.pedirArticulos(codigo, UsuarioActual.id(), p.articulos());
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public Solicitudes.DelHuesped cancelar(@PathVariable String codigo, @PathVariable long id) {
        ReservaService.validarCodigo(codigo);
        return solicitudes.cancelar(codigo, UsuarioActual.id(), id);
    }
}
