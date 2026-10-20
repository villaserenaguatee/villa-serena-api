package com.villaserena.api.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select r.empleadoId from RefreshToken r where r.tokenHash = :hash and r.tipoUsuario = :tipo")
    Optional<Long> buscarEmpleado(String hash, TipoUsuario tipo);

    @Modifying
    @Query("update RefreshToken r set r.revocadoEn = :ahora where r.empleadoId = :empleadoId and r.revocadoEn is null")
    int revocarTodosDelEmpleado(Long empleadoId, Instant ahora);

    @Modifying
    @Query("update RefreshToken r set r.revocadoEn = :ahora where r.huespedId = :huespedId and r.revocadoEn is null")
    int revocarTodosDelHuesped(Long huespedId, Instant ahora);
}
