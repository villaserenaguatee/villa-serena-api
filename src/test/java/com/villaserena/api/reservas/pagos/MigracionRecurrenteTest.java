package com.villaserena.api.reservas.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Comprueba la actualización con datos anteriores, además del arranque desde cero. */
class MigracionRecurrenteTest {
    @Test
    void conservaPagosEventosYResponsablesDeStripeAlAplicarV27() {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").target("26").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            Long huesped = jdbc.queryForObject("""
                    INSERT INTO huespedes(nombre_completo,correo,telefono,tipo_documento,numero_documento)
                    VALUES ('Huésped ficticio','migracion@correo.test','1','DPI','1') RETURNING id""", Long.class);
            Long tipo = jdbc.queryForObject("""
                    INSERT INTO tipos_habitacion(nombre,descripcion,capacidad,precio_base)
                    VALUES ('Tipo ficticio','Prueba',2,100) RETURNING id""", Long.class);
            Long reserva = jdbc.queryForObject("""
                    INSERT INTO reservas(codigo,huesped_id,tipo_habitacion_id,fecha_entrada,fecha_salida,
                                         numero_huespedes,estado,canal,total)
                    VALUES ('VS-MIGR01',?,?,'2030-01-01','2030-01-02',1,'CONFIRMADA','DIRECTO_WEB',100)
                    RETURNING id""", Long.class, huesped, tipo);
            Long cuenta = jdbc.queryForObject("INSERT INTO cuentas(reserva_id) VALUES (?) RETURNING id", Long.class, reserva);
            jdbc.update("""
                    INSERT INTO pagos(cuenta_id,metodo,estado,monto,stripe_session_id,stripe_payment_intent_id,aprobado_en)
                    VALUES (?,'STRIPE','APROBADO',100,'cs_historica','pi_historico',now())""", cuenta);
            jdbc.update("INSERT INTO stripe_eventos(evento_id,tipo) VALUES ('evt_historico','checkout.session.completed')");
            jdbc.update("""
                    INSERT INTO historial_estados(tipo_entidad,id_entidad,estado_nuevo,tipo_responsable)
                    VALUES ('RESERVA',?,'CONFIRMADA','STRIPE')""", reserva);

            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForMap("SELECT metodo,checkout_id,pago_externo_id FROM pagos WHERE cuenta_id=?", cuenta))
                    .containsEntry("metodo", "STRIPE").containsEntry("checkout_id", "cs_historica")
                    .containsEntry("pago_externo_id", "pi_historico");
            assertThat(jdbc.queryForObject("SELECT tipo FROM pago_eventos WHERE evento_id='evt_historico'", String.class))
                    .isEqualTo("checkout.session.completed");
            assertThat(jdbc.queryForObject("SELECT tipo_responsable FROM historial_estados WHERE id_entidad=?",
                    String.class, reserva)).isEqualTo("STRIPE");
            jdbc.update("""
                    INSERT INTO pagos(cuenta_id,metodo,estado,monto,checkout_id)
                    VALUES (?,'RECURRENTE','PENDIENTE',100,'ch_nuevo')""", cuenta);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos WHERE cuenta_id=?", Integer.class, cuenta)).isEqualTo(2);
        }
    }
}
