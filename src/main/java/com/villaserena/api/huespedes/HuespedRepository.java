package com.villaserena.api.huespedes;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HuespedRepository extends JpaRepository<Huesped, Long> {

    Optional<Huesped> findByCorreoIgnoreCase(String correo);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT h FROM Huesped h WHERE lower(h.correo)=lower(:correo)")
    Optional<Huesped> bloquearPorCorreo(String correo);
}
