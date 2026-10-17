package com.villaserena.api.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.reservas.BusquedaReservasService;
import com.villaserena.api.reservas.BusquedaReservasService.FiltroRapido;
import com.villaserena.api.reservas.BusquedaReservasService.Filtros;
import com.villaserena.api.reservas.dto.CalendarioReservas;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Con el perfil "demo", al arrancar con la base vacía se crean las reservas de prueba
 * (OBJ-2A, criterio 3). "Hoy" es el lunes 5 de octubre de 2026 (RelojFijoConfig).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles({"test", "demo"})
class ReservasDePruebaTest {

    static final LocalDate HOY = LocalDate.of(2026, 10, 5);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReservasDePrueba reservasDePrueba;

    @Autowired
    BusquedaReservasService busqueda;

    @MockitoBean
    PasarelaStripe stripe;

    @Test
    void creaDoceReservasDeLosCuatroCanalesYVariosEstados() {
        assertThat(contar("SELECT count(*) FROM reservas")).isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT DISTINCT canal FROM reservas", String.class))
                .containsExactlyInAnyOrder("DIRECTO_WEB", "RECEPCION", "BOOKING", "EXPEDIA");
        assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM reservas", String.class))
                .containsExactlyInAnyOrder("CONFIRMADA", "EN_ESTADIA", "FINALIZADA", "CANCELADA");
        // Todas con su cuenta y el cargo por alojamiento; no quedan correos pendientes.
        assertThat(contar("SELECT count(*) FROM cuentas")).isEqualTo(12);
        assertThat(contar("SELECT count(*) FROM outbox")).isZero();
        // Las fechas cubren este mes y el siguiente.
        assertThat(contar("SELECT count(*) FROM reservas WHERE fecha_entrada >= '2026-11-01'")).isPositive();
        assertThat(contar("SELECT count(*) FROM reservas WHERE fecha_salida <= '2026-10-05'")).isPositive();
    }

    @Test
    void elHuespedDeLaAppEstaEnEstadiaConLaHabitacionOcupada() {
        assertThat(jdbc.queryForObject("""
                SELECT hab.ocupacion FROM reservas r
                JOIN huespedes h ON h.id = r.huesped_id
                JOIN habitaciones hab ON hab.id = r.habitacion_id
                WHERE lower(h.correo) = ? AND r.estado = 'EN_ESTADIA'
                  AND r.fecha_entrada < ? AND r.fecha_salida > ?""",
                String.class, ReservasDePrueba.CORREO_APP, HOY, HOY)).isEqualTo("OCUPADA");
    }

    @Test
    void hayLlegadasYSalidasDeHoyYFilaSinAsignar() {
        assertThat(busqueda.buscar(rapido(FiltroRapido.LLEGAN_HOY), 0, 20).totalElementos()).isEqualTo(2);
        assertThat(busqueda.buscar(rapido(FiltroRapido.SALEN_HOY), 0, 20).contenido())
                .singleElement()
                .satisfies(r -> assertThat(r.saldoPendiente()).isPositive());

        CalendarioReservas gantt = busqueda.calendario(HOY.minusDays(15), HOY.plusDays(45));
        assertThat(gantt.reservas()).hasSize(11);
        assertThat(gantt.reservas()).anyMatch(r -> r.habitacionId() == null);
        assertThat(gantt.reservas()).noneMatch(r -> r.estado().name().equals("CANCELADA"));
    }

    @Test
    void noDuplicaSiYaHayReservas() throws Exception {
        reservasDePrueba.run(null);
        assertThat(contar("SELECT count(*) FROM reservas")).isEqualTo(12);
    }

    private static Filtros rapido(FiltroRapido filtro) {
        return new Filtros(null, null, null, null, null, null, filtro);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
