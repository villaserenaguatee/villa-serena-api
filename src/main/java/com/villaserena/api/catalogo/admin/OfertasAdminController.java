package com.villaserena.api.catalogo.admin;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.catalogo.dto.CaracteristicasHabitacion;
import com.villaserena.api.catalogo.dto.GuardarOfertaPeticion;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class OfertasAdminController {
    private final OfertasAdminService service;
    public OfertasAdminController(OfertasAdminService service) { this.service = service; }

    @GetMapping("/ofertas")
    public List<Map<String, Object>> listar() { return service.listar(); }

    @GetMapping("/ofertas/{slug}")
    public Map<String, Object> obtener(@PathVariable String slug) { return service.obtener(slug); }

    @PostMapping("/ofertas/{slug}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@PathVariable String slug, @Valid @RequestBody GuardarOfertaPeticion peticion) {
        return service.guardar(slug, peticion, true);
    }

    @PutMapping("/ofertas/{slug}")
    public Map<String, Object> actualizar(@PathVariable String slug, @Valid @RequestBody GuardarOfertaPeticion peticion) {
        return service.guardar(slug, peticion, false);
    }

    @PatchMapping("/ofertas/{slug}/publicacion")
    public Map<String,Object> publicar(@PathVariable String slug,
            @Valid @RequestBody com.villaserena.api.catalogo.dto.PublicacionOfertaPeticion peticion) {
        return service.publicar(slug,peticion.publicada());
    }

    @GetMapping("/habitaciones/{id}/caracteristicas")
    public Map<String, Object> caracteristicas(@PathVariable long id) { return service.caracteristicas(id); }

    @PutMapping("/habitaciones/{id}/caracteristicas")
    public Map<String, Object> guardarCaracteristicas(@PathVariable long id,
            @Valid @RequestBody CaracteristicasHabitacion peticion) {
        return service.guardarCaracteristicas(id, peticion);
    }
}
