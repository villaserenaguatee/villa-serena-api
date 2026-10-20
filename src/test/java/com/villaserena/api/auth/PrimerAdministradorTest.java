package com.villaserena.api.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.villaserena.api.config.PropiedadesVillaSerena;

class PrimerAdministradorTest {
    private final EmpleadoRepository empleados = mock(EmpleadoRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private PrimerAdministrador bootstrap(boolean habilitado, String correo, String nombre, String clave) {
        var datos = new PropiedadesVillaSerena.PrimerAdmin(habilitado, correo, nombre, clave);
        return new PrimerAdministrador(empleados, encoder,
                new PropiedadesVillaSerena(null, null, null, null, datos, null, null, null, null, null), jdbc);
    }

    @Test
    void apagadoNoCreaAunqueHayaCredenciales() {
        bootstrap(false, "admin@example.com", "Admin", "Clave-temporal-123").run(null);
        verifyNoInteractions(empleados, jdbc);
    }

    @Test
    void habilitadoValidaCredencialesAntesDeEscribir() {
        assertThatThrownBy(() -> bootstrap(true, "", "Admin", "Clave-temporal-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        for (String clave : new String[]{"corta", "á".repeat(37)}) {
            assertThatThrownBy(() -> bootstrap(true, "admin@example.com", "Admin", clave).run(null))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> bootstrap(true, "correo-invalido", "Admin", "Clave-temporal-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap(true, "a".repeat(145) + "@example.com", "Admin", "Clave-temporal-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(empleados, jdbc);
    }

    @Test
    void creaUnAdminConClaveCifradaYObligaACambiarla() {
        bootstrap(true, " admin@example.com ", " Admin ", "Clave-temporal-123").run(null);
        var captor = ArgumentCaptor.forClass(Empleado.class);
        verify(empleados).save(captor.capture());
        Empleado admin = captor.getValue();
        assertThat(admin.getCorreo()).isEqualTo("admin@example.com");
        assertThat(admin.getNombreCompleto()).isEqualTo("Admin");
        assertThat(admin.getRol()).isEqualTo(RolEmpleado.ADMIN);
        assertThat(admin.isDebeCambiarContrasena()).isTrue();
        assertThat(encoder.matches("Clave-temporal-123", admin.getContrasenaHash())).isTrue();
        verify(jdbc).execute("SELECT pg_advisory_xact_lock(730001)");
    }

    @Test
    void noCreaOtroAdminNiReestableceClaves() {
        when(empleados.existsByRol(RolEmpleado.ADMIN)).thenReturn(true);
        bootstrap(true, "otro@example.com", "Otro", "Clave-temporal-123").run(null);
        verify(empleados, never()).save(any());
    }

    @Test
    void noConvierteUnEmpleadoExistenteEnAdmin() {
        when(empleados.existsByCorreoIgnoreCase("admin@example.com")).thenReturn(true);
        assertThatThrownBy(() -> bootstrap(true, "admin@example.com", "Admin", "Clave-temporal-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(empleados, never()).save(any());
    }
}
