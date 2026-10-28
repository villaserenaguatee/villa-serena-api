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
        "villaserena.primer-admin.habilitado=false",
        "WEB_URL=https://hotel.example", "CORS_ORIGENES=https://hotel.example",
        "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost/unused",
        "SPRING_DATASOURCE_USERNAME=test", "SPRING_DATASOURCE_PASSWORD=test",
		"villaserena.primer-admin.correo=",
		"villaserena.primer-admin.nombre=",
		"villaserena.primer-admin.contrasena=",
		"villaserena.jwt.secreto=UHVibGljIHRlc3QgZml4dHVyZSBvbmx5IC0gbmV2ZXIgY29uZmlndXJlIHRoZSBhcHBsaWNhdGlvbiB3aXRoIHRoaXMgdmFsdWUu" })
class MigracionesSinDemoTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	Environment entorno;

    @Autowired
    com.villaserena.api.auth.EmpleadoRepository empleados;
    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired
    com.villaserena.api.hotel.HotelService hotelService;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void bootstrapCreaSoloElPrimerAdminYNoReemplazaSusCredenciales() {
        var datos = new com.villaserena.api.config.PropiedadesVillaSerena.PrimerAdmin(
                true, "admin@example.com", "Admin inicial", "Clave-temporal-123");
        var props = new com.villaserena.api.config.PropiedadesVillaSerena(
                null, null, null, null, datos, null, null, null, null, null);
        new com.villaserena.api.auth.PrimerAdministrador(empleados, encoder, props, jdbc).run(null);
        empleados.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees WHERE role='ADMIN'", Long.class)).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT password_hash FROM employees", String.class);
        assertThat(encoder.matches("Clave-temporal-123", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM employees", Boolean.class)).isTrue();
        var otro = new com.villaserena.api.config.PropiedadesVillaSerena.PrimerAdmin(
                true, "otro@example.com", "Otro admin", "Otra-clave-temporal-123");
        var otrasProps = new com.villaserena.api.config.PropiedadesVillaSerena(
                null, null, null, null, otro, null, null, null, null, null);
        new com.villaserena.api.auth.PrimerAdministrador(empleados, encoder, otrasProps, jdbc).run(null);
        empleados.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM employees", String.class)).isEqualTo(hash);
    }

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

    @Test
    @org.springframework.transaction.annotation.Transactional
    void inicializacionHotelNoAprovisionaSerieSinDemo() {
        var peticion = new com.villaserena.api.hotel.HotelAdminPeticion(
                "Hotel Villa Serena", "Hotel en Antigua", "5a Avenida Norte 12",
                "+502 7832 0000", "hotel@villaserena.test", "Villa Serena",
                "Villa Serena S.A.", "4817205-7", "5a Avenida Norte 12",
                java.util.List.of());
        var inicializacion = new com.villaserena.api.hotel.InicializarHotelPeticion(
                peticion.nombre(), peticion.descripcion(), peticion.direccion(),
                peticion.telefono(), peticion.correo(), peticion.nombreComercial(),
                peticion.razonSocial(), peticion.nit(), peticion.direccionFiscal());
        assertThat(hotelService.inicializar(inicializacion).creada()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_configuration", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_series", Integer.class)).isZero();

        assertThat(hotelService.inicializar(inicializacion).creada()).isFalse();
        hotelService.actualizar(peticion);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_series", Integer.class)).isZero();
    }

}
