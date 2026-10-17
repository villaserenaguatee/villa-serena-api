package com.villaserena.api.roomservice;

import java.util.List;

/**
 * Estados de un pedido (documento 07, sección 6). El avance es en orden y sin
 * saltos; ENTREGADO y CANCELADO son finales (RG-EST-05).
 */
public enum EstadoPedido {

    NUEVO,
    EN_PREPARACION,
    EN_CAMINO,
    ENTREGADO,
    CANCELADO;

    /** Los que aparecen en la cola de Room Service (HU-RS-01). */
    public static final List<EstadoPedido> ACTIVOS = List.of(NUEVO, EN_PREPARACION, EN_CAMINO);

    public boolean esActivo() {
        return ACTIVOS.contains(this);
    }

    /** El único estado al que puede pasar, o {@code null} si ya es final. */
    public EstadoPedido siguiente() {
        return switch (this) {
            case NUEVO -> EN_PREPARACION;
            case EN_PREPARACION -> EN_CAMINO;
            case EN_CAMINO -> ENTREGADO;
            case ENTREGADO, CANCELADO -> null;
        };
    }
}
