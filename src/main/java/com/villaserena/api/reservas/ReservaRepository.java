package com.villaserena.api.reservas;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    Optional<Reserva> findByCanalAndIdentificadorExterno(CanalReserva canal, String identificadorExterno);

    List<Reserva> findByEstadoAndCanalAndCreadaEnBefore(EstadoReserva estado, CanalReserva canal, Instant limite);
}
