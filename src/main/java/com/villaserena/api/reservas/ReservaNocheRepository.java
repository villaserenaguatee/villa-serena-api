package com.villaserena.api.reservas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaNocheRepository extends JpaRepository<ReservaNoche, Long> {

    List<ReservaNoche> findByReservaIdOrderByFecha(Long reservaId);
}
