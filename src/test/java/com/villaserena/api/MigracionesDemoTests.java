package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * El API demo arranca vacío y los datos se cargan explícitamente con psql.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
		"spring.config.import=",
        "villaserena.primer-admin.habilitado=false",
		"villaserena.primer-admin.correo=",
		"villaserena.primer-admin.nombre=",
		"villaserena.primer-admin.contrasena=",
		// Valores solo para las pruebas (no son secretos reales).
		"villaserena.jwt.secreto=UHVibGljIHRlc3QgZml4dHVyZSBvbmx5IC0gbmV2ZXIgY29uZmlndXJlIHRoZSBhcHBsaWNhdGlvbiB3aXRoIHRoaXMgdmFsdWUu",
		"DEMO_PASSWORD_HASH=$2y$10$EqZQCedXATrvLDvDyb/.K.WO6cgguZssAVOVY/nKQSTZChxDb5HPq",
		"CANAL1_KEY_HASH=9f06c44dd5a80429231bfc26a3334bef28ca9b64345a922aba20ea8eabc50e33",
		"CANAL2_KEY_HASH=6c1d9490f580587508725dfe3c742c0edb36ffa76e2daaccc54178a92b5a3406" })
class MigracionesDemoTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Autowired
	Environment entorno;

	@BeforeAll
	void cargaExplicitaDespuesDelArranque() throws Exception {
		assertThat(contar("employees")).isZero();
		assertThat(contar("reservations")).isZero();
		jdbc.execute("""
				CREATE FUNCTION reject_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'Controlled fixture failure'; END $$
				""");
		jdbc.execute("""
				CREATE TRIGGER reject_fixture BEFORE INSERT ON catalog_offers
				FOR EACH ROW EXECUTE FUNCTION reject_fixture()
				""");
		assertThatThrownBy(() -> TestcontainersConfiguration.cargarDatosPrueba(postgres, entorno))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Controlled fixture failure");
		assertThat(contar("employees")).isZero();
		assertThat(contar("room_types")).isZero();
		jdbc.execute("DROP TRIGGER reject_fixture ON catalog_offers");
		jdbc.execute("DROP FUNCTION reject_fixture()");
		TestcontainersConfiguration.cargarDatosPrueba(postgres, entorno);
	}

    @Autowired
    com.villaserena.api.archivos.AlmacenArchivos archivos;

    @Test
    void archivosLocalesFuncionanSinDependenciasRemotas() {
        assertThat(archivos).isInstanceOf(com.villaserena.api.piso.ArchivosLocales.class);
        byte[] pdf = "%PDF-1.4 documento local".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        archivos.guardarPrivado("prueba-local.pdf", pdf, "application/pdf");
        assertThat(archivos.existePrivado("prueba-local.pdf")).isTrue();
        assertThat(archivos.leerPrivado("prueba-local.pdf")).isEqualTo(pdf);
        assertThat(archivos.urlFirmada("prueba-local.pdf")).startsWith("http://localhost:8080/api/archivos/lectura/");
        assertThat(archivos.existePrivado("ausente.pdf")).isFalse();
        assertThatThrownBy(() -> archivos.leerPrivado("ausente.pdf"))
                .isInstanceOf(com.villaserena.api.comun.ApiException.class);
        assertThatThrownBy(() -> archivos.urlFirmada("ausente.pdf"))
                .isInstanceOf(com.villaserena.api.comun.ApiException.class);
    }

	@Test
	void cargaLosDatosDePresentacion() {
		// La carga explícita aporta el hotel, el catálogo y el inventario local.
		assertThat(contar("rooms")).isEqualTo(22);
		assertThat(contar("room_types")).isEqualTo(12);
		assertThat(contar("channels")).isEqualTo(2);
		assertThat(contar("employees")).isEqualTo(7);
		assertThat(contar("hotel_configuration")).isEqualTo(1);
		assertThat(contar("catalog_offers")).isEqualTo(15);
		assertThat(jdbc.queryForObject("""
				SELECT count(*)  FROM flyway_schema_history
				WHERE version IN ('6','17.1','22.1','24.1') AND success
				""", Integer.class)).isZero();
	}

	@Test
	void unaSegundaCargaNoDuplicaNiBorraDatos() {
		assertThatThrownBy(() -> TestcontainersConfiguration.cargarDatosPrueba(postgres, entorno))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("require empty business tables");
		assertThat(contar("rooms")).isEqualTo(22);
		assertThat(contar("employees")).isEqualTo(7);
	}

	@Test
	void elEsquemaPasaElScriptDePruebas() throws Exception {
		postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/test-schema.sql")),
				"/tmp/test-schema.sql");
		ExecResult resultado = postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d",
				postgres.getDatabaseName(), "-f", "/tmp/test-schema.sql");
		assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
		assertThat(resultado.getStdout()).contains("All schema checks passed");
	}

	private int contar(String tabla) {
		return jdbc.queryForObject("SELECT count(*)  FROM " + tabla, Integer.class);
	}

}
