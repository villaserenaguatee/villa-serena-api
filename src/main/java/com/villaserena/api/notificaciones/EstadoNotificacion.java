package com.villaserena.api.notificaciones;

/** Estado de un registro del Outbox ({@code ck_outbox_estado} de V5). */
public enum EstadoNotificacion {

    /** Todavía no se envía; el proceso programado lo intentará. */
    PENDIENTE,

    /** Salió correctamente. */
    ENVIADO,

    /** Se agotaron los intentos; queda el último error para revisarlo. */
    FALLIDO
}
