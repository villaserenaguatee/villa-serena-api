package com.villaserena.api.reservas;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    /** Si el correo del huésped tiene alguna reserva (acceso a la app, HU-HUE-08). */
    boolean existsByHuespedId(Long huespedId);

    Optional<Reserva> findByCanalAndIdentificadorExterno(CanalReserva canal, String identificadorExterno);

    List<Reserva> findByEstadoAndCanalAndCreadaEnBefore(EstadoReserva estado, CanalReserva canal, Instant limite);
}
