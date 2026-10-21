package com.villaserena.api.reservas.pagos;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Cliente del checkout alojado y verificación Svix sobre el cuerpo original. */
@Component
public class PasarelaRecurrenteClient implements PasarelaPagos {
    private final PropiedadesVillaSerena.Recurrente config;
    private final RestClient cliente;
    private final ObjectMapper json;
    private final Clock reloj;

    @org.springframework.beans.factory.annotation.Autowired
    public PasarelaRecurrenteClient(PropiedadesVillaSerena propiedades, ObjectMapper json, Clock reloj) {
        this(propiedades.recurrente(), clienteReal(), json, reloj);
    }

    PasarelaRecurrenteClient(PropiedadesVillaSerena.Recurrente config, RestClient cliente,
            ObjectMapper json, Clock reloj) {
        this.config = config;
        this.cliente = cliente;
        this.json = json;
        this.reloj = reloj;
    }

    private static RestClient clienteReal() {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://app.recurrente.com/api").requestFactory(factory).build();
    }

    @Override
    public Sesion crearSesion(String descripcion, BigDecimal monto, String urlRetorno, Instant expiraEn,
            Map<String, String> metadata) {
        var item = Map.of("name", descripcion, "amount_in_cents", centavos(monto), "currency", "GTQ",
                "quantity", 1, "charge_type", "one_time", "payment_method_types", List.of("card"),
                "available_installments", List.of());
        return mapear(solicitar(HttpMethod.POST, "/checkouts", Map.of("items", List.of(item),
                "success_url", urlRetorno, "cancel_url", urlRetorno, "expires_at", expiraEn.toString(),
                "metadata", metadata), null));
    }

    @Override
    public Sesion obtenerSesion(String id) {
        return mapear(solicitar(HttpMethod.GET, "/checkouts/{id}", null, id));
    }

    @Override
    public Sesion expirarSesion(String id) {
        Sesion actual = obtenerSesion(id);
        if (!actual.abierta()) {
            // No liberar inventario mientras el procesador todavía puede completar el pago.
            if ("payment_in_progress".equals(actual.estado())) {
                throw new ErrorPasarela("El pago sigue en proceso; se consultará nuevamente.");
            }
            return actual;
        }
        solicitar(HttpMethod.PATCH, "/checkouts/{id}", Map.of("expires_at", reloj.instant().toString()), id);
        Sesion vencida = obtenerSesion(id);
        if (!vencida.pagada() && !"expired".equals(vencida.estado())) {
            throw new ErrorPasarela("Recurrente todavía no confirmó el vencimiento del checkout.");
        }
        return vencida;
    }

    @Override
    public void reembolsar(String pagoExternoId) {
        if (pagoExternoId == null || pagoExternoId.isBlank()) {
            throw new ErrorPasarela("El pago no tiene un identificador para solicitar su reembolso.");
        }
        JsonNode respuesta = solicitar(HttpMethod.POST, "/refunds", Map.of("intent_id", pagoExternoId), null);
        if (!"succeeded".equals(texto(respuesta, "status"))) {
            throw new ErrorPasarela("Recurrente no ha confirmado el reembolso; la reserva no se canceló.");
        }
    }

    @Override
    public Evento verificarEvento(String cuerpo, Map<String, String> cabeceras) {
        verificarFirma(cuerpo, cabeceras);
        try {
            JsonNode evento = json.readTree(cuerpo);
            String tipo = texto(evento, "event_type");
            if (tipo == null) tipo = texto(evento, "type");
            if (tipo == null || tipo.isBlank()) throw new IllegalArgumentException();
            Sesion sesion = null;
            if ("intent.succeeded".equals(tipo) || "checkout.expired".equals(tipo)) {
                String checkoutId = texto(evento.path("checkout"), "id");
                if (checkoutId == null || checkoutId.isBlank()) throw new IllegalArgumentException();
                // La redirección y el evento no sustituyen la consulta del estado del checkout.
                sesion = obtenerSesion(checkoutId);
                if ("intent.succeeded".equals(tipo) && !sesion.pagada()) {
                    throw new ErrorPasarela("El checkout todavía no confirma el pago; el aviso debe reintentarse.");
                }
                if ("checkout.expired".equals(tipo) && !"expired".equals(sesion.estado())) {
                    throw new IllegalArgumentException();
                }
            }
            return new Evento(cabeceras.get("svix-id"), tipo, sesion);
        } catch (ErrorPasarela e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EVENTO_INVALIDO", "El aviso de Recurrente no es válido.");
        }
    }

    private void verificarFirma(String cuerpo, Map<String, String> cabeceras) {
        String secreto = requerido(config.webhookSecreto(), "RECURRENTE_WEBHOOK_SECRET");
        try {
            String id = cabeceras.get("svix-id");
            String timestamp = cabeceras.get("svix-timestamp");
            String firmas = cabeceras.get("svix-signature");
            if (id == null || id.isBlank() || timestamp == null || firmas == null) throw new IllegalArgumentException();
            Instant enviado = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(enviado, reloj.instant()).abs().compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException();
            }
            byte[] llave = Base64.getDecoder().decode(secreto.startsWith("whsec_") ? secreto.substring(6) : secreto);
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(llave, "HmacSHA256"));
            byte[] esperada = hmac.doFinal((id + "." + timestamp + "." + cuerpo).getBytes(StandardCharsets.UTF_8));
            for (String firma : firmas.split("\\s+")) {
                if (firma.startsWith("v1,") && MessageDigest.isEqual(esperada, Base64.getDecoder().decode(firma.substring(3)))) {
                    return;
                }
            }
        } catch (Exception e) {
            throw firmaInvalida();
        }
        throw firmaInvalida();
    }

    private JsonNode solicitar(HttpMethod metodo, String ruta, Object cuerpo, String id) {
        try {
            var solicitud = cliente.method(metodo).uri(ruta, id == null ? new Object[0] : new Object[]{id})
                    .header("X-SECRET-KEY", requerido(config.claveSecreta(), "RECURRENTE_SECRET_KEY"))
                    .contentType(MediaType.APPLICATION_JSON);
            if ("/refunds".equals(ruta)) {
                solicitud.header("Idempotency-Key", "reembolso-total-" + ((Map<?, ?>) cuerpo).get("intent_id"));
            }
            if (cuerpo != null) solicitud.body(cuerpo);
            String respuesta = solicitud.retrieve().body(String.class);
            if (respuesta == null) throw new ErrorPasarela("Recurrente respondió sin datos.");
            return json.readTree(respuesta);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            // No registrar el cuerpo del proveedor, cabeceras ni datos personales.
            throw new ErrorPasarela("No se pudo completar la operación con Recurrente. Intenta de nuevo.");
        }
    }

    private static Sesion mapear(JsonNode checkout) {
        String id = texto(checkout, "id");
        String estado = texto(checkout, "status");
        if (id == null || estado == null || !List.of("unpaid", "paid", "expired", "payment_in_progress").contains(estado)) {
            throw new ErrorPasarela("Recurrente devolvió un checkout inválido.");
        }
        String vencimiento = texto(checkout, "expires_at");
        String intentId = texto(checkout.path("latest_intent"), "id");
        String moneda = texto(checkout, "currency");
        JsonNode centavos = checkout.get("total_in_cents");
        BigDecimal monto = centavos == null || !centavos.isIntegralNumber() ? null
                : BigDecimal.valueOf(centavos.asLong(), 2);
        if ("paid".equals(estado) && (monto == null || !"GTQ".equals(moneda) || intentId == null)) {
            throw new ErrorPasarela("El checkout pagado no contiene los datos necesarios para conciliarlo.");
        }
        try {
            return new Sesion(id, texto(checkout, "checkout_url"), estado, "paid".equals(estado), intentId,
                    vencimiento == null ? null : Instant.parse(vencimiento), monto, moneda);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ErrorPasarela("Recurrente devolvió un vencimiento inválido.");
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    static long centavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact();
    }

    private static String requerido(String valor, String variable) {
        if (valor == null || valor.isBlank()) throw new ErrorPasarela("Falta " + variable + " en el entorno del API.");
        return valor;
    }

    private static ApiException firmaInvalida() {
        return new ApiException(HttpStatus.BAD_REQUEST, "FIRMA_INVALIDA", "La firma del aviso de Recurrente no es válida.");
    }
}
