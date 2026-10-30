package com.villaserena.api.reservas.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;

import tools.jackson.databind.json.JsonMapper;

class PasarelaRecurrenteClientTest {
    private static final Instant AHORA = Instant.parse("2026-10-18T18:00:00Z");
    private static final String SECRETO = "whsec_" + Base64.getEncoder()
            .encodeToString("secreto-ficticio-solo-pruebas".getBytes(StandardCharsets.UTF_8));
    private MockRestServiceServer servidor;
    private PasarelaRecurrenteClient cliente;

    @BeforeEach
    void preparar() {
        var builder = RestClient.builder().baseUrl("https://app.recurrente.com/api");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new PasarelaRecurrenteClient(new PropiedadesVillaSerena.Recurrente(
                "pk_test_ficticia", "sk_test_ficticia", SECRETO, "villaserena://pago-resultado"),
                builder.build(), JsonMapper.builder().build(), Clock.fixed(AHORA, ZoneOffset.UTC),
                "https://hotel.test/villa-serena-logo.png");
    }

    @AfterEach
    void verificarSolicitudes() {
        servidor.verify();
    }

    @ParameterizedTest
    @CsvSource({"VS-PRUEBA,1912.50,191250", "VS-OTRA,850.00,85000"})
    void creaCheckoutEnGtqConMontoExactoYVencimiento(String codigo, String monto, int centavos) {
        servidor.expect(requestTo("https://app.recurrente.com/api/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-SECRET-KEY", "sk_test_ficticia"))
                .andExpect(jsonPath("$.items[0].name").value("Reserva Villa Serena · " + codigo))
                .andExpect(jsonPath("$.items[0].image_url").value("https://hotel.test/villa-serena-logo.png"))
                .andExpect(jsonPath("$.items[0].amount_in_cents").value(centavos))
                .andExpect(jsonPath("$.items[0].currency").value("GTQ"))
                .andExpect(jsonPath("$.items[0].payment_method_types[0]").value("card"))
                .andExpect(jsonPath("$.expires_at").value("2026-10-18T18:30:00Z"))
                .andExpect(jsonPath("$.metadata.codigoReserva").value(codigo))
                .andRespond(withSuccess(checkout("unpaid"), MediaType.APPLICATION_JSON));
        var sesion = cliente.crearSesion("Reserva Villa Serena · " + codigo, new BigDecimal(monto),
                "https://hotel.test/resultado", AHORA.plusSeconds(1800), Map.of("codigoReserva", codigo));
        assertThat(sesion.id()).isEqualTo("ch_prueba");
        assertThat(sesion.abierta()).isTrue();
        assertThat(sesion.url()).isEqualTo("https://app.recurrente.com/checkout-session/ch_prueba");
    }

    @Test
    void firmaValidaConsultaElCheckoutYUsaSvixIdParaIdempotencia() throws Exception {
        String cuerpo = """
                {"type":"payment","event_type":"intent.succeeded","id":"in_prueba",
                 "status":"succeeded","checkout":{"id":"ch_prueba","status":"paid"}}""";
        esperarConsulta("paid");
        var evento = cliente.verificarEvento(cuerpo, firmar(cuerpo, AHORA));
        assertThat(evento.id()).isEqualTo("msg_prueba");
        assertThat(evento.tipo()).isEqualTo("intent.succeeded");
        assertThat(evento.sesion().pagada()).isTrue();
        assertThat(evento.sesion().pagoExternoId()).isEqualTo("in_prueba");
    }

    @Test
    void rechazaCuerpoAlteradoCabecerasAusentesYTimestampFueraDeVentana() throws Exception {
        String cuerpo = "{\"event_type\":\"intent.pending\"}";
        var cabeceras = firmar(cuerpo, AHORA);
        assertFirmaInvalida(cuerpo + " ", cabeceras);
        assertFirmaInvalida(cuerpo, Map.of());
        assertFirmaInvalida(cuerpo, firmar(cuerpo, AHORA.minusSeconds(301)));
        assertFirmaInvalida(cuerpo, firmar(cuerpo, AHORA.plusSeconds(301)));
    }

    @Test
    void aceptaMultiplesFirmasParaRotacionDeSecretos() throws Exception {
        String cuerpo = "{\"event_type\":\"intent.pending\",\"id\":\"in_prueba\"}";
        var firma = firmar(cuerpo, AHORA);
        var evento = cliente.verificarEvento(cuerpo, Map.of("svix-id", firma.get("svix-id"),
                "svix-timestamp", firma.get("svix-timestamp"), "svix-signature", "v1,eA== " + firma.get("svix-signature")));
        assertThat(evento.sesion()).isNull();
    }

    @Test
    void noConfiaEnEstadoPagadoDelWebhookSiCheckoutSigueAbierto() throws Exception {
        String cuerpo = "{\"event_type\":\"intent.succeeded\",\"checkout\":{\"id\":\"ch_prueba\",\"status\":\"paid\"}}";
        esperarConsulta("unpaid");
        assertThatThrownBy(() -> cliente.verificarEvento(cuerpo, firmar(cuerpo, AHORA)))
                .isInstanceOf(ErrorPasarela.class);
    }

    @Test
    void venceCheckoutYCompruebaElEstadoFinal() {
        esperarConsulta("unpaid");
        servidor.expect(requestTo("https://app.recurrente.com/api/checkouts/ch_prueba"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.expires_at").value(AHORA.toString()))
                .andRespond(withSuccess(checkout("expired"), MediaType.APPLICATION_JSON));
        esperarConsulta("expired");
        assertThat(cliente.expirarSesion("ch_prueba").estado()).isEqualTo("expired");
    }

    @Test
    void pagoCompletadoAntesDeVencerNoSeExpira() {
        esperarConsulta("paid");
        assertThat(cliente.expirarSesion("ch_prueba").pagada()).isTrue();
    }

    @Test
    void pagoEnProcesoNoPermiteLiberarLaReserva() {
        esperarConsulta("payment_in_progress");
        assertThatThrownBy(() -> cliente.expirarSesion("ch_prueba")).isInstanceOf(ErrorPasarela.class);
    }

    @Test
    void reembolsoTotalUsaIdUnificadoYLlaveIdempotente() {
        servidor.expect(requestTo("https://app.recurrente.com/api/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "reembolso-total-in_prueba"))
                .andExpect(content().json("{\"intent_id\":\"in_prueba\"}"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        cliente.reembolsar("in_prueba");
    }

    @Test
    void reembolsoPendienteNoSeDaPorCompletado() {
        servidor.expect(requestTo("https://app.recurrente.com/api/refunds"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"pending\"}"));
        assertThatThrownBy(() -> cliente.reembolsar("in_prueba")).isInstanceOf(ErrorPasarela.class);
    }

    @Test
    void errorDelProveedorNoExponeSuRespuesta() {
        servidor.expect(requestTo("https://app.recurrente.com/api/checkouts/ch_prueba"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("detalle confidencial"));
        assertThatThrownBy(() -> cliente.obtenerSesion("ch_prueba")).isInstanceOf(ErrorPasarela.class)
                .hasMessageNotContaining("detalle confidencial");
    }

    private void esperarConsulta(String estado) {
        servidor.expect(requestTo("https://app.recurrente.com/api/checkouts/ch_prueba"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(checkout(estado), MediaType.APPLICATION_JSON));
    }

    private static String checkout(String estado) {
        return """
                {"id":"ch_prueba","status":"%s","latest_intent":{"id":"in_prueba"},
                 "total_in_cents":191250,"currency":"GTQ",
                 "checkout_url":"https://app.recurrente.com/checkout-session/ch_prueba"}""".formatted(estado);
    }

    private static Map<String, String> firmar(String cuerpo, Instant instante) throws Exception {
        String timestamp = Long.toString(instante.getEpochSecond());
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRETO.substring(6)), "HmacSHA256"));
        String firma = Base64.getEncoder().encodeToString(hmac.doFinal(
                ("msg_prueba." + timestamp + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
        return Map.of("svix-id", "msg_prueba", "svix-timestamp", timestamp, "svix-signature", "v1," + firma);
    }

    private void assertFirmaInvalida(String cuerpo, Map<String, String> cabeceras) {
        assertThatThrownBy(() -> cliente.verificarEvento(cuerpo, cabeceras)).isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getMessage()).contains("firma"));
    }
}
