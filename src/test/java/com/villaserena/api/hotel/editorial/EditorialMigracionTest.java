package com.villaserena.api.hotel.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

class EditorialMigracionTest {
    @Test
    void actualizaV32ConDatosSinModificarNingunaTablaExistente() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").target("32").load().migrate();
            // Datos ficticios oficiales exclusivamente dentro de este contenedor desechable.
            postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/demo/load-data.sql")), "/tmp/load-data.sql");
            String hash = new BCryptPasswordEncoder(4).encode("Credencial-exclusiva-del-test");
            var carga = postgres.execInContainer("env", "DEMO_PASSWORD_HASH=" + hash, "CANAL1_KEY_HASH=" + "a".repeat(64), "CANAL2_KEY_HASH=" + "b".repeat(64),
                    "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", "/tmp/load-data.sql");
            assertThat(carga.getExitCode()).isZero();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            var tablas = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class);
            Map<String,List<String>> anteriores = new LinkedHashMap<>();
            for (String tabla : tablas) anteriores.put(tabla, filas(jdbc,tabla));
            assertThat(anteriores.get("employees")).isNotEmpty();
            assertThat(anteriores.get("hotel_configuration")).isNotEmpty();
            var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load();
            var migracion = flyway.migrate();
            assertThat(migracion.migrationsExecuted).isEqualTo(2);
            assertThat(migracion.migrations).extracting(resultado -> resultado.version).containsExactly("33", "34");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM employees WHERE personal_email IS NOT NULL", Long.class)).isZero();
            for (String tabla : tablas) assertThat(filas(jdbc,tabla)).as("Datos anteriores de %s",tabla).isEqualTo(anteriores.get(tabla));
            assertThat(filas(jdbc,"hotel_editorial_drafts")).isEmpty();
            assertThat(filas(jdbc,"hotel_editorial_publications")).isEmpty();
        }
    }

    private List<String> filas(JdbcTemplate jdbc,String tabla) {
        // V33 adds a nullable contact field; compare every pre-existing column unchanged.
        String fila = "employees".equals(tabla) ? "(to_jsonb(t) - 'personal_email')" : "to_jsonb(t)";
        return jdbc.queryForList("SELECT " + fila + "::text FROM \"" + tabla.replace("\"","\"\"") + "\" t ORDER BY " + fila + "::text",String.class);
    }
}
