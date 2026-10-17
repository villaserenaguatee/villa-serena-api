package com.villaserena.api.reservas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByReservaId(Long reservaId);
}
