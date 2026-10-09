package com.villaserena.api.comun;

/** Detalle de un error por campo (esquema {@code DetalleError} del contrato). */
public record DetalleError(String campo, String mensaje) {
}
