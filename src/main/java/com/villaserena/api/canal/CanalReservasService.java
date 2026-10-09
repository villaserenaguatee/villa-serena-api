package com.villaserena.api.canal;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.canal.dto.ReservaCanalPeticion;
import com.villaserena.api.canal.dto.ReservaCanalRespuesta;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.CanalReserva;
import com.villaserena.api.reservas.DisponibilidadService;
import com.villaserena.api.reservas.Reserva;
import com.villaserena.api.reservas.ReservaRepository;
import com.villaserena.api.reservas.ReservaService;
import com.villaserena.api.reservas.SolicitudReserva;
import com.villaserena.api.reservas.TipoHabitacionInfo;

/**
 * Reservas que llegan de un canal externo (HU-CM-01).
 * <p>
 * No repite reglas: las fechas, la capacidad, el tipo activo y la disponibilidad
 * las valida {@link ReservaService#crear}, igual que la web y Recepción
 * (RN-CM-003). Lo propio del canal es la idempotencia por identificador externo
 * (RN-CM-004), que evita cobrar dos veces cuando el canal reintenta por una
 * respuesta perdida.
 */
@Service
public class CanalReservasService {

    private final ReservaService reservas;
    private final ReservaRepository reservasRepo;
    private final DisponibilidadService disponibilidad;

    public CanalReservasService(ReservaService reservas, ReservaRepository reservasRepo,
            DisponibilidadService disponibilidad) {
        this.reservas = reservas;
        this.reservasRepo = reservasRepo;
        this.disponibilidad = disponibilidad;
    }

    /** Resultado: la reserva y si se creó ahora (201) o ya existía (200). */
    public record Resultado(ReservaCanalRespuesta reserva, boolean creada) {
    }

    @Transactional
    public Resultado recibir(CanalReserva canal, ReservaCanalPeticion peticion) {
        Optional<Reserva> existente = reservasRepo.findByCanalAndIdentificadorExterno(canal,
                peticion.identificadorExterno());
        if (existente.isPresent()) {
            return new Resultado(respuesta(existente.get()), false);
        }
        try {
            Reserva reserva = reservas.crear(SolicitudReserva.canal(canal, peticion.identificadorExterno(),
                    peticion.montoTotal(), peticion.tipoHabitacionId(), peticion.entrada(), peticion.salida(),
                    peticion.numeroHuespedes(), peticion.huesped()));
            return new Resultado(respuesta(reserva), true);
        } catch (DataIntegrityViolationException e) {
            // Dos envíos del mismo identificador a la vez: la restricción única de la
            // tabla rechaza el segundo y aquí se responde con el que sí quedó.
            return new Resultado(respuesta(reservasRepo
                    .findByCanalAndIdentificadorExterno(canal, peticion.identificadorExterno())
                    .orElseThrow(() -> e)), false);
        }
    }

    private ReservaCanalRespuesta respuesta(Reserva reserva) {
        String nombreTipo = disponibilidad.tipo(reserva.getTipoHabitacionId())
                .map(TipoHabitacionInfo::nombre)
                .orElseThrow(ApiException::noEncontrado);
        return ReservaCanalRespuesta.de(reserva, nombreTipo);
    }
}
