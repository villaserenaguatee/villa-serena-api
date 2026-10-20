package com.villaserena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.villaserena.api.TestcontainersConfiguration;
import com.villaserena.api.auth.admin.AdministracionEmpleados;
import com.villaserena.api.auth.admin.CrearEmpleadoPeticion;
import com.villaserena.api.auth.admin.EditarEmpleadoPeticion;
import com.villaserena.api.auth.dto.LoginPeticion;
import com.villaserena.api.comun.ApiException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EmpleadosAdminConcurrenciaTest {
    @Autowired AdministracionEmpleados administracion;
    @Autowired AuthService auth;
    @Autowired EmpleadoRepository empleados;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired JdbcTemplate jdbc;
    private final String correo = "concurrencia-" + UUID.randomUUID() + "@example.test";

    @AfterEach
    void limpiarCuentaDePrueba() {
        jdbc.update("DELETE FROM refresh_tokens WHERE employee_id IN (SELECT id FROM employees WHERE email = ?)", correo);
        jdbc.update("DELETE FROM employees WHERE email = ?", correo);
    }

    @Test
    void dosAltasDelMismoCorreoProducenUnaCuentaYUnConflicto() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var tareas = List.of(ejecutor.submit(() -> altaParalela(inicio)), ejecutor.submit(() -> altaParalela(inicio)));
            inicio.countDown();
            assertThat(List.of(tareas.get(0).get(15, TimeUnit.SECONDS), tareas.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees WHERE email = ?", Long.class, correo)).isEqualTo(1);
    }

    @Test
    void edicionesParcialesSimultaneasConservanAmbosCambios() throws Exception {
        long id = administracion.crear(peticion()).empleado().id();
        var nombre = new EditarEmpleadoPeticion();
        nombre.setNombreCompleto("Nombre Actualizado");
        var telefono = new EditarEmpleadoPeticion();
        telefono.setTelefono("12345678");
        var inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var uno = ejecutor.submit(() -> { esperar(inicio); return administracion.editar(id, -1, nombre); });
            var dos = ejecutor.submit(() -> { esperar(inicio); return administracion.editar(id, -1, telefono); });
            inicio.countDown();
            uno.get(15, TimeUnit.SECONDS);
            dos.get(15, TimeUnit.SECONDS);
        }
        Empleado actualizado = empleados.findById(id).orElseThrow();
        assertThat(actualizado.getNombreCompleto()).isEqualTo("Nombre Actualizado");
        assertThat(actualizado.getTelefono()).isEqualTo("12345678");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void renovarEsperaElCambioAdministrativoYNoEmiteSesionTrasRevocacion(boolean restablecer) throws Exception {
        var creado = administracion.crear(peticion());
        long id = creado.empleado().id();
        String refresh = auth.login(new LoginPeticion(correo, creado.contrasenaTemporal())).refreshToken();
        var cambioAplicado = new CountDownLatch(1);
        var confirmarCambio = new CountDownLatch(1);
        var renovacionIniciada = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var cambio = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(s -> {
                if (restablecer) administracion.restablecer(id);
                else administracion.cambiarEstado(id, -1, EstadoEmpleado.INACTIVO);
                cambioAplicado.countDown();
                esperar(confirmarCambio);
                return null;
            }));
            try {
                assertThat(cambioAplicado.await(15, TimeUnit.SECONDS)).isTrue();
                var renovacion = ejecutor.submit(() -> {
                    renovacionIniciada.countDown();
                    try { auth.renovar(refresh); return "SESION_EMITIDA"; }
                    catch (ApiException e) { return e.getCodigo(); }
                });
                assertThat(renovacionIniciada.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> renovacion.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                confirmarCambio.countDown();
                cambio.get(15, TimeUnit.SECONDS);
                assertThat(renovacion.get(15, TimeUnit.SECONDS)).isEqualTo("SESION_VENCIDA");
            } finally {
                confirmarCambio.countDown();
            }
        }
    }

    private int altaParalela(CountDownLatch inicio) {
        esperar(inicio);
        try { administracion.crear(peticion()); return 201; }
        catch (ApiException e) { return e.getEstado().value(); }
    }

    private CrearEmpleadoPeticion peticion() {
        return new CrearEmpleadoPeticion("Persona Concurrente", correo, "5555", RolEmpleado.RECEPCION, null);
    }

    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Se agotó la espera de la prueba.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
