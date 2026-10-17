package com.villaserena.api.archivos;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida de imágenes (HU-MYL-06 y HU-REC-17): Recepción y Mantenimiento/Limpieza de
 * cualquier área suben la foto de una incidencia y luego envían su clave al reportarla.
 */
@RestController
@RequestMapping("/api/v1/archivos")
@PreAuthorize("hasAnyRole('RECEPCION', 'MANTENIMIENTO_LIMPIEZA')")
public class ArchivosController {

    private final ImagenesService imagenes;

    public ArchivosController(ImagenesService imagenes) {
        this.imagenes = imagenes;
    }

    @PostMapping(path = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImagenSubida subir(@RequestPart("archivo") MultipartFile archivo, @RequestParam UsoImagen uso)
            throws IOException {
        return imagenes.subir(uso, archivo.getBytes());
    }
}
