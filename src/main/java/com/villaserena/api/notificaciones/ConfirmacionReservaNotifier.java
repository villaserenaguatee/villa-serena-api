package com.villaserena.api.notificaciones;

/**
 * Aviso de reserva confirmada (HU-HUE-07). Lo implementa Hugo con el Outbox y el
 * correo de confirmación (OBJ-1C). Se llama dentro de la transacción que confirma la
 * reserva: el Outbox guarda el correo y lo envía después, con reintentos.
 * <p>
 * Mientras no exista una implementación, las reservas se confirman igual y no se
 * envía correo.
 */
public interface ConfirmacionReservaNotifier {

    void notificar(long reservaId);
}
