package com.villaserena.api.catalogo.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(operationId = "crearTipoAdmin", summary = "Crear tipo de habitación")
    public TipoHabitacionAdmin crear(@Valid @RequestBody com.villaserena.api.catalogo.dto.CrearTipoHabitacionPeticion peticion) {
        var creado = service.crear(peticion);
        log.info("Tipo de habitación creado: administradorId={}, id={}, nombre={}",
                UsuarioActual.id(), creado.id(), creado.nombre());
        return creado;
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    @Operation(operationId = "actualizarTipoAdmin", summary = "Actualizar tipo de habitación")
    public TipoHabitacionAdmin actualizar(@PathVariable long id,
            @Valid @RequestBody com.villaserena.api.catalogo.dto.EditarTipoHabitacionPeticion peticion) {
        return service.actualizar(id, peticion);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/estado")
    @Operation(operationId = "cambiarEstadoTipoAdmin", summary = "Cambiar estado del tipo de habitación")
    public TipoHabitacionAdmin cambiarEstado(@PathVariable long id,
            @Valid @RequestBody com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion peticion) {
        return service.cambiarEstado(id, peticion);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/ajuste-fin-semana")
    @Operation(operationId = "ajusteFinSemanaAdmin", summary = "Configurar ajuste de fin de semana")
    public TipoHabitacionAdmin ajusteFinSemana(@PathVariable long id,
            @Valid @RequestBody com.villaserena.api.catalogo.dto.AjusteFinSemanaPeticion peticion) {
        return service.ajustarFinSemana(id, peticion);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "detalleTipoAdmin", summary = "Consultar tipo de habitación")
    public TipoHabitacionAdmin obtener(@PathVariable long id) {
        return service.obtener(id);
    }

    @GetMapping
    @Operation(operationId = "listarTiposAdmin", summary = "Listar tipos de habitación")
    public List<TipoHabitacionAdmin> listar(@org.springframework.web.bind.annotation.RequestParam(required = false) String estado) {
        return service.listar(estado);
    }
}
