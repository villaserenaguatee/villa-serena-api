package com.villaserena.api.hotel;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.DisponibilidadService;
import com.villaserena.api.reservas.TipoHabitacionInfo;
import com.villaserena.api.reservas.dto.TipoHabitacionPublico;

/** Web pública sin sesión: información del hotel y catálogo de tipos (HU-HUE-01 y HU-HUE-02). */
@RestController
@RequestMapping("/api/v1/publico")
public class CatalogoPublicoController {

    private final HotelService hotel;
    private final DisponibilidadService disponibilidad;

    public CatalogoPublicoController(HotelService hotel, DisponibilidadService disponibilidad) {
        this.hotel = hotel;
        this.disponibilidad = disponibilidad;
    }

    @GetMapping("/hotel")
    public HotelPublico hotel() {
        return hotel.publico();
    }

    @GetMapping("/tipos-habitacion")
    public List<TipoHabitacionPublico> tipos() {
        return disponibilidad.catalogo();
    }

    /** Un tipo INACTIVO responde 404, igual que uno inexistente. */
    @GetMapping("/tipos-habitacion/{id}")
    public TipoHabitacionPublico tipo(@PathVariable long id) {
        return disponibilidad.tipo(id).filter(TipoHabitacionInfo::activo).map(disponibilidad::publico)
                .orElseThrow(ApiException::noEncontrado);
    }
}
