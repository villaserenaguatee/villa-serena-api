package com.villaserena.api.piso;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.RolEmpleado;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.piso.IncidenciaService.Vista;
import com.villaserena.api.piso.dto.Incidencias;
import com.villaserena.api.piso.dto.ReportarIncidenciaPeticion;
import com.villaserena.api.piso.dto.ResolverIncidenciaPeticion;

import jakarta.validation.Valid;

/**
 * Incidencias de mantenimiento. Reportan Recepción y Mantenimiento/Limpieza de
 * cualquier área; consultan el Administrador (solo lectura) y Mantenimiento con área
 * MANTENIMIENTO o AMBAS, que además toman y resuelven.
 */
@RestController
@RequestMapping("/api/v1/incidencias")
public class IncidenciasController {

    private static final String MANTENIMIENTO = "hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.mantenimiento()";

    private final IncidenciaService incidencias;

    public IncidenciasController(IncidenciaService incidencias) {
        this.incidencias = incidencias;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RECEPCION', 'MANTENIMIENTO_LIMPIEZA')")
    public Incidencias.Creada reportar(@Valid @RequestBody ReportarIncidenciaPeticion p) {
        return incidencias.reportar(p, UsuarioActual.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (" + MANTENIMIENTO + ")")
    public List<Incidencias.Resumen> listar(@RequestParam(required = false) EstadoIncidencia estado,
            @RequestParam(required = false) Long habitacionId, @RequestParam(required = false) Long tecnicoId) {
        Vista vista = RolEmpleado.ADMIN.name().equals(UsuarioActual.rol()) ? Vista.ADMIN : Vista.MANTENIMIENTO;
        return incidencias.listar(vista, estado == null ? null : estado.name(), habitacionId, tecnicoId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (" + MANTENIMIENTO + ")")
    public Incidencias.Detalle detalle(@PathVariable long id) {
        return incidencias.detalle(id);
    }

    @PostMapping("/{id}/tomar")
    @PreAuthorize(MANTENIMIENTO)
    public Incidencias.Detalle tomar(@PathVariable long id) {
        return incidencias.tomar(id, UsuarioActual.id());
    }

    @PostMapping("/{id}/resolver")
    @PreAuthorize(MANTENIMIENTO)
    public Incidencias.Detalle resolver(@PathVariable long id, @Valid @RequestBody ResolverIncidenciaPeticion p) {
        return incidencias.resolver(id, UsuarioActual.id(), p.solucion());
    }

    /** Estados de una incidencia (documento 07 §8). */
    public enum EstadoIncidencia {
        REPORTADA, EN_PROCESO, RESUELTA
    }
}
