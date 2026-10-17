package com.villaserena.api.notificaciones;

/**
 * Eventos 1 y 2 en tiempo real (documento 07, sección 12): pedido nuevo y cambio de
 * estado de un pedido. Los implementa el WebSocket; quien cambia un pedido los llama
 * y la publicación ocurre después de que la transacción se confirme.
 * <p>
 * Mientras no haya implementación, los pedidos funcionan igual y las pantallas se
 * actualizan al recargar.
 */
public interface PedidoEventos {

    void pedidoNuevo(long pedidoId);

    void pedidoActualizado(long pedidoId);
}
