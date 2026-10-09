package com.villaserena.api.estadia;

import org.springframework.http.ResponseEntity;
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
import com.villaserena.api.estadia.dto.CheckoutAppPeticion;
import com.villaserena.api.estadia.dto.CheckoutResultado;
import com.villaserena.api.estadia.dto.VistaCheckout;
import com.villaserena.api.reservas.CuentaService;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.dto.CuentaHuesped;
import com.villaserena.api.reservas.dto.PagoIniciado;

import jakarta.validation.Valid;

/**
 * Cuenta, pago del saldo y check-out desde la app (HU-HUE-15, HU-HUE-16). Solo las
 * reservas del propio huésped: una ajena responde 404.
 */
@RestController
@RequestMapping("/api/v1/app/reservas/{codigo}")
@PreAuthorize("hasRole('HUESPED')")
public class AppCuentaController {

    private final CuentaService cuentas;
    private final CheckoutService checkout;
    private final PagoSaldoAppService pagoSaldo;

    public AppCuentaController(CuentaService cuentas, CheckoutService checkout, PagoSaldoAppService pagoSaldo) {
        this.cuentas = cuentas;
        this.checkout = checkout;
        this.pagoSaldo = pagoSaldo;
    }

    @GetMapping("/cuenta")
    public CuentaHuesped cuenta(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return cuentas.paraHuesped(codigo, UsuarioActual.id());
    }

    @GetMapping("/checkout")
    public VistaCheckout vistaCheckout(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return checkout.vista(checkout.reserva(codigo, UsuarioActual.id()), Origen.APP);
    }

    @PostMapping("/checkout")
    public CheckoutResultado confirmarCheckout(@PathVariable String codigo, @Valid @RequestBody CheckoutAppPeticion p) {
        ReservaService.validarCodigo(codigo);
        return checkout.hacerCheckout(checkout.reserva(codigo, UsuarioActual.id()), Origen.APP, p.comprador().nit(),
                p.comprador().nombreComprador(), null, p.aceptaCancelarPedidos(), Responsable.HUESPED);
    }

    @PostMapping("/pago-saldo")
    public ResponseEntity<PagoIniciado> pagarSaldo(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return pagoSaldo.iniciar(checkout.reserva(codigo, UsuarioActual.id()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
