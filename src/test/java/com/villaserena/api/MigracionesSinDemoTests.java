package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sin el perfil "demo" (configuración destinada a producción): Flyway crea el
 * esquema, pero no carga datos ficticios ni exige los hashes de demostración.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigracionesSinDemoTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	Environment entorno;

	@Test
	void noAplicaElSeedDeDemostracion() {
		Integer v6 = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '6'", Integer.class);
		Integer esquema = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5') AND success",
				Integer.class);
		assertThat(v6).isZero();
		assertThat(esquema).isEqualTo(5);
	}

	@ParameterizedTest
	@ValueSource(strings = { "configuracion_hotel", "series_factura", "tipos_habitacion", "habitaciones",
			"temporadas", "items_menu", "articulos", "canales", "empleados" })
	void lasTablasQuedanSinDatosFicticios(String tabla) {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class)).isZero();
	}

	@Test
	void noExigeLosHashesDeDemostracion() {
		assertThat(entorno.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
		assertThat(entorno.containsProperty("spring.flyway.placeholders.demo_password_hash")).isFalse();
	}

}
