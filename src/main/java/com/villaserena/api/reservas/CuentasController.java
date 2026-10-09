package com.villaserena.api.reservas;

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
import com.villaserena.api.reservas.dto.AgregarCargoPeticion;
import com.villaserena.api.reservas.dto.CargoCuentaRecepcion;
import com.villaserena.api.reservas.dto.CuentaRecepcion;
import com.villaserena.api.reservas.dto.MotivoPeticion;

import jakarta.validation.Valid;

/** Cuenta de la reserva en Recepción (HU-REC-13). */
@RestController
@RequestMapping("/api/v1/cuentas/{codigo}")
@PreAuthorize("hasRole('RECEPCION')")
public class CuentasController {

    private final CuentaService cuentas;

    public CuentasController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping
    public CuentaRecepcion consultar(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return cuentas.paraRecepcion(codigo);
    }

    @PostMapping("/cargos")
    @ResponseStatus(HttpStatus.CREATED)
    public CargoCuentaRecepcion agregarCargo(@PathVariable String codigo, @Valid @RequestBody AgregarCargoPeticion p) {
        ReservaService.validarCodigo(codigo);
        return cuentas.agregarCargo(codigo, p, UsuarioActual.id());
    }

    @PostMapping("/cargos/{id}/anular")
    public CargoCuentaRecepcion anularCargo(@PathVariable String codigo, @PathVariable long id,
            @Valid @RequestBody MotivoPeticion p) {
        ReservaService.validarCodigo(codigo);
        return cuentas.anularCargo(codigo, id, p.motivo(), UsuarioActual.id());
    }
}
