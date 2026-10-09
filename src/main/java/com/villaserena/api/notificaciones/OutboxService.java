package com.villaserena.api.notificaciones;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Guarda los avisos por enviar. El patrón Outbox (documento 14, AD-11) separa
 * "decidir que hay que avisar" de "avisar": aquí solo se escribe una fila en la
 * misma transacción del cambio de estado, y {@link OutboxJob} la envía después.
 * <p>
 * Por eso {@code encolar} exige una transacción abierta ({@code MANDATORY}): si se
 * pudiera llamar sin ella, el aviso quedaría guardado aunque la reserva se
 * deshiciera, y se enviarían correos de reservas que no existen.
 */
@Service
public class OutboxService {

    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxService(OutboxRepository outbox, ObjectMapper json, Clock reloj) {
        this.outbox = outbox;
        this.json = json;
        this.reloj = reloj;
    }

    /**
     * Encola un aviso. Lo usan la confirmación de reserva (OBJ-1C), el OTP
     * (OBJ-3A-2), la factura (OBJ-4B) y las push de pedidos y solicitudes.
     *
     * @param destinatario correo del huésped, o su identificador de dispositivo en las push
     * @param datos        lo que necesita la plantilla; se guarda como JSON
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Outbox encolar(TipoNotificacion tipo, String destinatario, Map<String, Object> datos) {
        Instant ahora = reloj.instant();
        Outbox aviso = new Outbox();
        aviso.setTipo(tipo);
        aviso.setDestinatario(destinatario);
        aviso.setPayload(json.writeValueAsString(datos));
        aviso.setEstado(EstadoNotificacion.PENDIENTE);
        aviso.setProximoIntentoEn(ahora);
        aviso.setCreadoEn(ahora);
        return outbox.save(aviso);
    }
}
