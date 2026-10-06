package com.villaserena.api.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.CanalReserva;

/**
 * Autentica a un canal externo por los encabezados {@code X-Canal-Codigo} y
 * {@code X-Canal-Clave} (RN-CM-001).
 * <p>
 * No usa JWT a propósito: quien llama es otro sistema, no una persona, y el
 * contrato define esta autenticación por clave. La ruta está en
 * {@code RUTAS_PUBLICAS} de SecurityConfig porque Spring Security no la revisa;
 * la revisa esta clase.
 * <p>
 * Canal inexistente y clave incorrecta responden lo mismo (401
 * {@code CANAL_NO_AUTORIZADO}) para no revelar qué canales existen.
 */
@Service
public class CanalAutenticacion {

    private final CanalRepository canales;

    public CanalAutenticacion(CanalRepository canales) {
        this.canales = canales;
    }

    @Transactional(readOnly = true)
    public CanalReserva autenticar(String codigo, String clave) {
        if (codigo == null || codigo.isBlank() || clave == null || clave.isBlank()) {
            throw noAutorizado();
        }
        Canal canal = canales.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(CanalAutenticacion::noAutorizado);
        if (!iguales(canal.getClaveHash(), sha256(clave))) {
            throw noAutorizado();
        }
        return canalDe(canal.getCodigo());
    }

    private static CanalReserva canalDe(String codigo) {
        try {
            CanalReserva canal = CanalReserva.valueOf(codigo);
            if (!canal.esExterno()) {
                throw noAutorizado();
            }
            return canal;
        } catch (IllegalArgumentException e) {
            // La tabla solo acepta BOOKING y EXPEDIA; esto es por si eso cambiara.
            throw noAutorizado();
        }
    }

    private static ApiException noAutorizado() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CANAL_NO_AUTORIZADO", "Canal o clave incorrectos.");
    }

    static String sha256(String texto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Esta máquina no tiene SHA-256", e);
        }
    }

    /** Comparación de tiempo constante: no deja medir cuánto coincide la clave. */
    private static boolean iguales(String esperado, String recibido) {
        return MessageDigest.isEqual(Optional.ofNullable(esperado).orElse("").getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }
}
