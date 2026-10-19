package com.villaserena.api.reservas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.dto.ReservaWebCreada;
import tools.jackson.databind.ObjectMapper;

/** La reserva, su respuesta y la prueba de titularidad se confirman en la misma transacción. */
@Service
public class IdempotenciaReservas {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final VerificacionCorreoReserva verificacion;

    public IdempotenciaReservas(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager,
            VerificacionCorreoReserva verificacion) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
        this.verificacion = verificacion;
    }

    public <T> T ejecutar(String alcance, String clave, Object peticion, Class<T> tipo, Supplier<T> crear) {
        if (clave == null) return crear.get(); // Compatibilidad de Recepción.
        return ejecutar(alcance, clave, peticion, tipo, crear, null, null, null);
    }

    public ReservaWebCreada ejecutarPublico(String alcance, String clave, Object peticion, String correo,
            String draft, String receipt, Supplier<ReservaWebCreada> crear) {
        return ejecutar(alcance, clave, peticion, ReservaWebCreada.class, crear, correo, draft, receipt);
    }

    private <T> T ejecutar(String alcance, String clave, Object peticion, Class<T> tipo, Supplier<T> crear,
            String correo, String draft, String receipt) {
        validarClave(clave);
        final String huella;
        try {
            huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(peticion).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return tx.execute(status -> {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", alcance + ":" + clave);
            var rows = jdbc.queryForList("SELECT huella,respuesta::text AS respuesta,verificacion_id "
                    + "FROM reservas_solicitudes WHERE alcance=? AND clave=?", alcance, clave);
            if (!rows.isEmpty()) {
                var fila = rows.getFirst();
                // Los intentos históricos conservan el replay previo; no habilitan la nueva consulta.
                if (correo != null && fila.get("verificacion_id") != null
                        && !verificacion.autorizaRecuperacion((UUID) fila.get("verificacion_id"), draft, receipt)) {
                    throw ApiException.noEncontrado();
                }
                if (!huella.equals(fila.get("huella"))) {
                    throw ApiException.conflicto("BOOKING_REQUEST_CONFLICT", "Esta solicitud ya se utilizó con otros datos.");
                }
                return json.readValue((String) fila.get("respuesta"), tipo);
            }
            UUID prueba = correo == null ? null : verificacion.consumir(correo, draft, receipt);
            T resultado = crear.get();
            jdbc.update("INSERT INTO reservas_solicitudes(alcance,clave,huella,respuesta,verificacion_id) "
                    + "VALUES(?,?,?,?::jsonb,?)", alcance, clave, huella, json.writeValueAsString(resultado), prueba);
            return resultado;
        });
    }

    /** Consulta sin efectos: nunca ejecuta la creación ni devuelve la respuesta con datos del huésped. */
    public String recuperarCodigo(String alcance, String clave, String draft, String receipt) {
        validarClave(clave);
        return tx.execute(status -> {
            boolean libre = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))", Boolean.class, alcance + ":" + clave));
            if (!libre) throw ApiException.conflicto("SOLICITUD_EN_PROCESO",
                    "La creación sigue en proceso. Conserva el identificador y consulta de nuevo.");
            var rows = jdbc.queryForList("SELECT respuesta->>'codigo' AS codigo,verificacion_id "
                    + "FROM reservas_solicitudes WHERE alcance=? AND clave=?", alcance, clave);
            if (rows.isEmpty() || !verificacion.autorizaRecuperacion(
                    (UUID) rows.getFirst().get("verificacion_id"), draft, receipt)) {
                throw ApiException.noEncontrado();
            }
            return (String) rows.getFirst().get("codigo");
        });
    }

    private static void validarClave(String clave) {
        if (clave == null || !clave.matches("[a-zA-Z0-9_-]{16,100}")) {
            throw ApiException.datosInvalidos("Identificador de solicitud inválido.", java.util.List.of());
        }
    }
}
