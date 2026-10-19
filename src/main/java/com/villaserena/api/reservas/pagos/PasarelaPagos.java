package com.villaserena.api.reservas.pagos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Contrato de pagos en línea independiente del proveedor.
 */
public interface PasarelaPagos {

    /** Checkout alojado; los estados se normalizan en el adaptador. */
    record Sesion(String id, String url, String estado, boolean pagada, String pagoExternoId, Instant expiraEn,
            BigDecimal monto, String moneda) {

        public Sesion(String id, String url, String estado, boolean pagada, String pagoExternoId, Instant expiraEn) {
            this(id, url, estado, pagada, pagoExternoId, expiraEn, null, null);
        }

        public boolean abierta() {
            return "unpaid".equals(estado);
        }
    }

    /** Evento del webhook ya verificado: solo interesan su id, su tipo y la sesión. */
    record Evento(String id, String tipo, Sesion sesion) {
    }

    Sesion crearSesion(String descripcion, BigDecimal monto, String urlRetorno, Instant expiraEn,
            Map<String, String> metadata);

    Sesion obtenerSesion(String sesionId);

    /** Vence una sesión abierta para que ya no se pueda pagar. Devuelve la sesión actualizada. */
    Sesion expirarSesion(String sesionId);

    /** Reembolso total del pago (RN-CAN-008). Lanza ErrorPasarela si Recurrente lo rechaza. */
    void reembolsar(String pagoExternoId);

    /** Verifica la firma del webhook (RECURRENTE_WEBHOOK_SECRET). Lanza ApiException FIRMA_INVALIDA si no es válida. */
    Evento verificarEvento(String cuerpo, Map<String, String> cabeceras);
}
