package com.villaserena.api.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.notificaciones.EstadoNotificacion;
import com.villaserena.api.notificaciones.Outbox;
import com.villaserena.api.notificaciones.OutboxDespachador;
import com.villaserena.api.notificaciones.OutboxRepository;
import com.villaserena.api.notificaciones.OutboxService;
import com.villaserena.api.notificaciones.TipoNotificacion;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Pruebas de OBJ-3A-2 parte C: la push solo sale durante la estadía (RN-NOT-001) y,
 * si el envío falla, se reintenta sin tocar el cambio de estado (RN-NOT-002).
 * <p>
 * En las pruebas {@code EXPO_PUSH_URL} apunta a un puerto cerrado, así que un
 * intento real de envío falla siempre: eso permite distinguir "no se intentó"
 * (queda ENVIADO) de "se intentó y falló" (queda PENDIENTE con el error).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
@Transactional
class EnviadorPushTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    OutboxService outbox;

    @Autowired
    OutboxRepository outboxRepo;

    @Autowired
    OutboxDespachador despachador;

    @MockitoBean
    PasarelaStripe stripe;

    long recepcionista;
    long huespedId;
    String codigo;

    @BeforeEach
    void prepararReserva() {
        recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Ana Push', 'push.ana@correo.test', '+502 5555 9999', 'Guatemalteca', 'DPI', '4444444444444')
                RETURNING id""", Long.class);
        long habitacion = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '202'", Long.class);
        long tipo = jdbc.queryForObject("SELECT tipo_habitacion_id FROM habitaciones WHERE id = ?", Long.class,
                habitacion);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-08"), 1, habitacion, recepcionista));
        codigo = reserva.getCodigo();
    }

    private long encolarPush() {
        Outbox aviso = outbox.encolar(TipoNotificacion.PUSH_PEDIDO_ENTREGADO, "ExponentPushToken[pruebas]",
                Map.of("pantalla", "pedidos", "codigoReserva", codigo, "pedidoId", 7));
        outboxRepo.flush();
        return aviso.getId();
    }

    @Test
    void sinCheckInLaPushNoSeEnvia() {
        long avisoId = encolarPush();

        despachador.procesar(avisoId);

        Outbox aviso = outboxRepo.findById(avisoId).orElseThrow();
        // No se intentó: no hay error guardado y no quedó pendiente de reintento.
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(aviso.getUltimoError()).isNull();
    }

    @Test
    void enEstadiaSeIntentaEnviarYSiFallaSeReintenta() {
        checkinService.hacerCheckin(codigo, recepcionista);
        long avisoId = encolarPush();

        despachador.procesar(avisoId);

        Outbox aviso = outboxRepo.findById(avisoId).orElseThrow();
        assertThat(aviso.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(aviso.getIntentos()).isEqualTo(1);
        assertThat(aviso.getUltimoError()).isNotNull();
        // La reserva no se ve afectada por el fallo del envío.
        assertThat(jdbc.queryForObject("SELECT estado FROM reservas WHERE codigo = ?", String.class, codigo))
                .isEqualTo("EN_ESTADIA");
    }

    @Test
    void elPayloadNoLlevaDatosPersonalesNiMontos() {
        long avisoId = encolarPush();

        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox WHERE id = ?", String.class, avisoId);
        assertThat(payload).contains(codigo).contains("pedidos")
                .doesNotContain("Ana Push").doesNotContain("push.ana@correo.test");
    }
}
