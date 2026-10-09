package com.villaserena.api.comun;

import java.util.List;

/** Formato único de error del API (esquema {@code Error} del contrato). */
public record ErrorRespuesta(String codigo, String mensaje, List<DetalleError> detalles) {

    public static ErrorRespuesta de(String codigo, String mensaje) {
        return new ErrorRespuesta(codigo, mensaje, List.of());
    }
}
