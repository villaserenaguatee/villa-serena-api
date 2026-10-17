package com.villaserena.api.estadia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.estadia.dto.HabitacionEstado;
import com.villaserena.api.reservas.dto.HabitacionReferencia;

/**
 * Habitaciones de Recepción (OBJ-2C): estado con indicadores, habitaciones
 * asignables y marcar sucia (HU-REC-10, HU-REC-07 y HU-REC-11). La asignación
 * está en {@code PUT /reservas/{codigo}/habitacion}.
 */
@RestController
@RequestMapping("/api/v1/habitaciones")
@PreAuthorize("hasRole('RECEPCION')")
public class HabitacionesController {

    private final HabitacionService habitaciones;

    public HabitacionesController(HabitacionService habitaciones) {
        this.habitaciones = habitaciones;
    }

    @GetMapping
    public List<HabitacionEstado> listar(@RequestParam(required = false) OcupacionHabitacion ocupacion,
            @RequestParam(required = false) CondicionHabitacion condicion,
            @RequestParam(required = false) Long tipoHabitacionId, @RequestParam(required = false) Integer piso) {
        return habitaciones.listar(ocupacion, condicion, tipoHabitacionId, piso);
    }

    @GetMapping("/disponibles")
    public List<HabitacionReferencia> disponibles(@RequestParam long tipoHabitacionId,
            @RequestParam LocalDate entrada, @RequestParam LocalDate salida) {
        return habitaciones.asignables(tipoHabitacionId, entrada, salida);
    }

    @PostMapping("/{id}/marcar-sucia")
    public HabitacionEstado marcarSucia(@PathVariable long id) {
        return habitaciones.marcarSucia(id, UsuarioActual.id());
    }
}
