package com.villaserena.api.reservas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoRepository extends JpaRepository<Cargo, Long> {

    List<Cargo> findByCuentaIdOrderByCreadoEn(Long cuentaId);
}
