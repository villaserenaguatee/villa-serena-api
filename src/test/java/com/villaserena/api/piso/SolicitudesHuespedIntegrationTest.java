package com.villaserena.api.piso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;
import com.villaserena.api.reservas.pagos.PasarelaPagos;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL real y transacciones confirmadas para comprobar carreras y eventos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SolicitudesHuespedIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired EmpleadoRepository empleados;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper json;
    @MockitoBean PasarelaPagos pasarela;
    @MockitoBean SimpMessagingTemplate mensajes;

    long huesped, ajeno, habitacion, reserva, articulo;
    String codigo, autorizacion;

    @BeforeEach
    void preparar() {
        codigo = "VS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        tx.executeWithoutResult(t -> {
            huesped = huesped();
            ajeno = huesped();
            long tipo = jdbc.queryForObject("SELECT min(id) FROM room_types", Long.class);
            habitacion = jdbc.queryForObject("INSERT INTO rooms(number,floor,room_type_id,occupancy) "
                    + "VALUES(?,1,?,'OCUPADA') RETURNING id", Long.class, "T" + codigo.substring(3), tipo);
            reserva = jdbc.queryForObject("""
                    INSERT INTO reservations(code,guest_id,room_type_id,room_id,checkin_date,checkout_date,
                    guest_count,status,channel,total) VALUES(?,?,?,?,'2030-01-01','2030-01-03',1,
                    'EN_ESTADIA','RECEPCION',100) RETURNING id""", Long.class, codigo, huesped, tipo, habitacion);
            articulo = jdbc.queryForObject("INSERT INTO articles(name,maximum_quantity) VALUES(?,2) RETURNING id",
                    Long.class, "Toalla " + codigo);
        });
        autorizacion = "Bearer " + tokens.emitirParaHuesped(huesped).accessToken();
        reset(mensajes);
    }

    @AfterEach
    void limpiar() {
        tx.executeWithoutResult(t -> {
            jdbc.update("DELETE FROM status_history WHERE entity_type='SOLICITUD' AND entity_id IN "
                    + "(SELECT id FROM requests WHERE reservation_id=?)", reserva);
            jdbc.update("DELETE FROM request_items WHERE request_id IN (SELECT id FROM requests WHERE reservation_id=?)", reserva);
            jdbc.update("DELETE FROM requests WHERE reservation_id=?", reserva);
            jdbc.update("DELETE FROM reservation_requests WHERE scope LIKE ?", "%:" + codigo);
            jdbc.update("DELETE FROM reservations WHERE id=?", reserva);
            jdbc.update("DELETE FROM rooms WHERE id=?", habitacion);
            jdbc.update("DELETE FROM articles WHERE id=?", articulo);
            jdbc.update("DELETE FROM refresh_tokens WHERE guest_id IN (?,?)", huesped, ajeno);
            jdbc.update("DELETE FROM guests WHERE id IN (?,?)", huesped, ajeno);
        });
    }

    @Test
    void creaLimpiezaConDtoCompletoSinDatosDeEmpleadoYCancelaSinBorrar() throws Exception {
        JsonNode creada = cuerpo(enviar("/limpieza", "{\"comentario\":\" Después de las diez \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("LIMPIEZA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.comentario").value("Después de las diez"))
                .andExpect(jsonPath("$.articulos").isEmpty()).andExpect(jsonPath("$.creadaEn").isString()));
        assertThat(creada.properties()).hasSize(6);
        long id = creada.get("id").asLong();
        enviar("/" + id + "/cancelar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        assertThat(jdbc.queryForList("SELECT new_status FROM status_history WHERE entity_type='SOLICITUD' "
                + "AND entity_id=? ORDER BY id", String.class, id)).containsExactly("PENDIENTE", "CANCELADA");
        jdbc.update("UPDATE reservations SET status='FINALIZADA' WHERE id=?", reserva);
        mvc.perform(get(ruta()).header("Authorization", autorizacion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("CANCELADA"));
        assertThat(jdbc.queryForObject("SELECT condition FROM rooms WHERE id=?", String.class, habitacion)).isEqualTo("LIMPIA");
    }

    @Test
    void articulosNoExigeClaveYDevuelveDetalleEnCreacionListadoYCancelacion() throws Exception {
        JsonNode creada = cuerpo(enviar("/articulos", peticionArticulos(2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.articulos[0].articuloId").value(articulo))
                .andExpect(jsonPath("$.articulos[0].nombre").value("Toalla " + codigo))
                .andExpect(jsonPath("$.articulos[0].cantidad").value(2))
                .andExpect(jsonPath("$.comentario").value((Object) null)));
        assertThat(creada.properties()).hasSize(6);
        assertThat(creada.get("articulos").get(0).properties()).hasSize(3);
        JsonNode lista = cuerpo(mvc.perform(get(ruta()).header("Authorization", autorizacion)).andExpect(status().isOk()));
        assertThat(lista.get(0)).isEqualTo(creada);
        enviar("/" + creada.get("id").asLong() + "/cancelar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.articulos[0].cantidad").value(2));
        assertThat(jdbc.queryForObject("SELECT maximum_quantity FROM articles WHERE id=?", Integer.class, articulo)).isEqualTo(2);
    }

    @Test
    void validaArticulosAntesDeEscribirYNoEmiteEventosEnErrores() throws Exception {
        for (String body : List.of("{}", "{\"articulos\":[]}", "{\"articulos\":[null]}",
                peticionArticulos(0), peticionArticulos(3), "{\"articulos\":[{\"articuloId\":999999,\"cantidad\":1}]}",
                "{\"articulos\":[{\"articuloId\":" + articulo + ",\"cantidad\":1},{\"articuloId\":" + articulo + ",\"cantidad\":1}]}")) {
            enviar("/articulos", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
        enviar("/limpieza", json.writeValueAsString(Map.of("comentario", "x".repeat(501))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM requests WHERE reservation_id=?", Integer.class, reserva)).isZero();
        verifyNoInteractions(mensajes);
    }

    @Test
    void soloCreaDuranteEstadiaPeroPermiteConsultarAntesYDespues() throws Exception {
        for (String estado : List.of("PENDIENTE_PAGO", "CONFIRMADA", "FINALIZADA", "CANCELADA")) {
            jdbc.update("UPDATE reservations SET status=? WHERE id=?", estado, reserva);
            enviar("/limpieza", "{}").andExpect(status().isConflict());
            enviar("/articulos", peticionArticulos(1)).andExpect(status().isConflict());
            mvc.perform(get(ruta()).header("Authorization", autorizacion)).andExpect(status().isOk());
        }
        jdbc.update("UPDATE reservations SET status='EN_ESTADIA',room_id=NULL WHERE id=?", reserva);
        enviar("/limpieza", "{}").andExpect(status().isConflict());
    }

    @Test
    void protegeLasCuatroOperacionesPorRolYTitularidad() throws Exception {
        long id = cuerpo(enviar("/limpieza", "{}").andExpect(status().isCreated())).get("id").asLong();
        String otra = "Bearer " + tokens.emitirParaHuesped(ajeno).accessToken();
        String empleado = personal("ana.perez@villaserena.test");
        for (String sufijo : List.of("", "/limpieza", "/articulos", "/" + id + "/cancelar")) {
            for (String auth : List.of("", empleado, otra)) {
                var request = sufijo.isEmpty() ? get(ruta()) : post(ruta() + sufijo)
                        .contentType(MediaType.APPLICATION_JSON).content(sufijo.equals("/articulos") ? peticionArticulos(1) : "{}");
                if (!auth.isEmpty()) request.header("Authorization", auth);
                mvc.perform(request).andExpect(status().is(auth.isEmpty() ? 401 : auth.equals(empleado) ? 403 : 404));
            }
        }
        enviar("/999999/cancelar", null).andExpect(status().isNotFound());
        // Una solicitud no puede cancelarse a través de otra reserva propia.
        String otroCodigo = "VS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long otraReserva = jdbc.queryForObject("""
                INSERT INTO reservations(code,guest_id,room_type_id,checkin_date,checkout_date,
                guest_count,status,channel,total) SELECT ?,guest_id,room_type_id,'2030-02-01','2030-02-03',
                1,'CONFIRMADA','RECEPCION',100 FROM reservations WHERE id=? RETURNING id""",
                Long.class, otroCodigo, reserva);
        try {
            mvc.perform(post("/api/v1/app/reservas/" + otroCodigo + "/solicitudes/" + id + "/cancelar")
                    .header("Authorization", autorizacion)).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id=?", String.class, id)).isEqualTo("PENDIENTE");
        } finally { jdbc.update("DELETE FROM reservations WHERE id=?", otraReserva); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_PROCESO", "ATENDIDA", "CANCELADA"})
    void noCancelaSolicitudesFueraDePendiente(String estado) throws Exception {
        long id = cuerpo(enviar("/limpieza", "{}").andExpect(status().isCreated())).get("id").asLong();
        Long empleado = jdbc.queryForObject("SELECT min(id) FROM employees", Long.class);
        jdbc.update("UPDATE requests SET status=?,employee_id=? WHERE id=?", estado, empleado, id);
        reset(mensajes);
        enviar("/" + id + "/cancelar", null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM requests WHERE id=?", String.class, id)).isEqualTo(estado);
        verifyNoInteractions(mensajes);
    }

    @Test
    void conservaRutasAnterioresEIdempotenciaDeArticulos() throws Exception {
        String body = "{\"clave\":\"" + UUID.randomUUID() + "\",\"articulos\":[{\"articuloId\":" + articulo + ",\"cantidad\":1}]}";
        String legacy = "/api/v1/app/reservas/" + codigo + "/solicitudes-articulos";
        JsonNode primera = cuerpo(mvc.perform(post(legacy).header("Authorization", autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));
        JsonNode segunda = cuerpo(mvc.perform(post(legacy).header("Authorization", autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));
        assertThat(segunda).isEqualTo(primera);
        mvc.perform(post("/api/v1/app/reservas/" + codigo + "/solicitudes-limpieza")
                .header("Authorization", autorizacion).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM requests WHERE reservation_id=?", Integer.class, reserva)).isEqualTo(2);
        mvc.perform(get("/api/v1/app/reservas/" + codigo + "/solicitudes-limpieza")
                .header("Authorization", autorizacion)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void soloUnaLimpiezaConcurrentePersisteYElEventoSaleTrasCommit() throws Exception {
        CyclicBarrier salida = new CyclicBarrier(2);
        var a = concurrente(salida, "/limpieza", autorizacion);
        var b = concurrente(salida, "/limpieza", autorizacion);
        var respuestas = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM requests WHERE reservation_id=?", Integer.class, reserva)).isEqualTo(1);
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(mensajes, times(1)).convertAndSend(eq("/topic/solicitudes"), payload.capture());
        assertThat(((Map<?, ?>) payload.getValue()).containsKey("estado")).isTrue();
        long id = jdbc.queryForObject("SELECT id FROM requests WHERE reservation_id=?", Long.class, reserva);
        assertThat(payload.getValue().toString()).contains("PENDIENTE").contains(Long.toString(id));
    }

    @Test
    void cancelarYTomarAlaVezNoPermiteDosTransiciones() throws Exception {
        long id = cuerpo(enviar("/limpieza", "{}").andExpect(status().isCreated())).get("id").asLong();
        CyclicBarrier salida = new CyclicBarrier(2);
        var cancelar = concurrente(salida, "/" + id + "/cancelar", autorizacion);
        String limpieza = personal("luis.garcia@villaserena.test");
        var tomar = CompletableFuture.supplyAsync(() -> {
            try {
                salida.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/limpieza/solicitudes/" + id + "/tomar")
                        .header("Authorization", limpieza)).andReturn().getResponse();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        assertThat(List.of(cancelar.get(20, TimeUnit.SECONDS), tomar.get(20, TimeUnit.SECONDS)))
                .extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM status_history WHERE entity_type='SOLICITUD' "
                + "AND entity_id=?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void solicitudSeComparteConLimpiezaYConservaHistorialDeAtencion() throws Exception {
        long id = cuerpo(enviar("/articulos", peticionArticulos(1)).andExpect(status().isCreated())).get("id").asLong();
        String limpieza = personal("luis.garcia@villaserena.test");
        mvc.perform(get("/api/v1/limpieza/solicitudes/" + id).header("Authorization", limpieza))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articulos[0].cantidad").value(1));
        for (String paso : List.of("tomar", "atender")) {
            mvc.perform(post("/api/v1/limpieza/solicitudes/" + id + "/" + paso).header("Authorization", limpieza))
                    .andExpect(status().isOk());
        }
        mvc.perform(get(ruta()).header("Authorization", autorizacion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ATENDIDA"))
                .andExpect(jsonPath("$[0].empleadoACargo").doesNotExist());
    }

    @Test
    void rollbackNoDejaSolicitudesNiHistorialNiEventos() {
        tx.executeWithoutResult(t -> {
            try { enviar("/limpieza", "{}").andExpect(status().isCreated()); }
            catch (Exception e) { throw new IllegalStateException(e); }
            t.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM requests WHERE reservation_id=?", Integer.class, reserva)).isZero();
        verifyNoInteractions(mensajes);
    }

    @Test
    void rechazaIdentificadoresMalFormados() throws Exception {
        mvc.perform(get("/api/v1/app/reservas/invalido/solicitudes").header("Authorization", autorizacion))
                .andExpect(status().isBadRequest());
        enviar("/0/cancelar", null).andExpect(status().isBadRequest());
    }

    private CompletableFuture<MockHttpServletResponse> concurrente(CyclicBarrier salida, String sufijo, String auth) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                salida.await(10, TimeUnit.SECONDS);
                return mvc.perform(post(ruta() + sufijo).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
    private long huesped() {
        return jdbc.queryForObject("""
                INSERT INTO guests(full_name,email,phone,nationality,document_type,document_number)
                VALUES('Huésped prueba',?,'55555555','GT','DPI','1234567890101') RETURNING id""", Long.class,
                UUID.randomUUID() + "@example.test");
    }
    private String personal(String correo) {
        return "Bearer " + tx.execute(t -> tokens.emitirParaEmpleado(empleados.findByCorreoIgnoreCase(correo).orElseThrow()).accessToken());
    }
    private String ruta() { return "/api/v1/app/reservas/" + codigo + "/solicitudes"; }
    private String peticionArticulos(int cantidad) {
        return "{\"articulos\":[{\"articuloId\":" + articulo + ",\"cantidad\":" + cantidad + "}]}";
    }
    private ResultActions enviar(String sufijo, String body) throws Exception {
        var request = post(ruta() + sufijo).header("Authorization", autorizacion);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    private JsonNode cuerpo(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
