package com.villaserena.api.comun;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Convierte las excepciones en el formato único de error del contrato. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorRespuesta> api(ApiException e) {
        return ResponseEntity.status(e.getEstado())
                .body(new ErrorRespuesta(e.getCodigo(), e.getMessage(), e.getDetalles()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e) {
        List<DetalleError> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new DetalleError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("DATOS_INVALIDOS", "Los datos enviados no son válidos.", detalles));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
            MultipartException.class})
    ResponseEntity<ErrorRespuesta> peticionMalFormada(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de("DATOS_INVALIDOS", "Los datos enviados no son válidos."));
    }

    /** Subidas que superan el tope del servidor (las imágenes se validan antes con un mensaje propio). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorRespuesta> archivoDemasiadoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ErrorRespuesta.de("ARCHIVO_DEMASIADO_GRANDE", "La imagen no puede superar los 5 MB."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorRespuesta> accesoDenegado(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorRespuesta.de("ACCESO_DENEGADO", "Acceso denegado."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorRespuesta> noAutenticado(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorRespuesta.de("NO_AUTENTICADO", "Debes iniciar sesión para continuar."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorRespuesta.de("NO_ENCONTRADO", "No se encontró el recurso solicitado."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorRespuesta> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorRespuesta.de("ERROR_INTERNO", "Ocurrió un error inesperado. Intenta de nuevo."));
    }
}
