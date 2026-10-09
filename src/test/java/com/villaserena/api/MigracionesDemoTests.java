package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Con el perfil "demo" (entorno local y presentación): se cargan los datos
 * ficticios de db/demo y el esquema pasa scripts/probar-esquema.sql.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = {
		// Valores solo para las pruebas (no son secretos reales).
		"DEMO_PASSWORD_HASH=$2y$10$EqZQCedXATrvLDvDyb/.K.WO6cgguZssAVOVY/nKQSTZChxDb5HPq",
		"CANAL1_KEY_HASH=9f06c44dd5a80429231bfc26a3334bef28ca9b64345a922aba20ea8eabc50e33",
		"CANAL2_KEY_HASH=6c1d9490f580587508725dfe3c742c0edb36ffa76e2daaccc54178a92b5a3406" })
class MigracionesDemoTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void cargaLosDatosDePresentacion() {
		assertThat(contar("habitaciones")).isEqualTo(12);
		assertThat(contar("tipos_habitacion")).isEqualTo(4);
		assertThat(contar("canales")).isEqualTo(2);
		assertThat(contar("empleados")).isEqualTo(7);
		assertThat(contar("configuracion_hotel")).isEqualTo(1);
	}

	@Test
	void elEsquemaPasaElScriptDePruebas() throws Exception {
		postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/probar-esquema.sql")),
				"/tmp/probar-esquema.sql");
		ExecResult resultado = postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d",
				postgres.getDatabaseName(), "-f", "/tmp/probar-esquema.sql");
		assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
		assertThat(resultado.getStdout()).contains("Todas las pruebas pasaron");
	}

	private int contar(String tabla) {
		return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
	}

}
