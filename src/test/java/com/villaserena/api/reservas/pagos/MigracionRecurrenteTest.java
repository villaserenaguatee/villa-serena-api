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
                    INSERT INTO guests(full_name,email,phone,document_type,document_number)
                    VALUES ('Huésped ficticio','migracion@correo.test','1','DPI','1') RETURNING id""", Long.class);
            Long tipo = jdbc.queryForObject("""
                    INSERT INTO room_types(name,description,capacity,base_price)
                    VALUES ('Tipo ficticio','Prueba',2,100) RETURNING id""", Long.class);
            Long reserva = jdbc.queryForObject("""
                    INSERT INTO reservations(code,guest_id,room_type_id,checkin_date,checkout_date,
                                         guest_count,status,channel,total)
                    VALUES ('VS-MIGR01',?,?,'2030-01-01','2030-01-02',1,'CONFIRMADA','DIRECTO_WEB',100)
                    RETURNING id""", Long.class, huesped, tipo);
            Long cuenta = jdbc.queryForObject("INSERT INTO accounts(reservation_id) VALUES (?) RETURNING id", Long.class, reserva);
            jdbc.update("""
                    INSERT INTO payments(account_id,method,status,amount,stripe_session_id,stripe_payment_intent_id,approved_at)
                    VALUES (?,'STRIPE','APROBADO',100,'cs_historica','pi_historico',now())""", cuenta);
            jdbc.update("INSERT INTO stripe_events(event_id,type) VALUES ('evt_historico','checkout.session.completed')");
            jdbc.update("""
                    INSERT INTO status_history(entity_type,entity_id,new_status,responsible_type)
                    VALUES ('RESERVA',?,'CONFIRMADA','STRIPE')""", reserva);

            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForMap("SELECT method AS \"metodo\",checkout_id,external_payment_id AS \"pago_externo_id\" FROM payments WHERE account_id=?", cuenta))
                    .containsEntry("metodo", "STRIPE").containsEntry("checkout_id", "cs_historica")
                    .containsEntry("pago_externo_id", "pi_historico");
            assertThat(jdbc.queryForObject("SELECT type AS \"tipo\" FROM payment_events WHERE event_id='evt_historico'", String.class))
                    .isEqualTo("checkout.session.completed");
            assertThat(jdbc.queryForObject("SELECT responsible_type AS \"tipo_responsable\" FROM status_history WHERE entity_id=?",
                    String.class, reserva)).isEqualTo("STRIPE");
            jdbc.update("""
                    INSERT INTO payments(account_id,method,status,amount,checkout_id)
                    VALUES (?,'RECURRENTE','PENDIENTE',100,'ch_nuevo')""", cuenta);
            assertThat(jdbc.queryForObject("SELECT count(*)  FROM payments WHERE account_id=?", Integer.class, cuenta)).isEqualTo(2);
        }
    }
}
