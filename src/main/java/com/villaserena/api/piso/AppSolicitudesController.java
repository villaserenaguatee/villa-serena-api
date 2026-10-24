package com.villaserena.api.piso;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.villaserena.api.piso.SolicitudesHuespedService.Item;
import com.villaserena.api.piso.SolicitudesHuespedService.SolicitudHuesped;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/app/reservas/{codigo}")
@PreAuthorize("hasRole('HUESPED')")
public class AppSolicitudesController {
    private final SolicitudesHuespedService solicitudes;

    public AppSolicitudesController(SolicitudesHuespedService solicitudes) {
        this.solicitudes = solicitudes;
    }

    public record Peticion(@Size(max = 500) String comentario) {}
    public record ArticulosPeticion(@NotEmpty List<@NotNull @Valid Item> articulos) {}

    @GetMapping({"/solicitudes", "/solicitudes-limpieza"})
    public List<SolicitudHuesped> lista(@PathVariable String codigo) {
        return solicitudes.listar(codigo);
    }

    @PostMapping("/solicitudes/limpieza")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudHuesped limpieza(@PathVariable String codigo, @Valid @RequestBody Peticion peticion) {
        return solicitudes.limpieza(codigo, peticion.comentario());
    }

    @PostMapping("/solicitudes/articulos")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudHuesped articulos(@PathVariable String codigo, @Valid @RequestBody ArticulosPeticion peticion) {
        return solicitudes.articulos(codigo, peticion.articulos(), null);
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public SolicitudHuesped cancelar(@PathVariable String codigo, @PathVariable long id) {
        return solicitudes.cancelar(codigo, id);
    }

    // Mantiene el HTTP 200 y la respuesta reducida del consumidor anterior.
    @PostMapping("/solicitudes-limpieza")
    public Map<String, Object> crear(@PathVariable String codigo, @Valid @RequestBody Peticion peticion) {
        var solicitud = solicitudes.limpieza(codigo, peticion.comentario());
        return Map.of("id", solicitud.id(), "estado", solicitud.estado());
    }
}
