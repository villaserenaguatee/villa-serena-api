package com.villaserena.api.demo;

import static com.villaserena.api.huespedes.TipoDocumento.DPI;
import static com.villaserena.api.huespedes.TipoDocumento.PASAPORTE;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.HistorialEstadoService;
import com.villaserena.api.comun.Responsable;
import com.villaserena.api.comun.TipoEntidadHistorial;
import com.villaserena.api.huespedes.HuespedDatos;
import com.villaserena.api.huespedes.HuespedService;
import com.villaserena.api.huespedes.TipoDocumento;
import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.EstadoReserva;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;

/**
 * Reservas de prueba para el Gantt, la búsqueda y la app (OBJ-2A). Solo con el perfil
 * "demo" y solo si la base no tiene reservas: al arrancar con la base vacía crea 12
 * reservas con fechas relativas a "hoy" (America/Guatemala), de los 4 canales y de
 * todos los estados, usando los servicios reales (cuenta, cargos e historial).
 * <p>
 * Incluye una reserva EN_ESTADIA con habitación OCUPADA para el huésped
 * {@value #CORREO_APP}, para probar la app sin esperar el check-in. No envía correos.
 */
@Component
@Profile("demo")
public class ReservasDePrueba implements ApplicationRunner {

    /** Huésped en estadía para probar la app (acceso con código por Mailpit). */
    public static final String CORREO_APP = "huesped.app@villaserena.test";

    private static final Logger log = LoggerFactory.getLogger(ReservasDePrueba.class);
    private static final String GT = "Guatemalteca";

    private final ReservaService reservas;
    private final ReservaRepository reservasRepo;
    private final HuespedService huespedes;
    private final HistorialEstadoService historial;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public ReservasDePrueba(ReservaService reservas, ReservaRepository reservasRepo, HuespedService huespedes,
            HistorialEstadoService historial, JdbcTemplate jdbc, Clock reloj) {
        this.reservas = reservas;
        this.reservasRepo = reservasRepo;
        this.huespedes = huespedes;
        this.historial = historial;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT count(*) FROM reservas", Integer.class) > 0) {
            return;
        }
        Optional<Long> recepcionista = jdbc.queryForList("""
                SELECT id FROM empleados WHERE rol = 'RECEPCION' AND estado = 'ACTIVO' ORDER BY id LIMIT 1""",
                Long.class).stream().findFirst();
        if (recepcionista.isEmpty() || jdbc.queryForObject("SELECT count(*) FROM habitaciones", Integer.class) == 0) {
            log.warn("Reservas de prueba omitidas: faltan los datos de demostración (empleados y habitaciones).");
            return;
        }
        long empleado = recepcionista.get();
        Responsable recepcion = Responsable.empleado(empleado);
        long ultimoAviso = jdbc.queryForObject("SELECT coalesce(max(id), 0) FROM outbox", Long.class);
        LocalDate hoy = LocalDate.now(reloj);

        // Llegan hoy: una con habitación y otra en "Sin asignar".
        long maria = huesped("María José López", "maria.lopez@correo.test", GT, DPI, "2456789010101");
        recepcion(maria, "Doble Superior", hoy, 2, 2, "103", empleado);
        long carlos = huesped("Carlos Méndez", "carlos.mendez@correo.test", GT, DPI, "2987654320101");
        recepcion(carlos, "Estándar Jardín", hoy, 1, 1, null, empleado);

        // En estadía: la del huésped de la app y otra que sale hoy con saldo pendiente.
        long ana = huesped("Ana Lucía Pérez", CORREO_APP, GT, DPI, "3012345670101");
        enEstadia(mover(recepcion(ana, "Suite Volcán", hoy, 3, 2, "201", empleado), -2), recepcion);
        long jorge = huesped("Jorge Ramírez", "jorge.ramirez@correo.test", GT, DPI, "2765432100101");
        enEstadia(mover(recepcion(jorge, "Suite Familiar", hoy, 3, 4, "301", empleado), -3), recepcion);

        // Finalizada la semana pasada: pagada en efectivo y con la cuenta cerrada.
        long sophie = huesped("Sophie Martin", "sophie.martin@correo.test", "Francesa", PASAPORTE, "19FR45821");
        Reserva finalizada = mover(recepcion(sophie, "Doble Superior", hoy, 3, 2, "104", empleado), -10);
        enEstadia(finalizada, recepcion);
        pagar(finalizada, "EFECTIVO", empleado);
        reservas.cambiarEstado(finalizada, EstadoReserva.FINALIZADA, recepcion, null);
        jdbc.update("UPDATE habitaciones SET ocupacion = 'LIBRE' WHERE id = ?", finalizada.getHabitacionId());
        historial.registrar(TipoEntidadHistorial.HABITACION_OCUPACION, finalizada.getHabitacionId(), "OCUPADA",
                "LIBRE", recepcion, null);
        jdbc.update("UPDATE cuentas SET estado = 'CERRADA', cerrada_en = now() WHERE reserva_id = ?",
                finalizada.getId());

        // Web: una cancelada y dos pagadas con Stripe.
        Reserva cancelada = reservas.crear(SolicitudReserva.web(tipo("Estándar Jardín"), hoy.plusDays(5),
                hoy.plusDays(7), 2, datos("Lucía Fernández", "lucia.fernandez@correo.test", GT, DPI,
                        "2345678900101")));
        reservas.cancelar(cancelada, recepcion, "El huésped cambió sus planes de viaje.");
        web(datos("Daniel Castillo", "daniel.castillo@correo.test", GT, DPI, "2111222330101"),
                "Doble Superior", hoy.plusDays(3), 2, 3, null, recepcion);
        web(datos("Emily Johnson", "emily.johnson@correo.test", "Estadounidense", PASAPORTE, "567123908"),
                "Suite Familiar", hoy.plusDays(28), 3, 5, "302", recepcion);

        // Canales externos.
        canal(CanalReserva.BOOKING, "BK-10045871", new BigDecimal("4100.00"),
                datos("John Smith", "john.smith@correo.test", "Estadounidense", PASAPORTE, "567890123"),
                "Suite Volcán", hoy.plusDays(6), 3, 2, "202", recepcion);
        canal(CanalReserva.EXPEDIA, "EX-77412093", new BigDecimal("1250.00"),
                datos("Hans Müller", "hans.muller@correo.test", "Alemana", PASAPORTE, "C4R7T2X91"),
                "Estándar Jardín", hoy.plusDays(10), 2, 1, null, recepcion);
        canal(CanalReserva.BOOKING, "BK-10046210", new BigDecimal("1900.00"),
                datos("Valentina Rossi", "valentina.rossi@correo.test", "Italiana", PASAPORTE, "YA8821456"),
                "Doble Superior", hoy.plusDays(35), 2, 3, "203", recepcion);

        // El mes siguiente, desde Recepción.
        long roberto = huesped("Roberto Ajú", "roberto.aju@correo.test", GT, DPI, "2678901230101");
        recepcion(roberto, "Suite Volcán", hoy.plusDays(20), 3, 2, "303", empleado);

        // Los datos de prueba no envían correos de confirmación.
        jdbc.update("DELETE FROM outbox WHERE id > ?", ultimoAviso);
        log.info("Reservas de prueba creadas: 12 (perfil demo). Huésped de la app: {}", CORREO_APP);
    }

    private Reserva recepcion(long huesped, String tipo, LocalDate entrada, int noches, int personas,
            String habitacion, long empleado) {
        return reservas.crear(SolicitudReserva.recepcion(huesped, tipo(tipo), entrada, entrada.plusDays(noches),
                personas, habitacion == null ? null : habitacion(habitacion), empleado));
    }

    private void web(HuespedDatos huesped, String tipo, LocalDate entrada, int noches, int personas,
            String habitacion, Responsable recepcion) {
        Reserva reserva = reservas.crear(SolicitudReserva.web(tipo(tipo), entrada, entrada.plusDays(noches),
                personas, huesped));
        pagar(reserva, "STRIPE", null);
        reservas.confirmarPagoWeb(reserva, Responsable.STRIPE);
        if (habitacion != null) {
            reservas.asignarHabitacion(reserva, habitacion(habitacion), recepcion);
        }
    }

    private void canal(CanalReserva canal, String identificador, BigDecimal monto, HuespedDatos huesped,
            String tipo, LocalDate entrada, int noches, int personas, String habitacion, Responsable recepcion) {
        Reserva reserva = reservas.crear(SolicitudReserva.canal(canal, identificador, monto, tipo(tipo), entrada,
                entrada.plusDays(noches), personas, huesped));
        if (habitacion != null) {
            reservas.asignarHabitacion(reserva, habitacion(habitacion), recepcion);
        }
    }

    /** Mueve las fechas de la reserva (y sus noches) {@code dias} días; negativo hacia el pasado. */
    private Reserva mover(Reserva reserva, int dias) {
        reserva.setFechaEntrada(reserva.getFechaEntrada().plusDays(dias));
        reserva.setFechaSalida(reserva.getFechaSalida().plusDays(dias));
        reservasRepo.saveAndFlush(reserva);
        jdbc.update("UPDATE reserva_noches SET fecha = fecha + ? WHERE reserva_id = ?", dias, reserva.getId());
        return reserva;
    }

    /** CONFIRMADA → EN_ESTADIA con la habitación OCUPADA, como en el check-in. */
    private void enEstadia(Reserva reserva, Responsable recepcion) {
        jdbc.update("UPDATE habitaciones SET ocupacion = 'OCUPADA' WHERE id = ?", reserva.getHabitacionId());
        historial.registrar(TipoEntidadHistorial.HABITACION_OCUPACION, reserva.getHabitacionId(), "LIBRE",
                "OCUPADA", recepcion, null);
        reservas.cambiarEstado(reserva, EstadoReserva.EN_ESTADIA, recepcion, null);
    }

    /** Pago APROBADO por el total de la reserva; los de Stripe con una sesión ficticia de prueba. */
    private void pagar(Reserva reserva, String metodo, Long empleado) {
        String sesion = "STRIPE".equals(metodo) ? "cs_test_demo_" + reserva.getCodigo() : null;
        jdbc.update("""
                INSERT INTO pagos (cuenta_id, metodo, estado, monto, stripe_session_id, registrado_por_empleado_id,
                                   aprobado_en)
                SELECT id, ?, 'APROBADO', ?, ?, ?, now() FROM cuentas WHERE reserva_id = ?""",
                metodo, reserva.getTotal(), sesion, empleado, reserva.getId());
    }

    /** Registra al huésped (o reutiliza el existente) y devuelve su id, como hace Recepción. */
    private long huesped(String nombre, String correo, String nacionalidad, TipoDocumento tipo, String documento) {
        return huespedes.obtenerOCrear(datos(nombre, correo, nacionalidad, tipo, documento)).getId();
    }

    private static HuespedDatos datos(String nombre, String correo, String nacionalidad, TipoDocumento tipo,
            String documento) {
        return new HuespedDatos(nombre, correo, "+502 5000 0000", nacionalidad, tipo, documento);
    }

    private long tipo(String nombre) {
        return jdbc.queryForObject("SELECT id FROM tipos_habitacion WHERE nombre = ?", Long.class, nombre);
    }

    private long habitacion(String numero) {
        return jdbc.queryForObject("SELECT id FROM habitaciones WHERE numero = ?", Long.class, numero);
    }
}
