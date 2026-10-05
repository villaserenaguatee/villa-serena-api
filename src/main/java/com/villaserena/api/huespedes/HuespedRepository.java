package com.villaserena.api.huespedes;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HuespedRepository extends JpaRepository<Huesped, Long> {

    Optional<Huesped> findByCorreoIgnoreCase(String correo);
}
