package com.villaserena.api.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Empleado> findByCorreoIgnoreCase(String correo);

    boolean existsByRol(RolEmpleado rol);

    boolean existsByCorreoIgnoreCase(String correo);
}
