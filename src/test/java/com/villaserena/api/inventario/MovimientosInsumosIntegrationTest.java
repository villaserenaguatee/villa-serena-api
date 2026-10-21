package com.villaserena.api.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.EmpleadoRepository;
import com.villaserena.api.auth.TokenService;

import tools.jackson.databind.ObjectMapper;

/** Contrato existente y transacciones reales, incluidas dos solicitudes concurrentes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MovimientosInsumosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EmpleadoRepository empleados;
    @Autowired TokenService tokens;
    @Autowired TransactionTemplate transacciones;

    private UUID insumo;
    private long administrador;
    private String token;
    private final Set<UUID> registros = new HashSet<>();

    @BeforeEach
    void preparar() {
        administrador = jdbc.queryForObject("SELECT id  FROM employees WHERE role='ADMIN' ORDER BY id LIMIT 1", Long.class);
        token = token("ADMIN");
        insumo = UUID.randomUUID();
        registros.add(insumo);
        String datos = "{\"nombre\":\"Insumo de prueba\",\"stock\":10,\"unidad\":\"unidad\",\"costoUnitario\":2}";
        jdbc.update("""
                INSERT INTO operation_records(type,id,data,creation,created_by)
                VALUES ('insumos',?,?::jsonb,?::jsonb,?)""", insumo, datos, datos, administrador);
    }

    @AfterEach
    void limpiar() {
        for (UUID id : registros) {
            jdbc.update("DELETE FROM operations_audit WHERE record_id=?", id);
            jdbc.update("DELETE FROM operation_records WHERE id=?", id);
        }
        registros.clear();
    }

    @Test
    void entradaConservaFormatoCamposAdicionalesAuditoriaYConsulta() throws Exception {
        UUID id = nuevoId();
        var datos = new java.util.LinkedHashMap<>(datos("entrada", "2.50"));
        datos.put("observacion", "Conservar este campo");
        datos.put("id", "dato-ignorado");
        datos.put("version", 99);
        var respuesta = guardar(id, 0, datos, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cantidad").value("2.50"))
                .andExpect(jsonPath("$.observacion").value("Conservar este campo"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(json.readTree(respuesta).get("fecha").asString())).isBefore(Instant.now().plusSeconds(1));
        assertThat(stock()).isEqualByComparingTo("12.50");
        assertThat(versionInsumo()).isEqualTo(2);
        assertThat(auditorias(insumo)).isEqualTo(1);
        assertThat(auditorias(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT employee_id AS \"empleado_id\" FROM operations_audit WHERE record_id=?", Long.class, id))
                .isEqualTo(administrador);
        mvc.perform(get("/api/v1/gestion/movimientos").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].tipo").value("entrada"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"salida", "merma"})
    void salidaYMermaDescuentanExistencias(String tipo) throws Exception {
        guardar(nuevoId(), 0, datos(tipo, new BigDecimal("2.75")), token).andExpect(status().isOk());
        assertThat(stock()).isEqualByComparingTo("7.25");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100000000", "no-es-numero"})
    void cantidadesInvalidasNoCambianExistencias(String cantidad) throws Exception {
        UUID id = nuevoId();
        guardar(id, 0, datos("salida", cantidad), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        assertSinCambios(id);
    }

    @Test
    void salidaInsuficienteNoDejaMovimientoNiAuditoria() throws Exception {
        UUID id = nuevoId();
        guardar(id, 0, datos("salida", 11), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
        assertSinCambios(id);
    }

    @Test
    void validaTipoMotivoEInsumo() throws Exception {
        var base = new java.util.LinkedHashMap<>(datos("ajuste", 1));
        guardar(nuevoId(), 0, base, token).andExpect(status().isBadRequest());
        base.put("tipo", "entrada"); base.put("motivo", " ");
        guardar(nuevoId(), 0, base, token).andExpect(status().isBadRequest());
        base.put("motivo", "Reposición"); base.put("insumoId", UUID.randomUUID().toString());
        guardar(nuevoId(), 0, base, token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Selecciona un registro existente de insumos."));
        base.put("insumoId", "invalido");
        guardar(nuevoId(), 0, base, token).andExpect(status().isBadRequest());
        assertThat(stock()).isEqualByComparingTo("10");
    }

    @Test
    void reenvioIdenticoDevuelveElOriginalSinAplicarOtraVez() throws Exception {
        UUID id = nuevoId();
        var datos = datos("salida", 3);
        String primera = guardar(id, 0, datos, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String repetida = guardar(id, 0, datos, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida)).isEqualTo(json.readTree(primera));
        assertThat(stock()).isEqualByComparingTo("7");
        assertThat(auditorias(id)).isEqualTo(1);
        assertThat(auditorias(insumo)).isEqualTo(1);
    }

    @Test
    void versionYDatosDeCreacionProtegenMovimientosInmutables() throws Exception {
        UUID id = nuevoId();
        guardar(id, 0, datos("entrada", 2), token).andExpect(status().isOk());
        guardar(id, 0, datos("entrada", 3), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSION_CAMBIADA"));
        guardar(id, 1, datos("entrada", 3), token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Los movimientos son inmutables."));
        // La versión se comprueba antes que los campos del movimiento.
        guardar(nuevoId(), 7, Map.of(), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSION_CAMBIADA"));
        assertThat(stock()).isEqualByComparingTo("12");
    }

    @Test
    void soloAdministracionPuedeRegistrarMovimientos() throws Exception {
        for (String rol : List.of("RECEPCION", "ROOM_SERVICE", "MANTENIMIENTO_LIMPIEZA")) {
            UUID id = nuevoId();
            guardar(id, 0, datos("entrada", 1), token(rol)).andExpect(status().isForbidden());
            assertSinCambios(id);
        }
        mvc.perform(post("/api/v1/gestion/movimientos").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", nuevoId(), "version", 0, "datos", datos("entrada", 1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dosSalidasConcurrentesNoPuedenGastarElMismoStock() throws Exception {
        var respuestas = simultaneas(List.of(nuevoId(), nuevoId()));
        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        assertThat(stock()).isEqualByComparingTo("3");
        assertThat(auditorias(insumo)).isEqualTo(1);
    }

    @Test
    void dosReenviosConcurrentesAplicanUnSoloMovimiento() throws Exception {
        UUID id = nuevoId();
        var respuestas = simultaneas(List.of(id, id));
        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsOnly(200);
        assertThat(stock()).isEqualByComparingTo("3");
        assertThat(auditorias(id)).isEqualTo(1);
        assertThat(auditorias(insumo)).isEqualTo(1);
    }

    @Test
    void unaCompraYUnaSalidaConcurrentesConservanAmbosCambios() throws Exception {
        UUID compra = nuevoId();
        UUID movimiento = nuevoId();
        var barrera = new CyclicBarrier(2);
        var compraFutura = CompletableFuture.supplyAsync(() -> {
            try {
                barrera.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/gestion/compras").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "id", compra, "version", 0, "datos", Map.of(
                                        "numeroFactura", compra.toString(), "proveedor", "Proveedor ficticio",
                                        "fecha", "2026-10-19", "estado", "pagada", "insumoId", insumo.toString(),
                                        "cantidad", 5, "costoUnitario", 2)))))
                        .andReturn().getResponse();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        var movimientoFuturo = CompletableFuture.supplyAsync(() -> {
            try {
                barrera.await(10, TimeUnit.SECONDS);
                return guardar(movimiento, 0, datos("salida", 7), token).andReturn().getResponse();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        assertThat(compraFutura.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        assertThat(movimientoFuturo.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(versionInsumo()).isEqualTo(3);
        assertThat(auditorias(insumo)).isEqualTo(2);
    }

    @Test
    void falloDeAuditoriaRevierteMovimientoYExistencias() throws Exception {
        UUID id = nuevoId();
        jdbc.update("""
                INSERT INTO operations_audit(type,record_id,version,employee_id,new)
                VALUES ('movimientos',?,1,?,'{}')""", id, administrador);
        guardar(id, 0, datos("salida", 3), token).andExpect(status().isInternalServerError());
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(versionInsumo()).isEqualTo(1);
        assertThat(auditorias(insumo)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*)  FROM operation_records WHERE type='movimientos' AND id=?",
                Integer.class, id)).isZero();
    }

    private List<MockHttpServletResponse> simultaneas(List<UUID> ids) throws Exception {
        var barrera = new CyclicBarrier(ids.size());
        List<CompletableFuture<MockHttpServletResponse>> futuros = new ArrayList<>();
        for (UUID id : ids) {
            futuros.add(CompletableFuture.supplyAsync(() -> {
                try {
                    barrera.await(10, TimeUnit.SECONDS);
                    return guardar(id, 0, datos("salida", 7), token).andReturn().getResponse();
                } catch (Exception e) { throw new IllegalStateException(e); }
            }));
        }
        List<MockHttpServletResponse> resultados = new ArrayList<>();
        for (var futuro : futuros) resultados.add(futuro.get(30, TimeUnit.SECONDS));
        return resultados;
    }

    private UUID nuevoId() { UUID id = UUID.randomUUID(); registros.add(id); return id; }

    private Map<String,Object> datos(String tipo, Object cantidad) {
        return Map.of("insumoId", insumo.toString(), "tipo", tipo, "cantidad", cantidad, "motivo", "Movimiento de prueba");
    }

    private ResultActions guardar(UUID id, long version, Map<String,Object> datos, String credencial) throws Exception {
        return mvc.perform(post("/api/v1/gestion/movimientos").header("Authorization", credencial)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", id, "version", version, "datos", datos))));
    }

    private String token(String rol) {
        String correo = jdbc.queryForObject("SELECT email AS \"correo\" FROM employees WHERE role=? ORDER BY id LIMIT 1", String.class, rol);
        return "Bearer " + tokens.emitirParaEmpleado(transacciones.execute(t -> empleados.findByCorreoIgnoreCase(correo).orElseThrow()))
                .accessToken();
    }

    private BigDecimal stock() {
        return jdbc.queryForObject("SELECT (data->>'stock')::numeric  FROM operation_records WHERE type='insumos' AND id=?",
                BigDecimal.class, insumo);
    }

    private long versionInsumo() {
        return jdbc.queryForObject("SELECT version  FROM operation_records WHERE type='insumos' AND id=?", Long.class, insumo);
    }

    private int auditorias(UUID id) {
        return jdbc.queryForObject("SELECT count(*)  FROM operations_audit WHERE record_id=?", Integer.class, id);
    }

    private void assertSinCambios(UUID movimiento) {
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(auditorias(movimiento)).isZero();
        assertThat(auditorias(insumo)).isZero();
    }
}
