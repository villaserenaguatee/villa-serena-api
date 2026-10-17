package com.villaserena.api.comun;

/**
 * Responsable de una acción: un empleado (con su id) o un actor sin usuario
 * (cliente web, canal, Stripe, sistema o huésped).
 */
public record Responsable(TipoResponsable tipo, Long empleadoId) {

    public static final Responsable CLIENTE = new Responsable(TipoResponsable.CLIENTE, null);
    public static final Responsable CANAL = new Responsable(TipoResponsable.CANAL, null);
    public static final Responsable STRIPE = new Responsable(TipoResponsable.STRIPE, null);
    public static final Responsable SISTEMA = new Responsable(TipoResponsable.SISTEMA, null);
    public static final Responsable HUESPED = new Responsable(TipoResponsable.HUESPED, null);

    public static Responsable empleado(long empleadoId) {
        return new Responsable(TipoResponsable.EMPLEADO, empleadoId);
    }
}
