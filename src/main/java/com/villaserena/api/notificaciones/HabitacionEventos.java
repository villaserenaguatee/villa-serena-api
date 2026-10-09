package com.villaserena.api.notificaciones;

/**
 * Evento 4 en tiempo real: cambio de estado de una habitación ({@code /topic/habitaciones}).
 * Lo implementa Hugo con WebSocket (OBJ-3A-1). Quien cambia una habitación lo llama
 * después de confirmar la transacción. Mientras no exista, Recepción ve el cambio
 * al abrir la pantalla.
 */
public interface HabitacionEventos {

    void habitacionCambio(long habitacionId);
}
