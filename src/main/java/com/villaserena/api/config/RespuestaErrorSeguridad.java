package com.villaserena.api.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.villaserena.api.comun.ErrorRespuesta;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/** Escribe los errores de los filtros de seguridad con el formato único del API. */
@Component
public class RespuestaErrorSeguridad {

    private final ObjectMapper objectMapper;

    public RespuestaErrorSeguridad(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escribir(HttpServletResponse respuesta, int estado, String codigo, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(respuesta.getOutputStream(), ErrorRespuesta.de(codigo, mensaje));
    }
}
