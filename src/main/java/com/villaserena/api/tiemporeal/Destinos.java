package com.villaserena.api.tiemporeal;

/** Los cuatro destinos STOMP del contrato. No hay otros. */
public final class Destinos {

    /** Cola de Room Service: eventos 1 y 2. */
    public static final String PEDIDOS = "/topic/pedidos";

    /** Cola privada del huésped: evento 2, solo sus pedidos. */
    public static final String PEDIDOS_HUESPED = "/queue/pedidos";

    /** Solicitudes de limpieza y artículos: evento 3 (OBJ-3B-2). */
    public static final String SOLICITUDES = "/topic/solicitudes";

    /** Cambios de estado de habitación: evento 4. */
    public static final String HABITACIONES = "/topic/habitaciones";

    private Destinos() {
    }
}
