package com.villaserena.api.archivos;

/**
 * Bucket privado de MinIO (fotos de incidencias). Las fotos privadas nunca tienen una
 * URL permanente: se entregan con una URL firmada de corta duración.
 */
public interface AlmacenArchivos {

    void guardarPrivado(String clave, byte[] contenido, String tipoContenido);

    boolean existePrivado(String clave);

    /** URL firmada de lectura, válida por los minutos configurados. */
    String urlFirmada(String clave);
}
