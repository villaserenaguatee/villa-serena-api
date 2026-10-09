package com.villaserena.api.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revocadoEn = :ahora where r.empleadoId = :empleadoId and r.revocadoEn is null")
    int revocarTodosDelEmpleado(Long empleadoId, Instant ahora);

    @Modifying
    @Query("update RefreshToken r set r.revocadoEn = :ahora where r.huespedId = :huespedId and r.revocadoEn is null")
    int revocarTodosDelHuesped(Long huespedId, Instant ahora);
}
