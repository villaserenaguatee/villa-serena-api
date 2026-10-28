package com.villaserena.api.archivos;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;

/**
 * Imágenes subidas por el personal: solo JPG o PNG de hasta 5 MB, validando el
 * firma del contenido, no la extensión ni el MIME del navegador. HOTEL también
 * exige una imagen decodificable y limita sus dimensiones antes de almacenarla.
 */
@Service
public class ImagenesService {

    /** 5 MB (5 MiB): límite de las fotos (RN-MAN-003). */
    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;

    /** Prefijo de las fotos de incidencias en el bucket privado. */
    public static final String PREFIJO_INCIDENCIAS = "incidencias/";

    /** Prefijo de las fotos de tipos de habitación en el bucket público (servidas por CDN). */
    public static final String PREFIJO_TIPOS = "tipos/";

    /** Prefijo de las fotos del menú en el bucket público (servidas por CDN). */
    public static final String PREFIJO_MENU = "menu/";
    public static final String PREFIJO_HOTEL = "hotel/";

    private static final byte[] FIRMA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final AlmacenArchivos almacen;

    public ImagenesService(AlmacenArchivos almacen) {
        this.almacen = almacen;
    }

    public ImagenSubida subir(UsoImagen uso, byte[] contenido) {
        String rol = com.villaserena.api.auth.UsuarioActual.rol();
        if (uso == UsoImagen.TIPO_HABITACION || uso == UsoImagen.MENU || uso == UsoImagen.HOTEL) {
            if (!"ADMIN".equals(rol)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                        "Solo el administrador puede subir fotos del catálogo.");
            }
        } else if (uso == UsoImagen.INCIDENCIA) {
            if (!java.util.Set.of("RECEPCION", "MANTENIMIENTO_LIMPIEZA").contains(rol)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                        "Solo Recepción y Mantenimiento/Limpieza pueden subir fotos de incidencias.");
            }
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                    "Uso no permitido para carga de imágenes: " + uso);
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
        String clave;
        if (uso == UsoImagen.HOTEL) {
            validarHotel(contenido);
            clave = PREFIJO_HOTEL + UUID.randomUUID() + extension;
            try {
                almacen.guardarPublico(clave, contenido, tipo);
            } catch (RuntimeException error) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ALMACEN_NO_DISPONIBLE",
                        "No se pudo guardar la fotografía. La galería no cambió.");
            }
        } else if (uso == UsoImagen.TIPO_HABITACION) {
            clave = PREFIJO_TIPOS + UUID.randomUUID() + extension;
            almacen.guardarPublico(clave, contenido, tipo);
        } else if (uso == UsoImagen.MENU) {
            clave = PREFIJO_MENU + UUID.randomUUID() + extension;
            almacen.guardarPublico(clave, contenido, tipo);
        } else {
            clave = PREFIJO_INCIDENCIAS + UUID.randomUUID() + extension;
            almacen.guardarPrivado(clave, contenido, tipo);
        }
        return new ImagenSubida(clave);
    }

    private static void validarHotel(byte[] contenido) {
        try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) throw invalido("TIPO_NO_PERMITIDO", "Selecciona una fotografía JPG o PNG válida.");
            var lector = lectores.next();
            try {
                lector.setInput(entrada);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho < 1 || alto < 1 || (long) ancho * alto > 16_000_000)
                    throw invalido("DIMENSIONES_NO_PERMITIDAS", "La fotografía admite hasta 16 millones de píxeles.");
                if (lector.read(0) == null) throw invalido("TIPO_NO_PERMITIDO", "Selecciona una fotografía válida.");
            } finally {
                lector.dispose();
            }
        } catch (IOException | IllegalArgumentException error) {
            throw invalido("TIPO_NO_PERMITIDO", "Selecciona una fotografía JPG o PNG válida.");
        }
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
