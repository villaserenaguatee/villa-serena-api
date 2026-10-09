package com.villaserena.api.app;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.notificaciones.EnviadorNotificacion;
import com.villaserena.api.notificaciones.Outbox;
import com.villaserena.api.notificaciones.TipoNotificacion;

/**
 * Envía las push por la API de Expo (HU-HUE-17). Se suma al Outbox que ya existe:
 * los reintentos, el conteo de intentos y el estado FALLIDO son los mismos de los
 * correos, no hay un segundo mecanismo.
 * <p>
 * Dos reglas del documento 10 se aplican aquí, no en quien encola:
 * <ul>
 * <li>Solo se envía mientras la reserva está EN_ESTADIA (RN-NOT-001). Entre que se
 * encola y se envía puede pasar el check-out, y entonces ya no corresponde
 * molestar al huésped.</li>
 * <li>El texto no lleva nombres, montos ni datos de pago (RN-NOT-004); el payload
 * solo trae lo necesario para abrir la pantalla.</li>
 * </ul>
 */
@Component
public class EnviadorPush implements EnviadorNotificacion {

    private static final Logger log = LoggerFactory.getLogger(EnviadorPush.class);

    private final RestClient expo;
    private final JdbcTemplate jdbc;

    public EnviadorPush(JdbcTemplate jdbc, PropiedadesVillaSerena propiedades) {
        this.expo = RestClient.create(propiedades.notificaciones().expoPushUrl());
        this.jdbc = jdbc;
    }

    @Override
    public boolean soporta(TipoNotificacion tipo) {
        return !tipo.esCorreo();
    }

    @Override
    public void enviar(Outbox aviso, Map<String, Object> datos) {
        String codigoReserva = String.valueOf(datos.get("codigoReserva"));
        if (!enEstadia(codigoReserva)) {
            log.info("La reserva {} ya no está en estadía; no se envía la push {}", codigoReserva, aviso.getTipo());
            return;
        }
        Map<String, Object> mensaje = Map.of(
                "to", aviso.getDestinatario(),
                "title", "Villa Serena",
                "body", cuerpo(aviso.getTipo()),
                "data", datos);
        // Expo acepta un arreglo de mensajes; se manda uno por aviso para que un
        // fallo no arrastre a los demás.
        expo.post().body(List.of(mensaje)).retrieve().toBodilessEntity();
    }

    private boolean enEstadia(String codigoReserva) {
        Integer enEstadia = jdbc.queryForObject(
                "SELECT count(*) FROM reservas WHERE codigo = ? AND estado = 'EN_ESTADIA'", Integer.class,
                codigoReserva);
        return enEstadia != null && enEstadia > 0;
    }

    private static String cuerpo(TipoNotificacion tipo) {
        return switch (tipo) {
            case PUSH_PEDIDO_ENTREGADO -> "Tu pedido fue entregado";
            case PUSH_SOLICITUD_ATENDIDA -> "Tu solicitud fue atendida";
            default -> throw new IllegalStateException("Sin texto de push para " + tipo);
        };
    }
}
