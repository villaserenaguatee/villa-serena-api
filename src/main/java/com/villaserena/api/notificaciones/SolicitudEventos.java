package com.villaserena.api.notificaciones;

/**
 * Evento 3 en tiempo real: solicitud nueva de un huésped ({@code /topic/solicitudes}).
 * Lo implementa el WebSocket (OBJ-3A-1). Quien crea la solicitud lo llama después de
 * confirmar la transacción. Mientras no exista, Limpieza ve la solicitud al recargar.
 */
public interface SolicitudEventos {

    void solicitudNueva(long solicitudId);
}
