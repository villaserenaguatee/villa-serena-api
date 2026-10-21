package com.villaserena.api.auth;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Empleado> findByCorreoIgnoreCase(String correo);

    boolean existsByRol(RolEmpleado rol);

    boolean existsByCorreoIgnoreCase(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empleado e where e.id = :id")
    Optional<Empleado> buscarParaActualizar(long id);

    @Query("""
            select e from Empleado e where (:rol is null or e.rol = :rol)
            and (:estado is null or e.estado = :estado) order by e.nombreCompleto, e.id
            """)
    List<Empleado> listar(RolEmpleado rol, EstadoEmpleado estado);

    /** Invalida también las versiones que usa el editor existente de catálogos de personal. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE employees SET version = version + 1 WHERE id = :id", nativeQuery = true)
    int incrementarVersion(long id);
}
