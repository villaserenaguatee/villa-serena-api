package com.villaserena.api.reservas.pagos;

import org.springframework.http.HttpStatus;

import com.villaserena.api.comun.ApiException;

/** Recurrente rechazó o no respondió una operación. */
public class ErrorPasarela extends ApiException {

    public ErrorPasarela(String mensaje) {
        super(HttpStatus.BAD_GATEWAY, "ERROR_PASARELA", mensaje);
    }
}
