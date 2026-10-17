package com.villaserena.api.tiemporeal;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.villaserena.api.auth.TipoUsuario;
import com.villaserena.api.config.JwtConfig;

/**
 * Seguridad del WebSocket: autentica en el CONNECT y autoriza cada SUBSCRIBE.
 * <p>
 * Dos formas de entrar, según el contrato:
 * <ul>
 * <li>la app manda el JWT del huésped en el encabezado {@code Authorization};</li>
 * <li>la web manda un {@code ticket} de un solo uso y 60 segundos, porque el
 * navegador nunca tiene el JWT del empleado.</li>
 * </ul>
 * Además bloquea los SEND del cliente a {@code /topic} y {@code /user}: el tiempo
 * real es de ida, del servidor a las pantallas. Sin esto, cualquiera con una sesión
 * podría publicar un pedido falso en la cola de Room Service.
 * <p>
 * El nombre del Principal es el id del usuario, y es lo que resuelve la cola
 * privada {@code /user/queue/pedidos}: el cliente no puede pedir los pedidos de otro.
 */
@Component
public class AutenticacionStomp implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AutenticacionStomp.class);

    private final JwtDecoder jwtDecoder;
    private final WsTicketService tickets;
    private final AutorizacionSuscripciones autorizacion;

    public AutenticacionStomp(JwtDecoder jwtDecoder, WsTicketService tickets,
            AutorizacionSuscripciones autorizacion) {
        this.jwtDecoder = jwtDecoder;
        this.tickets = tickets;
        this.autorizacion = autorizacion;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || cabeceras.getCommand() == null) {
            return mensaje;
        }
        switch (cabeceras.getCommand()) {
            case CONNECT -> cabeceras.setUser(autenticar(cabeceras));
            case SUBSCRIBE -> autorizar(cabeceras);
            case SEND -> rechazarEnvios(cabeceras);
            default -> {
                // DISCONNECT y los demás no necesitan revisión.
            }
        }
        return mensaje;
    }

    private Authentication autenticar(StompHeaderAccessor cabeceras) {
        String autorizacionHeader = primero(cabeceras, "Authorization");
        if (autorizacionHeader != null && autorizacionHeader.startsWith("Bearer ")) {
            return porJwt(autorizacionHeader.substring(7));
        }
        return porTicket(primero(cabeceras, "ticket"));
    }

    private Authentication porJwt(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.debug("CONNECT rechazado: JWT inválido");
            throw noAutenticado();
        }
        if (!TipoUsuario.HUESPED.name().equals(jwt.getClaimAsString(JwtConfig.CLAIM_TIPO))) {
            // El personal entra por ticket; su JWT no sale del BFF.
            throw noAutenticado();
        }
        return autenticacion(jwt.getSubject(), jwt.getClaimAsString(JwtConfig.CLAIM_ROL), null);
    }

    private Authentication porTicket(String ticket) {
        WsTicketService.Emitido emitido = tickets.consumir(ticket).orElseThrow(() -> {
            log.debug("CONNECT rechazado: ticket inexistente, vencido o ya usado");
            return noAutenticado();
        });
        return autenticacion(String.valueOf(emitido.empleadoId()), emitido.rol(), emitido.area());
    }

    private static Authentication autenticacion(String id, String rol, String area) {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        if (rol != null) {
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
        }
        if (area != null) {
            autoridades.add(new SimpleGrantedAuthority("AREA_" + area));
        }
        return new UsernamePasswordAuthenticationToken(id, null, autoridades);
    }

    private void autorizar(StompHeaderAccessor cabeceras) {
        Authentication usuario = cabeceras.getUser() instanceof Authentication auth ? auth : null;
        if (!autorizacion.puedeSuscribirse(usuario, cabeceras.getDestination())) {
            log.debug("SUBSCRIBE rechazado a {}", cabeceras.getDestination());
            throw new org.springframework.security.access.AccessDeniedException(
                    "No puedes suscribirte a " + cabeceras.getDestination());
        }
    }

    private static void rechazarEnvios(StompHeaderAccessor cabeceras) {
        String destino = cabeceras.getDestination();
        if (destino != null && (destino.startsWith("/topic") || destino.startsWith("/user")
                || destino.startsWith("/queue"))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El cliente no publica en " + destino);
        }
    }

    private static String primero(StompHeaderAccessor cabeceras, String nombre) {
        List<String> valores = cabeceras.getNativeHeader(nombre);
        return valores == null || valores.isEmpty() ? null : valores.getFirst();
    }

    private static org.springframework.security.core.AuthenticationException noAutenticado() {
        return new org.springframework.security.authentication.BadCredentialsException(
                "Credenciales inválidas para el WebSocket.");
    }
}
