package com.villaserena.api.notificaciones;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.villaserena.api.comun.ApiException;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/** Configuración interna del buzón operativo; no deriva del correo público del hotel. */
@Component
public class ContactoCorreoConfig {
    private final String destinatario;

    public ContactoCorreoConfig(@Value("${villa-serena.contacto.destinatario:}") String valor) {
        destinatario = validar(valor);
        if (destinatario == null) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Contacto web deshabilitado: configura villa-serena.contacto.destinatario con una única dirección válida.");
        }
    }

    private static String validar(String valor) {
        if (valor == null || valor.indexOf('\r') >= 0 || valor.indexOf('\n') >= 0) return null;
        String direccion = valor.trim();
        if (direccion.isEmpty() || direccion.length() > 254) return null;
        try {
            InternetAddress buzon = new InternetAddress(direccion, true);
            buzon.validate();
            return !buzon.isGroup() && buzon.getPersonal() == null && direccion.equals(buzon.getAddress())
                    ? direccion : null;
        } catch (AddressException invalida) {
            return null;
        }
    }

    public String exigirDestinatario() {
        if (destinatario == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTACTO_NO_DISPONIBLE",
                    "El envío de mensajes no está disponible temporalmente.");
        }
        return destinatario;
    }
}
