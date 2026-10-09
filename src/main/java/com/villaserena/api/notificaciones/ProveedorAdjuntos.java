package com.villaserena.api.notificaciones;

import java.util.List;
import java.util.Map;

/**
 * Archivos que acompañan a un correo del Outbox (por ejemplo, el PDF de la factura).
 * Se generan al enviar, no al encolar: si fallan, el Outbox reintenta el correo.
 */
public interface ProveedorAdjuntos {

    boolean soporta(TipoNotificacion tipo);

    /** @param datos el payload del aviso */
    List<Adjunto> adjuntos(Map<String, Object> datos);

    record Adjunto(String nombre, byte[] contenido, String tipoContenido) {
    }
}
