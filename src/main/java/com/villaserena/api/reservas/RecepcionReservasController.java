package com.villaserena.api.reservas;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.estadia.CheckinService;
import com.villaserena.api.estadia.HabitacionService;
import com.villaserena.api.reservas.dto.AsignarHabitacionPeticion;
import com.villaserena.api.reservas.dto.CancelarReservaPeticion;
import com.villaserena.api.reservas.dto.OpcionDisponibleRecepcion;
import com.villaserena.api.reservas.dto.ReservaDetalle;
import com.villaserena.api.reservas.dto.ReservaRecepcionPeticion;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;
import com.villaserena.api.reservas.dto.VistaPreviaCancelacion;

import jakarta.validation.Valid;

/**
 * Reservas de Recepción: disponibilidad, crear, cancelar y check-in
 * (HU-REC-03, 04, 05 y 12) y asignar la habitación (HU-REC-07, OBJ-2C). La búsqueda,
 * el Gantt y el detalle son de Josué.
 */
@RestController
@RequestMapping("/api/v1/reservas")
@PreAuthorize("hasRole('RECEPCION')")
public class RecepcionReservasController {

    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    private final ReservaDetalleService detalle;
    private final CancelacionService cancelacion;
    private final CheckinService checkin;
    private final HabitacionService habitaciones;

    public RecepcionReservasController(DisponibilidadService disponibilidad, ReservaService reservas,
            ReservaDetalleService detalle, CancelacionService cancelacion, CheckinService checkin,
            HabitacionService habitaciones) {
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.detalle = detalle;
        this.cancelacion = cancelacion;
        this.checkin = checkin;
        this.habitaciones = habitaciones;
    }

    @GetMapping("/disponibilidad")
    public List<OpcionDisponibleRecepcion> disponibilidad(@RequestParam LocalDate entrada,
            @RequestParam LocalDate salida, @RequestParam int huespedes) {
        return disponibilidad.buscar(entrada, salida, huespedes).stream()
                .map(o -> new OpcionDisponibleRecepcion(
                        new TipoHabitacionReferencia(o.tipo().id(), o.tipo().nombre()), o.tipo().capacidad(),
                        o.restantes(), o.cotizacion().noches(), o.cotizacion().total(), o.cotizacion().desglose()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaDetalle crear(@Valid @RequestBody ReservaRecepcionPeticion p) {
        Reserva reserva = reservas.crear(SolicitudReserva.recepcion(p.huespedId(), p.tipoHabitacionId(), p.entrada(),
                p.salida(), p.numeroHuespedes(), p.habitacionId(), UsuarioActual.id()));
        return detalle.de(reserva);
    }

    @GetMapping("/{codigo}/cancelacion")
    public VistaPreviaCancelacion vistaPreviaCancelacion(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return cancelacion.vistaPrevia(codigo);
    }

    @PostMapping("/{codigo}/cancelar")
    public ReservaDetalle cancelar(@PathVariable String codigo, @Valid @RequestBody CancelarReservaPeticion p) {
        ReservaService.validarCodigo(codigo);
        return detalle.de(cancelacion.cancelar(codigo, p.motivo(), UsuarioActual.id()));
    }

    @PutMapping("/{codigo}/habitacion")
    public ReservaDetalle asignarHabitacion(@PathVariable String codigo,
            @Valid @RequestBody AsignarHabitacionPeticion p) {
        ReservaService.validarCodigo(codigo);
        return detalle.de(habitaciones.asignar(codigo, p.habitacionId(), UsuarioActual.id()));
    }

    @PostMapping("/{codigo}/check-in")
    public ReservaDetalle checkIn(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return detalle.de(checkin.hacerCheckin(codigo, UsuarioActual.id()));
    }
}
