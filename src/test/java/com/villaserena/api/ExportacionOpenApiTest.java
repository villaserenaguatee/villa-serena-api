package com.villaserena.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.villaserena.api.reservas.pagos.PasarelaPagos;

import tools.jackson.databind.ObjectMapper;

/** Exporta la implementación de pruebas; el contrato canónico vive en docs. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class})
@ActiveProfiles("test")
class ExportacionOpenApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PasarelaPagos pasarela;

    @Test
    void exportaLasOperacionesRealesSinLevantarUnServicioPublico() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var schema = json.readTree(body);
        assertThat(schema.path("openapi").asText()).startsWith("3.");
        assertThat(schema.path("paths").has("/api/v1/reservas")).isTrue();
        assertThat(schema.path("paths").has("/api/v1/publico/reservas")).isTrue();
        assertThat(schema.path("paths").has("/api/v1/admin/hotel")).isTrue();
        Path output = Path.of("target", "openapi-spring.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(schema));
    }
}
