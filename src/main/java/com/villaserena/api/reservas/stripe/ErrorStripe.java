package com.villaserena.api.reservas.stripe;

import org.springframework.http.HttpStatus;

import com.villaserena.api.comun.ApiException;

/** Stripe rechazó o no respondió una operación. */
public class ErrorStripe extends ApiException {

    public ErrorStripe(String mensaje) {
        super(HttpStatus.BAD_GATEWAY, "ERROR_STRIPE", mensaje);
    }
}
