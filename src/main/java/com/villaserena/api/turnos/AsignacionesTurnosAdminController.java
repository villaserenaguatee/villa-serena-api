package com.villaserena.api.turnos;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Administración de asignaciones semanales de turnos a empleados (HU-ADM-12). */
@RestController
@RequestMapping("/api/v1/admin/asignaciones-turnos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class AsignacionesTurnosAdminController {

    private static final Logger log = LoggerFactory.getLogger(AsignacionesTurnosAdminController.class);

    private final TurnosService servicio;

    public AsignacionesTurnosAdminController(TurnosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "listarAsignacionesTurnosAdmin", summary = "Consultar vista semanal de turnos")
    public List<AsignacionTurno> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return servicio.listarAsignaciones(inicio, fin);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "asignarTurnosAdmin", summary = "Asignar turnos por fechas")
    public List<AsignacionTurno> asignar(@Valid @RequestBody AsignarTurnosPeticion peticion) {
        List<AsignacionTurno> creadas = servicio.asignarTurnos(peticion);
        log.info("Turnos asignados: cantidad={}, empleadoId={}, turnoId={}, administradorId={}",
                creadas.size(), peticion.empleadoId(), peticion.turnoId(), UsuarioActual.id());
        return creadas;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "quitarAsignacionTurnoAdmin", summary = "Quitar asignación de turno")
    public void eliminar(@PathVariable long id) {
        servicio.eliminarAsignacion(id);
        log.info("Asignación de turno eliminada: id={}, administradorId={}", id, UsuarioActual.id());
    }
}
