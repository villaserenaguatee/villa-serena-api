package com.villaserena.api.estadia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.estadia.CheckoutService.Origen;
import com.villaserena.api.estadia.dto.PagoRecepcionPeticion;
import com.villaserena.api.facturacion.EmisorFactura;
import com.villaserena.api.reservas.MetodoPago;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.pagos.PasarelaPagos;

/**
 * RN-RES-022: si la factura falla, el check-out no cambia nada. Esta prueba no corre
 * dentro de una transacción de prueba (para ver el rollback real) y limpia sus datos.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
class CheckoutRollbackTest {

    @Autowired
    ReservaService reservaService;

    @Autowired
    CheckinService checkinService;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    EmisorFactura emisorFactura;

    @MockitoBean
    PasarelaPagos pasarela;

    Long reservaId;
    Long huespedId;
    Long habitacionId;

    @Test
    void siLaFacturaFallaNoCambiaNada() {
        long recepcionista = jdbc.queryForObject(
                "SELECT id  FROM employees WHERE email = 'ana.perez@villaserena.test'", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO guests (full_name, email, phone, nationality, document_type, document_number)
                VALUES ('Rollback', 'rollback@correo.test', '+502 0', 'Guatemalteca', 'DPI', '0') RETURNING id""",
                Long.class);
        habitacionId = jdbc.queryForObject("SELECT id  FROM rooms WHERE number = '102'", Long.class);
        long tipo = jdbc.queryForObject("SELECT id  FROM room_types WHERE name = 'Estándar Jardín'", Long.class);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacionId, recepcionista));
        reservaId = reserva.getId();
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionista);
        when(emisorFactura.emitir(anyLong(), anyString(), anyString())).thenThrow(new IllegalStateException("PDF"));

        Reserva enEstadia = checkoutService.reserva(reserva.getCodigo(), null);
        assertThatThrownBy(() -> checkoutService.hacerCheckout(enEstadia, Origen.RECEPCION, "CF", "Rollback",
                new PagoRecepcionPeticion(MetodoPago.EFECTIVO, null), false, Responsable.empleado(recepcionista)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT status AS \"estado\" FROM reservations WHERE id = ?", String.class, reservaId))
                .isEqualTo("EN_ESTADIA");
        assertThat(jdbc.queryForObject("SELECT status AS \"estado\" FROM accounts WHERE reservation_id = ?", String.class, reservaId))
                .isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*)  FROM payments p JOIN accounts c ON c.id = p.account_id WHERE c.reservation_id = ?""",
                Integer.class, reservaId)).isZero();
        assertThat(jdbc.queryForObject("SELECT occupancy AS \"ocupacion\" FROM rooms WHERE id = ?", String.class, habitacionId))
                .isEqualTo("OCUPADA");
    }

    @AfterEach
    void limpiar() {
        if (reservaId != null) {
            jdbc.update("DELETE FROM status_history WHERE entity_type = 'RESERVA' AND entity_id = ?", reservaId);
            jdbc.update("DELETE FROM payments WHERE account_id IN (SELECT id FROM accounts WHERE reservation_id = ?)", reservaId);
            jdbc.update("DELETE FROM charges WHERE account_id IN (SELECT id FROM accounts WHERE reservation_id = ?)", reservaId);
            jdbc.update("DELETE FROM accounts WHERE reservation_id = ?", reservaId);
            jdbc.update("DELETE FROM reservation_nights WHERE reservation_id = ?", reservaId);
            jdbc.update("DELETE FROM reservations WHERE id = ?", reservaId);
        }
        if (habitacionId != null) {
            jdbc.update("DELETE FROM status_history WHERE entity_type LIKE 'HABITACION%' AND entity_id = ?",
                    habitacionId);
            jdbc.update("UPDATE rooms SET occupancy = 'LIBRE', condition = 'LIMPIA' WHERE id = ?", habitacionId);
        }
        if (huespedId != null) {
            jdbc.update("DELETE FROM guests WHERE id = ?", huespedId);
        }
    }
}
