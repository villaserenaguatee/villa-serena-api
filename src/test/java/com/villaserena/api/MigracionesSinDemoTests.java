package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Sin el perfil "demo" (configuración destinada a producción): Flyway crea el
 * esquema, pero no carga datos ficticios ni exige los hashes de demostración.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
		"spring.config.import=",
		"villaserena.primer-admin.correo=",
		"villaserena.primer-admin.nombre=",
		"villaserena.primer-admin.contrasena=",
		"villaserena.jwt.secreto=UHVibGljIHRlc3QgZml4dHVyZSBvbmx5IC0gbmV2ZXIgY29uZmlndXJlIHRoZSBhcHBsaWNhdGlvbiB3aXRoIHRoaXMgdmFsdWUu" })
class MigracionesSinDemoTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	Environment entorno;

	@Test
	void noAplicaElSeedDeDemostracion() {
		Integer semillas = jdbc.queryForObject("""
				SELECT count(*)  FROM flyway_schema_history WHERE version IN ('6','17.1','22.1','24.1')
				""", Integer.class);
		Integer esquema = jdbc.queryForObject(
				"SELECT count(*)  FROM flyway_schema_history WHERE version IN ('1','2','3','4','5') AND success",
				Integer.class);
		assertThat(semillas).isZero();
		assertThat(esquema).isEqualTo(5);
	}

	@Test
	void todasLasTablasDeNegocioQuedanVacias() {
		var tablas = jdbc.queryForList("""
				SELECT tablename  FROM pg_tables
				WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
				ORDER BY tablename
				""", String.class);
		assertThat(tablas).isNotEmpty().allSatisfy(tabla ->
				assertThat(jdbc.queryForObject("SELECT count(*)  FROM \"" + tabla.replace("\"", "\"\"") + "\"", Long.class))
						.as("Filas iniciales en %s", tabla).isZero());
	}

	@Test
	void noExigeLosHashesDeDemostracion() {
		assertThat(entorno.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
		assertThat(entorno.containsProperty("spring.flyway.placeholders.demo_password_hash")).isFalse();
	}

}
