package com.villaserena.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.villaserena.api.auth.admin.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.Validation;

class CorreoPersonalEmpleadoTest {
    @Test void edicionPersonalConservaCredencialesPermisosYSesiones() {
        var repository = mock(EmpleadoRepository.class);
        var tokens = mock(TokenService.class);
        var employee = new Empleado();
        employee.setId(7L); employee.setCorreo("laboral@example.test");
        employee.setCorreoPersonal("anterior@example.test"); employee.setContrasenaHash("hash-conservado");
        employee.setRol(RolEmpleado.RECEPCION);
        when(repository.buscarParaActualizar(7L)).thenReturn(Optional.of(employee));
        var service = new AdministracionEmpleados(repository, mock(PasswordEncoder.class), tokens);
        var edit = new EditarEmpleadoPeticion(); edit.setCorreoPersonal("personal@example.test");
        var result = service.editar(7, 1, edit);
        assertEquals("personal@example.test", result.correoPersonal());
        assertEquals("laboral@example.test", result.correo());
        assertEquals("hash-conservado", employee.getContrasenaHash());
        assertTrue(employee.isDebeCambiarContrasena());
        verifyNoInteractions(tokens);
        var omitted = new EditarEmpleadoPeticion(); omitted.setTelefono("55551234");
        assertEquals("personal@example.test", service.editar(7, 1, omitted).correoPersonal());
        var clear = new EditarEmpleadoPeticion(); clear.setCorreoPersonal(null);
        assertNull(service.editar(7, 1, clear).correoPersonal());
        verifyNoInteractions(tokens);
    }

    @Test void altaSeparaContactoYCorreoLaboralConTemporalGenerada() {
        var repository = mock(EmpleadoRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var tokens = mock(TokenService.class);
        when(encoder.encode(anyString())).thenReturn("hash-generado");
        var service = new AdministracionEmpleados(repository, encoder, tokens);
        var result = service.crear(new CrearEmpleadoPeticion("Persona", "LABORAL@example.test", "55551234", RolEmpleado.RECEPCION, null, "personal@example.test"));
        assertEquals("laboral@example.test", result.empleado().correo());
        assertEquals("personal@example.test", result.empleado().correoPersonal());
        assertNotNull(result.contrasenaTemporal());
        verify(repository).existsByCorreoIgnoreCase("laboral@example.test");
        verify(repository, never()).existsByCorreoIgnoreCase("personal@example.test");
        verify(repository).saveAndFlush(argThat(e -> e.getContrasenaHash().equals("hash-generado") && e.isDebeCambiarContrasena()));
        verifyNoInteractions(tokens);
    }

    @Test void jsonDistingueOmitidoDeNullYRechazaModificarCorreoLaboral() {
        var json = new tools.jackson.databind.ObjectMapper();
        var omitted = json.readValue("{\"telefono\":\"55551234\"}", EditarEmpleadoPeticion.class);
        assertFalse(omitted.correoPersonalInformado());
        var clear = json.readValue("{\"correoPersonal\":null}", EditarEmpleadoPeticion.class);
        assertTrue(clear.correoPersonalInformado()); assertNull(clear.getCorreoPersonal());
        var changed = json.readValue("{\"correoPersonal\":\"personal@example.test\"}", EditarEmpleadoPeticion.class);
        assertTrue(changed.correoPersonalInformado()); assertEquals("personal@example.test", changed.getCorreoPersonal());
        assertThrows(Exception.class, () -> json.readValue("{\"correo\":\"otro@example.test\"}", EditarEmpleadoPeticion.class));
        var legacy = json.readValue("{\"nombreCompleto\":\"Persona\",\"correo\":\"laboral@example.test\",\"telefono\":\"55551234\",\"rol\":\"RECEPCION\"}", CrearEmpleadoPeticion.class);
        assertNull(legacy.correoPersonal()); assertEquals("laboral@example.test", legacy.correo());
    }

    @Test void validaContactoOpcionalSinVolverloCredencial() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new CrearEmpleadoPeticion("Persona", "laboral@example.test", "55551234", RolEmpleado.RECEPCION, null)).isEmpty());
            for (String value : new String[]{"", "no-email", "a @example.test", "a".repeat(150)+"@example.test"}) {
                var edit = new EditarEmpleadoPeticion(); edit.setCorreoPersonal(value);
                assertFalse(validator.validate(edit).isEmpty());
            }
            var edit = new EditarEmpleadoPeticion(); edit.setCorreoPersonal(null);
            assertTrue(validator.validate(edit).isEmpty());
            edit.setCorreoPersonal("personal@example.test");
            assertTrue(validator.validate(edit).isEmpty());
        }
    }
}
