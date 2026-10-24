package com.villaserena.api.catalogo.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.catalogo.dto.TemporadaAdmin;
import com.villaserena.api.catalogo.dto.TemporadaPeticion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/temporadas")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class TemporadasAdminController {

    private final TemporadasAdminService servicio;

    public TemporadasAdminController(TemporadasAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "listarTemporadasAdmin", summary = "Listar temporadas")
    public List<TemporadaAdmin> listar() {
        return servicio.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "crearTemporadaAdmin", summary = "Crear temporada")
    public TemporadaAdmin crear(@Valid @RequestBody TemporadaPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "editarTemporadaAdmin", summary = "Reemplazar temporada")
    public TemporadaAdmin editar(@PathVariable long id, @Valid @RequestBody TemporadaPeticion peticion) {
        return servicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "eliminarTemporadaAdmin", summary = "Eliminar temporada")
    public void eliminar(@PathVariable long id) {
        servicio.eliminar(id);
    }
}
