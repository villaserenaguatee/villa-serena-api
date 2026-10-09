package com.villaserena.api.reservas;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.reservas.dto.EstadoReservaPublico;
import com.villaserena.api.reservas.dto.OpcionDisponiblePublica;
import com.villaserena.api.reservas.dto.PagoIniciado;
import com.villaserena.api.reservas.dto.ReservaWebCreada;
import com.villaserena.api.reservas.dto.ReservaWebPeticion;
import com.villaserena.api.reservas.dto.TipoHabitacionReferencia;
import com.villaserena.api.reservas.stripe.PagoStripeService;

import jakarta.validation.Valid;

/** Web pública sin sesión: disponibilidad, reserva y pago (HU-HUE-03 a HU-HUE-06). */
@RestController
@RequestMapping("/api/v1/publico")
public class PublicoReservasController {

    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    private final PagoStripeService pagos;
    private final java.time.Clock reloj;

    public PublicoReservasController(DisponibilidadService disponibilidad, ReservaService reservas,
            PagoStripeService pagos, java.time.Clock reloj) {
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @GetMapping("/disponibilidad")
    public List<OpcionDisponiblePublica> disponibilidad(@RequestParam LocalDate entrada,
            @RequestParam LocalDate salida, @RequestParam int huespedes) {
        return disponibilidad.buscar(entrada, salida, huespedes).stream()
                .map(o -> OpcionDisponiblePublica.de(o.tipoPublico(), o.cotizacion()))
                .toList();
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaWebCreada crear(@Valid @RequestBody ReservaWebPeticion p) {
        Reserva reserva = reservas.crear(SolicitudReserva.web(p.tipoHabitacionId(), p.entrada(), p.salida(),
                p.numeroHuespedes(), p.huesped()));
        String tipo = disponibilidad.tipo(reserva.getTipoHabitacionId()).map(TipoHabitacionInfo::nombre).orElse("");
        return new ReservaWebCreada(reserva.getCodigo(), reserva.getEstado(),
                new TipoHabitacionReferencia(reserva.getTipoHabitacionId(), tipo), reserva.getFechaEntrada(),
                reserva.getFechaSalida(), reserva.noches(), reserva.getNumeroHuespedes(), reserva.getTotal(),
                OffsetDateTime.ofInstant(pagos.vencimientoPago(reserva), reloj.getZone()));
    }

    @PostMapping("/reservas/{codigo}/pago")
    public PagoIniciado iniciarPago(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return pagos.iniciarPagoReserva(codigo);
    }

    @GetMapping("/reservas/{codigo}/estado")
    public EstadoReservaPublico estado(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return pagos.estadoPublico(codigo);
    }
}
