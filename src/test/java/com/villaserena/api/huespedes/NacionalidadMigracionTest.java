package com.villaserena.api.huespedes;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Comprueba la actualización de una base existente sin modificar datos históricos. */
@Testcontainers
class NacionalidadMigracionTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Test
    void permiteNulosYConservaLaNacionalidadAnterior() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target("25").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("""
                INSERT INTO guests
                    (full_name, email, phone, nationality, document_type, document_number)
                VALUES ('Perfil histórico', 'historico@example.test', '+50255551234', 'Guatemalteca', 'DPI', '1234567890101')
                """);

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT nationality AS \"nacionalidad\" FROM guests WHERE email = 'historico@example.test'",
                String.class)).isEqualTo("Guatemalteca");
        jdbc.update("""
                INSERT INTO guests (full_name, email, phone, document_type, document_number)
                VALUES ('Perfil nuevo', 'nuevo@example.test', '+50255551234', 'DPI', '9876543210101')
                """);
        assertThat(jdbc.queryForObject("SELECT nationality AS \"nacionalidad\" FROM guests WHERE email = 'nuevo@example.test'",
                String.class)).isNull();
        assertThat(jdbc.queryForList("""
                SELECT is_nullable  FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name IN ('guests', 'additional_guests')
                    AND column_name = 'nationality'
                """, String.class)).containsExactly("YES", "YES");
    }
}
