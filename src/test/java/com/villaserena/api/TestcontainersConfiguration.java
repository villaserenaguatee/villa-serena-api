package com.villaserena.api;

import java.nio.file.Path;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	@Bean
	@Profile("test")
	@DependsOn("flywayInitializer")
	InitializingBean datosPrueba(PostgreSQLContainer postgres, Environment entorno) {
		return () -> cargarDatosPrueba(postgres, entorno);
	}

	static void cargarDatosPrueba(PostgreSQLContainer postgres, Environment entorno) throws Exception {
		postgres.copyFileToContainer(MountableFile.forHostPath(Path.of("scripts/demo/load-data.sql")), "/tmp/load-data.sql");
		var resultado = postgres.execInContainer("env",
				"DEMO_PASSWORD_HASH=" + entorno.getRequiredProperty("DEMO_PASSWORD_HASH"),
				"CANAL1_KEY_HASH=" + entorno.getRequiredProperty("CANAL1_KEY_HASH"),
				"CANAL2_KEY_HASH=" + entorno.getRequiredProperty("CANAL2_KEY_HASH"),
				"psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
				"-f", "/tmp/load-data.sql");
		if (resultado.getExitCode() != 0) {
			throw new IllegalStateException("No se pudieron cargar los datos de prueba: " + resultado.getStderr());
		}
	}
}
