package com.villaserena.api.tiemporeal.dto;

import java.time.OffsetDateTime;

/**
 * Sobre común de los cuatro eventos (sección {@code x-websocket} del contrato):
 * tipo, momento y datos. Solo hay cuatro tipos; no se agregan más sin cambiar el
 * contrato.
 */
public record EventoTiempoReal(String tipo, OffsetDateTime ocurridoEn, Object datos) {

    public static final String NUEVO_PEDIDO = "NUEVO_PEDIDO";
    public static final String PEDIDO_ACTUALIZADO = "PEDIDO_ACTUALIZADO";
    public static final String NUEVA_SOLICITUD = "NUEVA_SOLICITUD";
    public static final String HABITACION_ACTUALIZADA = "HABITACION_ACTUALIZADA";
}
