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
import com.villaserena.api.reservas.stripe.PasarelaStripe;

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
    PasarelaStripe stripe;

    Long reservaId;
    Long huespedId;
    Long habitacionId;

    @Test
    void siLaFacturaFallaNoCambiaNada() {
        long recepcionista = jdbc.queryForObject(
                "SELECT id FROM empleados WHERE correo = 'ana.perez@villaserena.test'", Long.class);
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Rollback', 'rollback@correo.test', '+502 0', 'Guatemalteca', 'DPI', '0') RETURNING id""",
                Long.class);
        habitacionId = jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '102'", Long.class);
        long tipo = jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = 'Estándar Jardín'", Long.class);
        Reserva reserva = reservaService.crear(SolicitudReserva.recepcion(huespedId, tipo,
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07"), 1, habitacionId, recepcionista));
        reservaId = reserva.getId();
        checkinService.hacerCheckin(reserva.getCodigo(), recepcionista);
        when(emisorFactura.emitir(anyLong(), anyString(), anyString())).thenThrow(new IllegalStateException("PDF"));

        Reserva enEstadia = checkoutService.reserva(reserva.getCodigo(), null);
        assertThatThrownBy(() -> checkoutService.hacerCheckout(enEstadia, Origen.RECEPCION, "CF", "Rollback",
                new PagoRecepcionPeticion(MetodoPago.EFECTIVO, null), false, Responsable.empleado(recepcionista)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT estado FROM reservas WHERE id = ?", String.class, reservaId))
                .isEqualTo("EN_ESTADIA");
        assertThat(jdbc.queryForObject("SELECT estado FROM cuentas WHERE reserva_id = ?", String.class, reservaId))
                .isEqualTo("ABIERTA");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pagos p JOIN cuentas c ON c.id = p.cuenta_id WHERE c.reserva_id = ?""",
                Integer.class, reservaId)).isZero();
        assertThat(jdbc.queryForObject("SELECT ocupacion FROM habitaciones WHERE id = ?", String.class, habitacionId))
                .isEqualTo("OCUPADA");
    }

    @AfterEach
    void limpiar() {
        if (reservaId != null) {
            jdbc.update("DELETE FROM historial_estados WHERE tipo_entidad = 'RESERVA' AND id_entidad = ?", reservaId);
            jdbc.update("DELETE FROM pagos WHERE cuenta_id IN (SELECT id FROM cuentas WHERE reserva_id = ?)", reservaId);
            jdbc.update("DELETE FROM cargos WHERE cuenta_id IN (SELECT id FROM cuentas WHERE reserva_id = ?)", reservaId);
            jdbc.update("DELETE FROM cuentas WHERE reserva_id = ?", reservaId);
            jdbc.update("DELETE FROM reserva_noches WHERE reserva_id = ?", reservaId);
            jdbc.update("DELETE FROM reservas WHERE id = ?", reservaId);
        }
        if (habitacionId != null) {
            jdbc.update("DELETE FROM historial_estados WHERE tipo_entidad LIKE 'HABITACION%' AND id_entidad = ?",
                    habitacionId);
            jdbc.update("UPDATE habitaciones SET ocupacion = 'LIBRE', condicion = 'LIMPIA' WHERE id = ?", habitacionId);
        }
        if (huespedId != null) {
            jdbc.update("DELETE FROM huespedes WHERE id = ?", huespedId);
        }
    }
}
