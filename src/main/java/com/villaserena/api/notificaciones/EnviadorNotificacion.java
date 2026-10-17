package com.villaserena.api.notificaciones;

import java.util.Map;

/**
 * Quien sabe entregar un tipo de aviso. Hay uno para correo
 * ({@link EnviadorCorreo}); para las push de OBJ-3A-2 basta con agregar otro
 * {@code @Component} que soporte los tipos {@code PUSH_*}: {@link OutboxJob} lo
 * encuentra solo y no hay que tocarlo.
 */
public interface EnviadorNotificacion {

    boolean soporta(TipoNotificacion tipo);

    /**
     * Entrega el aviso. Si lanza una excepción, el Outbox suma un intento y lo
     * reintenta más tarde.
     *
     * @param datos el {@code payload} del aviso ya convertido de JSON
     */
    void enviar(Outbox aviso, Map<String, Object> datos);
}
