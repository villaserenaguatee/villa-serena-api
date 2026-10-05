package com.villaserena.api.comun;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con el formato único del API ({@code {codigo, mensaje, detalles}}).
 * Los servicios la lanzan y {@link ManejadorErrores} la convierte en la respuesta HTTP.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final List<DetalleError> detalles;

    public ApiException(HttpStatus estado, String codigo, String mensaje) {
        this(estado, codigo, mensaje, List.of());
    }

    public ApiException(HttpStatus estado, String codigo, String mensaje, List<DetalleError> detalles) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.detalles = detalles;
    }

    public static ApiException noEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No se encontró el recurso solicitado.");
    }

    public static ApiException conflicto(String codigo, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje);
    }

    public static ApiException datosInvalidos(String mensaje, List<DetalleError> detalles) {
        return new ApiException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", mensaje, detalles);
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<DetalleError> getDetalles() {
        return detalles;
    }
}
