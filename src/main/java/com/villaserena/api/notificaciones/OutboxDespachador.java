package com.villaserena.api.notificaciones;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.config.PropiedadesVillaSerena;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Envía los avisos del Outbox, uno por transacción (RN-NOT-003).
 * <p>
 * Está separado de {@link OutboxJob} a propósito: {@code @Transactional} solo
 * funciona cuando la llamada entra desde fuera del objeto, así que el reloj
 * (el job) y el trabajo (esta clase) tienen que ser dos beans distintos.
 * <p>
 * A los {@code intentos-maximos} intentos el aviso queda FALLIDO con el último
 * error guardado, para no reintentar para siempre un correo que nunca va a salir
 * (una dirección mal escrita, por ejemplo).
 */
@Service
public class OutboxDespachador {

    private static final Logger log = LoggerFactory.getLogger(OutboxDespachador.class);
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final OutboxRepository outbox;
    private final List<EnviadorNotificacion> enviadores;
    private final ObjectMapper json;
    private final Clock reloj;
    private final int intentosMaximos;
    private final Duration espera;
    private final Limit lote;

    public OutboxDespachador(OutboxRepository outbox, List<EnviadorNotificacion> enviadores, ObjectMapper json,
            Clock reloj, PropiedadesVillaSerena propiedades) {
        this.outbox = outbox;
        this.enviadores = enviadores;
        this.json = json;
        this.reloj = reloj;
        PropiedadesVillaSerena.Notificaciones config = propiedades.notificaciones();
        this.intentosMaximos = config.intentosMaximos();
        this.espera = Duration.ofMinutes(config.esperaMinutos());
        this.lote = Limit.of(config.lote());
    }

    /** Envía los pendientes que ya toca intentar y devuelve cuántos salieron. */
    public int enviarPendientes() {
        int enviados = 0;
        for (Long id : pendientes()) {
            try {
                if (procesar(id)) {
                    enviados++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo procesar el aviso {} del outbox: {}", id, e.getMessage());
            }
        }
        return enviados;
    }

    /**
     * Solo los identificadores: así cada aviso se vuelve a leer en su propia
     * transacción y el bloqueo de la lista no se sostiene durante los envíos.
     */
    @Transactional(readOnly = true)
    public List<Long> pendientes() {
        return outbox.tomarPendientes(reloj.instant(), lote).stream().map(Outbox::getId).toList();
    }

    /** @return {@code true} si el aviso salió en este intento */
    @Transactional
    public boolean procesar(long id) {
        Outbox aviso = outbox.findById(id).orElse(null);
        if (aviso == null || aviso.getEstado() != EstadoNotificacion.PENDIENTE) {
            return false;
        }
        aviso.setIntentos(aviso.getIntentos() + 1);
        boolean enviado = false;
        try {
            EnviadorNotificacion enviador = enviadores.stream()
                    .filter(e -> e.soporta(aviso.getTipo()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Todavía no hay quién envíe los avisos de tipo " + aviso.getTipo()));
            enviador.enviar(aviso, json.readValue(aviso.getPayload(), MAPA));
            aviso.setEstado(EstadoNotificacion.ENVIADO);
            aviso.setEnviadoEn(reloj.instant());
            aviso.setUltimoError(null);
            enviado = true;
            log.info("Aviso {} ({}) enviado a {}", aviso.getId(), aviso.getTipo(), aviso.getDestinatario());
        } catch (RuntimeException e) {
            aviso.setUltimoError(recortar(e));
            if (aviso.getIntentos() >= intentosMaximos) {
                aviso.setEstado(EstadoNotificacion.FALLIDO);
                log.error("Aviso {} ({}) FALLIDO tras {} intentos: {}", aviso.getId(), aviso.getTipo(),
                        aviso.getIntentos(), aviso.getUltimoError());
            } else {
                // Cada intento espera un poco más que el anterior.
                aviso.setProximoIntentoEn(reloj.instant().plus(espera.multipliedBy(aviso.getIntentos())));
                log.warn("Aviso {} ({}) falló en el intento {}; se reintenta: {}", aviso.getId(), aviso.getTipo(),
                        aviso.getIntentos(), aviso.getUltimoError());
            }
        }
        outbox.save(aviso);
        return enviado;
    }

    private static String recortar(RuntimeException e) {
        String mensaje = e.getClass().getSimpleName() + ": " + e.getMessage();
        return mensaje.length() > 1000 ? mensaje.substring(0, 1000) : mensaje;
    }
}
