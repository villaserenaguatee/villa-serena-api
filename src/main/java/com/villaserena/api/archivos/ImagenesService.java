package com.villaserena.api.archivos;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

/**
 * Imágenes subidas por el personal: solo JPG o PNG de hasta 5 MB, validando el
 * contenido real (no la extensión ni el tipo que dice el navegador). Por ahora solo
 * el uso INCIDENCIA (bucket privado); los usos de Administración llegan con su módulo.
 */
@Service
public class ImagenesService {

    /** 5 MB (5 MiB): límite de las fotos (RN-MAN-003). */
    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;

    /** Prefijo de las fotos de incidencias en el bucket privado. */
    public static final String PREFIJO_INCIDENCIAS = "incidencias/";

    private static final byte[] FIRMA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final AlmacenArchivos almacen;

    public ImagenesService(AlmacenArchivos almacen) {
        this.almacen = almacen;
    }

    public ImagenSubida subir(UsoImagen uso, byte[] contenido) {
        if (uso != UsoImagen.INCIDENCIA) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                    "Solo puedes subir fotos de incidencias.");
        }
        if (contenido == null || contenido.length == 0) {
            throw invalido("TIPO_NO_PERMITIDO", "Selecciona una imagen JPG o PNG.");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw invalido("ARCHIVO_DEMASIADO_GRANDE", "La imagen no puede superar los 5 MB.");
        }
        String extension;
        String tipo;
        if (empiezaCon(contenido, FIRMA_JPG)) {
            extension = ".jpg";
            tipo = "image/jpeg";
        } else if (empiezaCon(contenido, FIRMA_PNG)) {
            extension = ".png";
            tipo = "image/png";
        } else {
            throw invalido("TIPO_NO_PERMITIDO", "Solo se aceptan imágenes JPG o PNG.");
        }
        String clave = PREFIJO_INCIDENCIAS + UUID.randomUUID() + extension;
        almacen.guardarPrivado(clave, contenido, tipo);
        return new ImagenSubida(clave);
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        return contenido.length >= firma.length
                && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
    }

    private static ApiException invalido(String codigo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, mensaje,
                List.of(new DetalleError("archivo", mensaje)));
    }
}
