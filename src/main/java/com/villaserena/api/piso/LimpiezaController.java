package com.villaserena.api.piso;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.piso.dto.HabitacionLimpieza;
import com.villaserena.api.piso.dto.ResultadoCondicionHabitacion;

/** Limpieza de habitaciones: solo Mantenimiento/Limpieza con área LIMPIEZA o AMBAS (HU-MYL-01 a 03). */
@RestController
@RequestMapping("/api/v1/limpieza/habitaciones")
@PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
public class LimpiezaController {

    private final LimpiezaService limpieza;

    public LimpiezaController(LimpiezaService limpieza) {
        this.limpieza = limpieza;
    }

    @GetMapping
    public List<HabitacionLimpieza> pendientes() {
        return limpieza.pendientes();
    }

    @PostMapping("/{id}/iniciar")
    public ResultadoCondicionHabitacion iniciar(@PathVariable long id) {
        return limpieza.iniciar(id, UsuarioActual.id());
    }

    @PostMapping("/{id}/interrumpir")
    public ResultadoCondicionHabitacion interrumpir(@PathVariable long id) {
        return limpieza.interrumpir(id, UsuarioActual.id());
    }

    @PostMapping("/{id}/terminar")
    public ResultadoCondicionHabitacion terminar(@PathVariable long id) {
        return limpieza.terminar(id, UsuarioActual.id());
    }
}
