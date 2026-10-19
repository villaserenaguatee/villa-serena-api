package com.villaserena.api.reservas.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.villaserena.api.reservas.EstadoPago;
import com.villaserena.api.reservas.Pago;
import com.villaserena.api.reservas.dto.ProcesoPago.AccionPago;
import com.villaserena.api.reservas.dto.ProcesoPago.EstadoCheckout;

class ConsultaProcesoPagoTest {
    private static final Instant AHORA = Instant.parse("2026-10-05T18:00:00Z");
    private final PasarelaPagos pasarela = mock(PasarelaPagos.class);
    private final ConsultaProcesoPago consulta = new ConsultaProcesoPago(pasarela, Clock.fixed(AHORA, ZoneOffset.UTC));

    static Stream<Arguments> estados() {
        return Stream.of(
                Arguments.of("unpaid", false, "ABIERTO", "CONTINUAR", false),
                Arguments.of("payment_in_progress", false, "PROCESANDO", "ESPERAR", false),
                Arguments.of("paid", true, "PAGADO", "ESPERAR", false),
                Arguments.of("expired", false, "VENCIDO", "REINTENTAR", true));
    }

    @ParameterizedTest
    @MethodSource("estados")
    void distingueAccionesSinAprobarElPago(String externo, boolean pagada, String estado, String accion, boolean reintento) {
        Pago pago = pendiente();
        when(pasarela.obtenerSesion("ch_prueba")).thenReturn(new PasarelaPagos.Sesion("ch_prueba", "https://pago.test",
                externo, pagada, null, AHORA.plusSeconds(600)));
        var resultado = consulta.consultar(Optional.of(pago), true, AHORA.plusSeconds(1800));
        assertThat(resultado.estadoCheckout().name()).isEqualTo(estado);
        assertThat(resultado.accionPago().name()).isEqualTo(accion);
        assertThat(resultado.puedeReintentar()).isEqualTo(reintento);
        assertThat(resultado.urlPago()).isEqualTo("CONTINUAR".equals(accion) ? "https://pago.test" : null);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        verify(pasarela, never()).crearSesion(any(), any(), any(), any(), any());
    }

    @Test
    void errorDePasarelaNoHabilitaOtroCobro() {
        when(pasarela.obtenerSesion("ch_prueba")).thenThrow(new ErrorPasarela("No disponible"));
        var resultado = consulta.consultar(Optional.of(pendiente()), true, AHORA.plusSeconds(1800));
        assertThat(resultado.estadoCheckout()).isEqualTo(EstadoCheckout.NO_VERIFICADO);
        assertThat(resultado.accionPago()).isEqualTo(AccionPago.ESPERAR);
        assertThat(resultado.puedeReintentar()).isFalse();
    }

    @Test
    void plazoVencidoNoPermiteIniciarNiReintentar() {
        assertThat(consulta.consultar(Optional.empty(), true, AHORA).accionPago()).isEqualTo(AccionPago.NINGUNA);
        Pago fallido = pendiente(); fallido.setEstado(EstadoPago.FALLIDO);
        var resultado = consulta.consultar(Optional.of(fallido), true, AHORA);
        assertThat(resultado.accionPago()).isEqualTo(AccionPago.NINGUNA);
        assertThat(resultado.puedeReintentar()).isFalse();
        verifyNoInteractions(pasarela);
    }

    @Test
    void sinIntentoVigentePermiteIniciarPeroNoLoConfundeConReintentar() {
        var resultado = consulta.consultar(Optional.empty(), true, AHORA.plusSeconds(1800));
        assertThat(resultado.accionPago()).isEqualTo(AccionPago.INICIAR);
        assertThat(resultado.estadoCheckout()).isEqualTo(EstadoCheckout.SIN_CHECKOUT);
        assertThat(resultado.puedeReintentar()).isFalse();
    }

    @Test
    void sesionUnpaidConFechaVencidaNoSeOfreceParaContinuarNiDuplicar() {
        when(pasarela.obtenerSesion("ch_prueba")).thenReturn(new PasarelaPagos.Sesion("ch_prueba", "https://pago.test",
                "unpaid", false, null, AHORA));
        var resultado = consulta.consultar(Optional.of(pendiente()), true, AHORA.plusSeconds(1800));
        assertThat(resultado.estadoCheckout()).isEqualTo(EstadoCheckout.NO_VERIFICADO);
        assertThat(resultado.accionPago()).isEqualTo(AccionPago.ESPERAR);
        assertThat(resultado.urlPago()).isNull();
    }

    private Pago pendiente() {
        Pago pago = new Pago(); pago.setEstado(EstadoPago.PENDIENTE); pago.setCheckoutId("ch_prueba"); return pago;
    }

    @Test
    void checkoutAunAbiertoFueraDelPlazoDeReservaNoOfreceContinuar() {
        when(pasarela.obtenerSesion("ch_prueba")).thenReturn(new PasarelaPagos.Sesion("ch_prueba", "https://pago.test",
                "unpaid", false, null, AHORA.plusSeconds(600)));
        var resultado = consulta.consultar(Optional.of(pendiente()), true, AHORA);
        assertThat(resultado.estadoCheckout()).isEqualTo(EstadoCheckout.ABIERTO);
        assertThat(resultado.accionPago()).isEqualTo(AccionPago.NINGUNA);
        assertThat(resultado.urlPago()).isNull();
        assertThat(resultado.puedeReintentar()).isFalse();
    }
}
