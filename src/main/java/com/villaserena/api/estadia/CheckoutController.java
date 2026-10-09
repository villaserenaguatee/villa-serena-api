package com.villaserena.api.estadia;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.estadia.CheckoutService.Origen;
import com.villaserena.api.estadia.dto.CheckoutRecepcionPeticion;
import com.villaserena.api.estadia.dto.CheckoutResultado;
import com.villaserena.api.estadia.dto.VistaCheckout;
import com.villaserena.api.reservas.ReservaService;

import jakarta.validation.Valid;

/** Check-out en Recepción con pago único (HU-REC-14, HU-REC-15). */
@RestController
@RequestMapping("/api/v1/checkout/{codigo}")
@PreAuthorize("hasRole('RECEPCION')")
public class CheckoutController {

    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @GetMapping
    public VistaCheckout vista(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return checkout.vista(checkout.reserva(codigo, null), Origen.RECEPCION);
    }

    @PostMapping
    public CheckoutResultado confirmar(@PathVariable String codigo, @Valid @RequestBody CheckoutRecepcionPeticion p) {
        ReservaService.validarCodigo(codigo);
        return checkout.hacerCheckout(checkout.reserva(codigo, null), Origen.RECEPCION, p.comprador().nit(),
                p.comprador().nombreComprador(), p.pago(), false, Responsable.empleado(UsuarioActual.id()));
    }
}
