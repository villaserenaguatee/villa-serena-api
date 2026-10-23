package com.villaserena.api.catalogo.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.catalogo.dto.FotosTipoPeticion;
import com.villaserena.api.catalogo.dto.TipoHabitacionAdmin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/tipos-habitacion")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class TiposHabitacionAdminController {

    private static final Logger log = LoggerFactory.getLogger(TiposHabitacionAdminController.class);

    private final TiposHabitacionAdminService service;

    public TiposHabitacionAdminController(TiposHabitacionAdminService service) {
        this.service = service;
    }

    @PutMapping("/{id}/fotos")
    @Operation(operationId = "actualizarFotosTipoAdmin", summary = "Actualizar fotos y principal del tipo")
    public TipoHabitacionAdmin actualizarFotos(@PathVariable long id, @Valid @RequestBody FotosTipoPeticion peticion) {
        var actualizado = service.actualizarFotos(id, peticion);
        log.info("Fotos de tipo de habitación actualizadas: administradorId={}, tipoId={}, cantidadFotos={}",
                UsuarioActual.id(), id, peticion.fotos().size());
        return actualizado;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "detalleTipoAdmin", summary = "Consultar tipo de habitación")
    public TipoHabitacionAdmin obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @GetMapping
    @Operation(operationId = "listarTiposAdmin", summary = "Listar tipos de habitación")
    public List<TipoHabitacionAdmin> listar() {
        return service.listar();
    }
}
