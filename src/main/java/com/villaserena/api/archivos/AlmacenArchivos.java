package com.villaserena.api.archivos;

/**
 * Bucket privado de R2/S3 (fotos de incidencias y facturas). Las fotos privadas nunca tienen una
 * URL permanente: se entregan con una URL firmada de corta duración.
 */
public interface AlmacenArchivos {

    void guardarPrivado(String clave, byte[] contenido, String tipoContenido);

    boolean existePrivado(String clave);

    /** Almacenamiento en bucket público para fotos de catálogos servidas por CDN. */
    void guardarPublico(String clave, byte[] contenido, String tipoContenido);

    /** Comprueba si el objeto público existe. */
    boolean existePublico(String clave);

    /** Lectura interna del mismo archivo privado para adjuntos; nunca expone una URL pública. */
    byte[] leerPrivado(String clave);

    /** URL firmada de lectura, válida por los minutos configurados. */
    String urlFirmada(String clave);
}
