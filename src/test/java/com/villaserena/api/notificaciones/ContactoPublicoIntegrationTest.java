package com.villaserena.api.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.ConnectException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.thymeleaf.TemplateEngine;

import com.villaserena.api.AlmacenEnMemoria;
import com.villaserena.api.RelojFijoConfig;
import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.ManejadorErrores;
import com.villaserena.api.config.PropiedadesVillaSerena;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** PostgreSQL real y transporte mock: no conecta a SMTP ni envía a buzones reales. */
@SpringBootTest(properties = "CONTACTO_DESTINATARIO=operacion@example.test")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RelojFijoConfig.class, AlmacenEnMemoria.class})
@ActiveProfiles("test")
class ContactoPublicoIntegrationTest {
    private static final String RUTA = "/api/v1/publico/contacto";
    private static final String JSON = """
            {"nombre":"Visitante Ejemplo","correo":"visitante@example.test","mensaje":"Consulta aislada"}
            """;
    private static final AtomicInteger IP = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired TemplateEngine plantillas;
    @Autowired PropiedadesVillaSerena propiedades;
    @Autowired ContactoPublicoController controller;
    @MockitoBean JavaMailSender mail;

    @BeforeEach
    void correoDePruebas() {
        when(mail.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private MockHttpServletRequestBuilder peticion(UUID id) {
        return post(RUTA).header("Idempotency-Key", id).contentType(MediaType.APPLICATION_JSON).content(JSON)
                .with(request -> { request.setRemoteAddr("192.0.2." + IP.incrementAndGet()); return request; });
    }

    private ContactoPublicoController conDestinatario(String valor) {
        return new ContactoPublicoController(jdbc, transacciones, mail, plantillas, propiedades,
                new ContactoCorreoConfig(valor));
    }

    private ContactoPublicoController.Peticion datos() {
        return new ContactoPublicoController.Peticion("Visitante Ejemplo", "visitante@example.test", "Consulta aislada");
    }

    @Test
    void publicoEnviaUnaVezYElSeguimientoSoloExponeEstado() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(peticion(id)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$", aMapWithSize(2)));
        mvc.perform(peticion(id)).andExpect(status().isOk());
        mvc.perform(get(RUTA + "/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.id").value(id.toString()));
        mvc.perform(peticion(id).content(JSON.replace("Consulta aislada", "Otros datos")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONTACTO_DATOS_DISTINTOS"));
        verify(mail, times(1)).send(any(MimeMessage.class));
        assertThat(jdbc.queryForObject("SELECT attempts FROM web_contact WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void destinoInvalidoDevuelve503YRevierteCadaIntentoNuevo() throws Exception {
        for (String valor : List.of("", "invalido", "a@example.test,b@example.test")) {
            UUID id = UUID.randomUUID();
            MockMvc sinDestino = MockMvcBuilders.standaloneSetup(conDestinatario(valor))
                    .setControllerAdvice(new ManejadorErrores()).build();
            sinDestino.perform(peticion(id)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.codigo").value("CONTACTO_NO_DISPONIBLE"))
                    .andExpect(jsonPath("$.mensaje").value("El envío de mensajes no está disponible temporalmente."));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM web_contact WHERE id=?", Integer.class, id)).isZero();
        }
        verify(mail, never()).send(any(MimeMessage.class));
    }

    @Test
    void sinDestinoConservaResultadosPreviosYNoIncrementaFallidos() {
        UUID id = UUID.randomUUID();
        controller.enviar(datos(), id);
        ContactoPublicoController sinDestino = conDestinatario("");
        for (String estado : List.of("ENVIADO", "INCIERTO", "ENVIANDO")) {
            jdbc.update("UPDATE web_contact SET status=?,updated_at=now() WHERE id=?", estado, id);
            assertThat(sinDestino.enviar(datos(), id).estado()).isEqualTo(estado);
            assertThat(sinDestino.estado(id).estado()).isEqualTo(estado);
        }
        jdbc.update("UPDATE web_contact SET status='FALLIDO',attempts=3 WHERE id=?", id);
        assertThrows(ApiException.class, () -> sinDestino.enviar(datos(), id));
        assertThat(jdbc.queryForObject("SELECT attempts FROM web_contact WHERE id=?", Integer.class, id)).isEqualTo(3);
        assertThat(sinDestino.estado(id).estado()).isEqualTo("FALLIDO");
        verify(mail, times(1)).send(any(MimeMessage.class));
    }

    static Stream<MailException> rechazosPrevios() {
        return Stream.of(new MailAuthenticationException("Rechazo simulado"),
                new MailSendException("Conexión simulada", new ConnectException("Conexión rechazada")));
    }

    @ParameterizedTest
    @MethodSource("rechazosPrevios")
    void conexionYAutenticacionFallidasPermitenSoloReintentoExplicito(MailException fallo) {
        doThrow(fallo).doNothing().when(mail).send(any(MimeMessage.class));
        UUID id = UUID.randomUUID();
        assertThat(controller.enviar(datos(), id).estado()).isEqualTo("FALLIDO");
        verify(mail, times(1)).send(any(MimeMessage.class));
        assertThat(controller.enviar(datos(), id).estado()).isEqualTo("ENVIADO");
        assertThat(controller.enviar(datos(), id).estado()).isEqualTo("ENVIADO");
        verify(mail, times(2)).send(any(MimeMessage.class));
        assertThat(jdbc.queryForObject("SELECT attempts FROM web_contact WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM web_contact WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void resultadoInciertoNoSeReenviaYUnEnvioAntiguoSoloCambiaSuEstado() {
        doThrow(new MailSendException("Resultado simulado sin confirmar")).when(mail).send(any(MimeMessage.class));
        UUID id = UUID.randomUUID();
        assertThat(controller.enviar(datos(), id).estado()).isEqualTo("INCIERTO");
        assertThat(controller.enviar(datos(), id).estado()).isEqualTo("INCIERTO");
        jdbc.update("UPDATE web_contact SET status='ENVIANDO',updated_at=now()-interval '3 minutes' WHERE id=?", id);
        assertThat(controller.estado(id).estado()).isEqualTo("INCIERTO");
        verify(mail, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void dosSolicitudesConcurrentesSoloInicianUnSmtp() throws Exception {
        CountDownLatch iniciado = new CountDownLatch(1), terminar = new CountDownLatch(1);
        doAnswer(i -> { iniciado.countDown(); assertTrue(terminar.await(10, TimeUnit.SECONDS)); return null; })
                .when(mail).send(any(MimeMessage.class));
        UUID id = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primero = pool.submit(() -> controller.enviar(datos(), id));
            try {
                assertTrue(iniciado.await(10, TimeUnit.SECONDS));
                var segundo = pool.submit(() -> controller.enviar(datos(), id));
                assertThat(segundo.get(10, TimeUnit.SECONDS).estado()).isEqualTo("ENVIANDO");
            } finally {
                terminar.countDown();
            }
            assertThat(primero.get(10, TimeUnit.SECONDS).estado()).isEqualTo("ENVIADO");
        }
        verify(mail, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void cambiarCorreoPublicoNoCambiaDestinoRemitenteNiTransporte() throws Exception {
        String previo = jdbc.queryForObject("SELECT email FROM hotel_configuration WHERE id=1", String.class);
        try {
            controller.enviar(datos(), UUID.randomUUID());
            jdbc.update("UPDATE hotel_configuration SET email='publico@example.test' WHERE id=1");
            controller.enviar(datos(), UUID.randomUUID());
            var mensajes = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mail, times(2)).send(mensajes.capture());
            for (MimeMessage mensaje : mensajes.getAllValues()) {
                assertThat(((InternetAddress) mensaje.getAllRecipients()[0]).getAddress()).isEqualTo("operacion@example.test");
                assertThat(((InternetAddress) mensaje.getReplyTo()[0]).getAddress()).isEqualTo("visitante@example.test");
                assertThat(mensaje.getFrom()[0].toString()).isEqualTo(new InternetAddress(propiedades.notificaciones().remitente()).toString());
            }
        } finally {
            jdbc.update("UPDATE hotel_configuration SET email=? WHERE id=1", previo);
        }
    }

    @Test
    void rechazaDatosYCabecerasInvalidasYConsultaDesconocida() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(post(RUTA).header("Idempotency-Key", "no-uuid").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadRequest());
        for (String contenido : List.of(JSON.replace("visitante@example.test", "invalido"),
                JSON.replace("Visitante Ejemplo", "Nombre\\r\\nBcc:otro@example.test"),
                JSON.replace("Consulta aislada", "a".repeat(5001)), JSON.replace("Consulta aislada", " "))) {
            mvc.perform(peticion(UUID.randomUUID()).content(contenido)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(RUTA + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        verify(mail, never()).send(any(MimeMessage.class));
    }
}
