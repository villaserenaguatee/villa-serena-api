package com.villaserena.api.notificaciones;

/**
 * Tipos de aviso que acepta la tabla {@code outbox} (restricción
 * {@code ck_outbox_tipo} de V5). Los de correo los envía {@link EnviadorCorreo};
 * los de push quedan listos para OBJ-3A-2 (Carlos).
 */
public enum TipoNotificacion {

    CORREO_CONFIRMACION,
    CORREO_OTP,
    CORREO_FACTURA,
    PUSH_PEDIDO_ENTREGADO,
    PUSH_SOLICITUD_ATENDIDA;

    public boolean esCorreo() {
        return name().startsWith("CORREO_");
    }
}
