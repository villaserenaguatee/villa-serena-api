package com.villaserena.api.turnos;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Administración del catálogo de turnos de trabajo (HU-ADM-12). */
@RestController
@RequestMapping("/api/v1/admin/turnos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class TurnosAdminController {

    private static final Logger log = LoggerFactory.getLogger(TurnosAdminController.class);

    private final TurnosService servicio;

    public TurnosAdminController(TurnosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "listarTurnosAdmin", summary = "Listar turnos")
    public List<TurnoAdmin> listar() {
        return servicio.listarTurnos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crearTurnoAdmin", summary = "Crear turno")
    public TurnoAdmin crear(@Valid @RequestBody TurnoPeticion peticion) {
        TurnoAdmin creado = servicio.crearTurno(peticion);
        log.info("Turno creado: id={}, administradorId={}", creado.id(), UsuarioActual.id());
        return creado;
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "editarTurnoAdmin", summary = "Editar turno")
    public TurnoAdmin editar(@PathVariable long id, @Valid @RequestBody EditarTurnoPeticion peticion) {
        TurnoAdmin editado = servicio.editarTurno(id, peticion);
        log.info("Turno editado: id={}, administradorId={}", id, UsuarioActual.id());
        return editado;
    }

    @PostMapping("/{id}/desactivar")
    @Operation(operationId = "desactivarTurnoAdmin", summary = "Desactivar turno")
    public TurnoAdmin desactivar(@PathVariable long id) {
        TurnoAdmin desactivado = servicio.desactivarTurno(id);
        log.info("Turno desactivado: id={}, administradorId={}", id, UsuarioActual.id());
        return desactivado;
    }
}
