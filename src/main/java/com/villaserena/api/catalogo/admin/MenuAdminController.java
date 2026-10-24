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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.catalogo.dto.CategoriaMenuPeticion;
import com.villaserena.api.catalogo.dto.CrearItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EditarItemMenuPeticion;
import com.villaserena.api.catalogo.dto.EstadoCatalogoPeticion;
import com.villaserena.api.catalogo.dto.ItemMenuAdmin;
import com.villaserena.api.roomservice.dto.CategoriaMenu;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/menu")
@PreAuthorize("hasRole('ADMIN')")
public class MenuAdminController {

    private static final Logger log = LoggerFactory.getLogger(MenuAdminController.class);

    private final MenuAdminService servicio;

    public MenuAdminController(MenuAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/categorias")
    public List<CategoriaMenu> listarCategorias() {
        return servicio.listarCategorias();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaMenu crearCategoria(@Valid @RequestBody CategoriaMenuPeticion peticion) {
        CategoriaMenu creada = servicio.crearCategoria(peticion);
        log.info("Categoría de menú creada: administradorId={}, id={}, nombre={}",
                UsuarioActual.id(), creada.id(), creada.nombre());
        return creada;
    }

    @PatchMapping("/categorias/{id}")
    public CategoriaMenu editarCategoria(@PathVariable long id,
            @Valid @RequestBody CategoriaMenuPeticion peticion) {
        CategoriaMenu editada = servicio.editarCategoria(id, peticion);
        log.info("Categoría de menú editada: administradorId={}, id={}, nombre={}",
                UsuarioActual.id(), editada.id(), editada.nombre());
        return editada;
    }

    @GetMapping("/items")
    public List<ItemMenuAdmin> listarItems(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String estado) {
        return servicio.listarItems(categoriaId, estado);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemMenuAdmin crearItem(@Valid @RequestBody CrearItemMenuPeticion peticion) {
        ItemMenuAdmin creado = servicio.crearItem(peticion);
        log.info("Ítem de menú creado: administradorId={}, id={}, nombre={}",
                UsuarioActual.id(), creado.id(), creado.nombre());
        return creado;
    }

    @PatchMapping("/items/{id}")
    public ItemMenuAdmin editarItem(@PathVariable long id,
            @Valid @RequestBody EditarItemMenuPeticion peticion) {
        ItemMenuAdmin editado = servicio.editarItem(id, peticion);
        log.info("Ítem de menú editado: administradorId={}, id={}, nombre={}",
                UsuarioActual.id(), editado.id(), editado.nombre());
        return editado;
    }

    @PatchMapping("/items/{id}/estado")
    public ItemMenuAdmin cambiarEstado(@PathVariable long id,
            @Valid @RequestBody EstadoCatalogoPeticion peticion) {
        ItemMenuAdmin actualizado = servicio.cambiarEstado(id, peticion);
        log.info("Estado de ítem de menú modificado: administradorId={}, id={}, estado={}",
                UsuarioActual.id(), id, actualizado.estado());
        return actualizado;
    }

    @PostMapping("/items/{id}/reactivar")
    public ItemMenuAdmin reactivar(@PathVariable long id) {
        ItemMenuAdmin reactivado = servicio.reactivar(id);
        log.info("Ítem de menú reactivado a DISPONIBLE: administradorId={}, id={}",
                UsuarioActual.id(), id);
        return reactivado;
    }
}
