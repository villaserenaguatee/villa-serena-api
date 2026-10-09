package com.villaserena.api.estadia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.stripe.PasarelaStripe;

/**
 * Asignaciones simultáneas de la misma habitación (HU-REC-07). Sin {@code @Transactional}
 * porque usa dos transacciones reales; limpia lo que crea.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
class AsignacionConcurrenteTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @Autowired
    EmpleadoRepository empleados;

    @Autowired
    TokenService tokens;

    @Autowired
    RelojFijoConfig.RelojPrueba reloj;

    @MockitoBean
    PasarelaStripe stripe;

    long huespedId;
    long reservaA;
    long reservaB;

    @BeforeEach
    void preparar() {
        huespedId = jdbc.queryForObject("""
                INSERT INTO huespedes (nombre_completo, correo, telefono, nacionalidad, tipo_documento, numero_documento)
                VALUES ('Concurrencia', 'concurrencia@correo.test', '+502 5555 4444', 'Guatemalteca', 'DPI', '4444444440101')
                RETURNING id""", Long.class);
        reservaA = reserva("VS-CONCA1");
        reservaB = reserva("VS-CONCB1");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reservas WHERE id IN (?, ?)", reservaA, reservaB);
        jdbc.update("DELETE FROM huespedes WHERE id = ?", huespedId);
        reloj.reiniciar();
    }

    /**
     * Otra transacción asigna la misma habitación sin confirmar todavía: guardar
     * habitacion_id bloquea la fila de la habitación, así que esta asignación espera y
     * luego ve el traslape.
     */
    @Test
    void dosAsignacionesALaVezSoloUnaGana() throws Exception {
        long habitacion = habitacion103();
        CompletableFuture<Void> otra = enOtraTransaccion(
                "UPDATE reservas SET habitacion_id = %d WHERE id = %d".formatted(habitacion, reservaB));

        asignarA(habitacion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_OCUPADA"));
        otra.get(10, TimeUnit.SECONDS);
        assertThat(habitacionDe(reservaA)).isNull();
        assertThat(habitacionDe(reservaB)).isEqualTo(habitacion);
    }

    /**
     * Otra transacción mueve las fechas de una reserva que ya tiene la habitación (no
     * bloquea la fila de la habitación): la revisión previa no lo ve, la restricción
     * EXCLUDE lo detiene al guardar y el API lo traduce a 409.
     */
    @Test
    void laRestriccionExcludeSeTraduceA409() throws Exception {
        long habitacion = habitacion103();
        jdbc.update("""
                UPDATE reservas SET habitacion_id = ?, fecha_entrada = '2026-10-25', fecha_salida = '2026-10-27'
                WHERE id = ?""", habitacion, reservaB);
        CompletableFuture<Void> otra = enOtraTransaccion("""
                UPDATE reservas SET fecha_entrada = '2026-10-20', fecha_salida = '2026-10-22' WHERE id = %d"""
                .formatted(reservaB));

        asignarA(habitacion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HABITACION_OCUPADA"));
        otra.get(10, TimeUnit.SECONDS);
        assertThat(habitacionDe(reservaA)).isNull();
    }

    /** Ejecuta la sentencia en otra transacción y la confirma 1,5 s después. */
    private CompletableFuture<Void> enOtraTransaccion(String sql) throws InterruptedException {
        CountDownLatch sinConfirmar = new CountDownLatch(1);
        CompletableFuture<Void> otra = CompletableFuture.runAsync(() -> transacciones.executeWithoutResult(t -> {
            jdbc.update(sql);
            sinConfirmar.countDown();
            dormir(1500);
        }));
        assertThat(sinConfirmar.await(10, TimeUnit.SECONDS)).isTrue();
        return otra;
    }

    private ResultActions asignarA(long habitacion) throws Exception {
        return mvc.perform(put("/api/v1/reservas/{c}/habitacion", "VS-CONCA1")
                .header("Authorization", token("ana.perez@villaserena.test"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"habitacionId\": " + habitacion + "}"));
    }

    private long habitacion103() {
        return jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = '103'", Long.class);
    }

    private Long habitacionDe(long reserva) {
        return jdbc.queryForObject("SELECT habitacion_id FROM reservas WHERE id = ?", Long.class, reserva);
    }

    private long reserva(String codigo) {
        return jdbc.queryForObject("""
                INSERT INTO reservas (codigo, huesped_id, tipo_habitacion_id, fecha_entrada, fecha_salida,
                                      numero_huespedes, estado, canal, total)
                SELECT ?, ?, id, '2026-10-20', '2026-10-22', 1, 'CONFIRMADA', 'RECEPCION', 1700.00
                FROM tipos_habitacion WHERE nombre = 'Doble Superior'
                RETURNING id""", Long.class, codigo, huespedId);
    }

    private String token(String correo) {
        reloj.fijar(Instant.now());
        try {
            return "Bearer " + tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow())
                    .accessToken();
        } finally {
            reloj.reiniciar();
        }
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
