package com.villaserena.api.hotel;

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
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Administración del catálogo de amenidades del hotel (HU-ADM-11). */
@RestController
@RequestMapping("/api/v1/admin/amenidades")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class AmenidadesAdminController {

    private static final Logger log = LoggerFactory.getLogger(AmenidadesAdminController.class);

    private final AmenidadesWifiService servicio;

    public AmenidadesAdminController(AmenidadesWifiService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "listarAmenidadesAdmin", summary = "Listar amenidades")
    public List<AmenidadAdmin> listar() {
        return servicio.listarAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crearAmenidadAdmin", summary = "Crear amenidad")
    public AmenidadAdmin crear(@Valid @RequestBody AmenidadPeticion peticion) {
        AmenidadAdmin creada = servicio.crearAdmin(peticion);
        log.info("Amenidad creada: id={}, administradorId={}", creada.id(), UsuarioActual.id());
        return creada;
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "editarAmenidadAdmin", summary = "Editar amenidad")
    public AmenidadAdmin editar(@PathVariable long id, @Valid @RequestBody EditarAmenidadPeticion peticion) {
        AmenidadAdmin editada = servicio.editarAdmin(id, peticion);
        log.info("Amenidad editada: id={}, administradorId={}", id, UsuarioActual.id());
        return editada;
    }

    @PatchMapping("/{id}/estado")
    @Operation(operationId = "estadoAmenidadAdmin", summary = "Activar o desactivar amenidad")
    public AmenidadAdmin cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoCatalogoPeticion peticion) {
        AmenidadAdmin actualizada = servicio.cambiarEstadoAdmin(id, peticion.estado());
        log.info("Estado de amenidad actualizado: id={}, estado={}, administradorId={}", id, actualizada.estado(), UsuarioActual.id());
        return actualizada;
    }
}
