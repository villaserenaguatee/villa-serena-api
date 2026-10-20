package com.villaserena.api.auth.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.EstadoEmpleado;
import com.villaserena.api.auth.RolEmpleado;
import com.villaserena.api.auth.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/empleados")
@PreAuthorize("hasRole('ADMIN')")
public class EmpleadosAdminController {
    private static final Logger log = LoggerFactory.getLogger(EmpleadosAdminController.class);
    private final AdministracionEmpleados empleados;

    public EmpleadosAdminController(AdministracionEmpleados empleados) { this.empleados = empleados; }

    @GetMapping
    @Operation(operationId = "listarEmpleadosAdmin", summary = "Lista empleados por rol y estado")
    public List<EmpleadoAdmin> listar(@RequestParam(required = false) RolEmpleado rol,
            @RequestParam(required = false) EstadoEmpleado estado) {
        return empleados.listar(rol, estado);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crearEmpleadoAdmin", summary = "Crea un empleado con contraseña temporal")
    public EmpleadoCreado crear(@Valid @RequestBody CrearEmpleadoPeticion peticion) {
        var creado = empleados.crear(peticion);
        log.info("Empleado creado: administradorId={}, empleadoId={}", UsuarioActual.id(), creado.empleado().id());
        return creado;
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "editarEmpleadoAdmin", summary = "Edita los datos y permisos de un empleado")
    public EmpleadoAdmin editar(@PathVariable long id, @Valid @RequestBody EditarEmpleadoPeticion peticion) {
        var editado = empleados.editar(id, UsuarioActual.id(), peticion);
        log.info("Empleado editado: administradorId={}, empleadoId={}", UsuarioActual.id(), id);
        return editado;
    }

    @PatchMapping("/{id}/estado")
    @Operation(operationId = "estadoEmpleadoAdmin", summary = "Activa o desactiva un empleado")
    public EmpleadoAdmin estado(@PathVariable long id, @Valid @RequestBody EstadoEmpleadoPeticion peticion) {
        var actualizado = empleados.cambiarEstado(id, UsuarioActual.id(), peticion.estado());
        log.info("Estado de empleado actualizado: administradorId={}, empleadoId={}, estado={}",
                UsuarioActual.id(), id, actualizado.estado());
        return actualizado;
    }

    @PostMapping("/{id}/restablecer-contrasena")
    @Operation(operationId = "restablecerContrasenaAdmin", summary = "Genera una nueva contraseña temporal")
    public ContrasenaRestablecida restablecer(@PathVariable long id) {
        var restablecida = empleados.restablecer(id);
        log.info("Contraseña de empleado restablecida: administradorId={}, empleadoId={}", UsuarioActual.id(), id);
        return restablecida;
    }
}
