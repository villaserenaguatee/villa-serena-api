package com.villaserena.api.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.huespedes.HuespedDatos;
import com.villaserena.api.huespedes.TipoDocumento;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Pruebas de OBJ-1C parte A: el correo se encola con la reserva y el Outbox lo
 * reintenta sin tocar la reserva (RN-NOT-003).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class OutboxIntegrationTest {

    @Autowired
    ReservaService reservas;

    @Autowired
    OutboxService outbox;

    @Autowired
    OutboxRepository outboxRepo;

    @Autowired
    OutboxDespachador despachador;

    @MockitoBean
    JavaMailSender correo;

    @MockitoBean
    PasarelaStripe stripe;

    /** El JavaMailSender es un mock: tiene que devolver un mensaje de verdad para armarlo. */
    @org.junit.jupiter.api.BeforeEach
    void prepararCorreo() {
        when(correo.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private static HuespedDatos huesped() {
        return new HuespedDatos("Laura Méndez", "laura.mendez@example.com", "+502 5555 0001", "Guatemalteca",
                TipoDocumento.DPI, "2547896320101");
    }

    private Reserva reservaDeCanal(String identificador) {
        return reservas.crear(SolicitudReserva.canal(com.villaserena.api.reservas.CanalReserva.EXPEDIA, identificador,
                new java.math.BigDecimal("1500.00"), 1L, LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-05"),
                2, huesped()));
    }

    @Test
    void elCorreoQuedaEncoladoConLosDatosDeLaHistoria() {
        Reserva reserva = reservaDeCanal("EX-7001");

        List<Outbox> avisos = outboxRepo.findByTipoAndDestinatarioOrderByIdDesc(
                TipoNotificacion.CORREO_CONFIRMACION, "laura.mendez@example.com");
        assertThat(avisos).hasSize(1);
        Outbox aviso = avisos.getFirst();
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(aviso.getIntentos()).isZero();
        // Criterios 2 a 4 de HU-HUE-07.
        assertThat(aviso.getPayload())
                .contains("\"codigo\":\"" + reserva.getCodigo() + "\"")
                .contains("\"noches\":2")
                .contains("\"horaCheckin\":\"15:00\"")
                .contains("\"horaCheckout\":\"12:00\"")
                .contains("\"total\":\"Q 1500.00\"")
                // Pagado: el canal ya cobró (criterio 3).
                .contains("\"pagado\":true")
                .contains("appDescargaUrl");
    }

    @Test
    void siElCorreoFallaSeReintentaYLaReservaSigueConfirmada() {
        Reserva reserva = reservaDeCanal("EX-7002");
        long avisoId = outboxRepo.findByTipoAndDestinatarioOrderByIdDesc(
                TipoNotificacion.CORREO_CONFIRMACION, "laura.mendez@example.com").getFirst().getId();
        doThrow(new MailSendException("Mailpit apagado")).when(correo).send(any(MimeMessage.class));

        assertThat(despachador.procesar(avisoId)).isFalse();

        Outbox aviso = outboxRepo.findById(avisoId).orElseThrow();
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(aviso.getIntentos()).isEqualTo(1);
        assertThat(aviso.getUltimoError()).contains("Mailpit apagado");
        assertThat(aviso.getProximoIntentoEn()).isAfter(RelojFijoConfig.AHORA);
        // Lo importante: la reserva no se vio afectada.
        assertThat(reserva.getEstado()).isEqualTo(com.villaserena.api.reservas.EstadoReserva.CONFIRMADA);
    }

    @Test
    void despuesDeCincoIntentosQuedaFallido() {
        reservaDeCanal("EX-7003");
        long avisoId = outboxRepo.findByTipoAndDestinatarioOrderByIdDesc(
                TipoNotificacion.CORREO_CONFIRMACION, "laura.mendez@example.com").getFirst().getId();
        doThrow(new MailSendException("Mailpit apagado")).when(correo).send(any(MimeMessage.class));

        for (int intento = 1; intento <= 5; intento++) {
            despachador.procesar(avisoId);
        }

        Outbox aviso = outboxRepo.findById(avisoId).orElseThrow();
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(aviso.getIntentos()).isEqualTo(5);
    }

    @Test
    void cuandoElCorreoSaleQuedaEnviado() {
        reservaDeCanal("EX-7004");
        long avisoId = outboxRepo.findByTipoAndDestinatarioOrderByIdDesc(
                TipoNotificacion.CORREO_CONFIRMACION, "laura.mendez@example.com").getFirst().getId();
        doNothing().when(correo).send(any(MimeMessage.class));

        assertThat(despachador.procesar(avisoId)).isTrue();

        Outbox aviso = outboxRepo.findById(avisoId).orElseThrow();
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(aviso.getEnviadoEn()).isEqualTo(RelojFijoConfig.AHORA);
        assertThat(aviso.getUltimoError()).isNull();
        verify(correo).send(any(MimeMessage.class));
    }
}
