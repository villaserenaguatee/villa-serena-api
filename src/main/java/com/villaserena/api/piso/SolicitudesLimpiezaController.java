package com.villaserena.api.piso;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.piso.dto.Solicitudes;

/** Solicitudes de huéspedes para Limpieza: solo MYL con área LIMPIEZA o AMBAS (HU-MYL-04 y 05). */
@RestController
@RequestMapping("/api/v1/limpieza/solicitudes")
@PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
public class SolicitudesLimpiezaController {

    private final SolicitudService solicitudes;

    public SolicitudesLimpiezaController(SolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @GetMapping
    public List<Solicitudes.ParaLimpieza> cola() {
        return solicitudes.cola();
    }

    @PostMapping("/{id}/tomar")
    public Solicitudes.ParaLimpieza tomar(@PathVariable long id) {
        return solicitudes.tomar(id, UsuarioActual.id());
    }

    @PostMapping("/{id}/atender")
    public Solicitudes.ParaLimpieza atender(@PathVariable long id) {
        return solicitudes.atender(id, UsuarioActual.id());
    }
}
