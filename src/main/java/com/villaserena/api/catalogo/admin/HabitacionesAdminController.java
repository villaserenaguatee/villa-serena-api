package com.villaserena.api.catalogo.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.catalogo.dto.CrearHabitacionPeticion;
import com.villaserena.api.catalogo.dto.EditarHabitacionPeticion;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import com.villaserena.api.catalogo.dto.HabitacionAdmin;
import com.villaserena.api.catalogo.dto.FotosHabitacionRespuesta;
import com.villaserena.api.catalogo.dto.FotosTipoPeticion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/habitaciones")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class HabitacionesAdminController {

    private static final Logger log = LoggerFactory.getLogger(HabitacionesAdminController.class);

    private final HabitacionesAdminService service;

    public HabitacionesAdminController(HabitacionesAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listarHabitacionesAdmin", summary = "Listar catálogo de habitaciones")
    public List<HabitacionAdmin> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long tipoHabitacionId) {
        return service.listar(estado, tipoHabitacionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crearHabitacionAdmin", summary = "Crear habitación")
    public HabitacionAdmin crear(@Valid @RequestBody CrearHabitacionPeticion peticion) {
        var creada = service.crear(peticion);
        log.info("Habitación física creada: administradorId={}, id={}, numero={}, tipoId={}",
                UsuarioActual.id(), creada.id(), creada.numero(), creada.tipoHabitacionId());
        return creada;
    }

    @GetMapping("/{id}/fotos")
    public FotosHabitacionRespuesta fotos(@PathVariable long id) { return service.fotos(id); }

    @PutMapping("/{id}/fotos")
    public FotosHabitacionRespuesta actualizarFotos(@PathVariable long id, @Valid @RequestBody FotosTipoPeticion peticion) { return service.actualizarFotos(id, peticion); }

    @GetMapping("/{id}")
    @Operation(operationId = "detalleHabitacionAdmin", summary = "Consultar habitación")
    public HabitacionAdmin obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "actualizarHabitacionAdmin", summary = "Actualizar habitación")
    public HabitacionAdmin actualizar(@PathVariable long id, @Valid @RequestBody EditarHabitacionPeticion peticion) {
        return service.actualizar(id, peticion);
    }

    @PatchMapping("/{id}/estado")
    @Operation(operationId = "cambiarEstadoHabitacionAdmin", summary = "Cambiar estado de la habitación")
    public HabitacionAdmin cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoCatalogoPeticion peticion) {
        return service.cambiarEstado(id, peticion);
    }
}
