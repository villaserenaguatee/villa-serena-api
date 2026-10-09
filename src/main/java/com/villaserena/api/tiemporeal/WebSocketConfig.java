package com.villaserena.api.tiemporeal;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.villaserena.api.config.PropiedadesVillaSerena;

/**
 * WebSocket con STOMP en {@code /ws} (documento 14, sección 6). Agente de mensajes
 * en memoria: para el hito local basta y no agrega infraestructura.
 * <p>
 * La ruta {@code /ws} no la protege Spring Security por HTTP: la autenticación va en
 * el mensaje CONNECT, y de eso se encarga {@link AutenticacionStomp}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AutenticacionStomp autenticacion;
    private final String[] origenes;

    public WebSocketConfig(AutenticacionStomp autenticacion, PropiedadesVillaSerena propiedades) {
        this.autenticacion = autenticacion;
        this.origenes = propiedades.cors().origenes().toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws").setAllowedOrigins(origenes);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue");
        registro.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(autenticacion);
    }
}
