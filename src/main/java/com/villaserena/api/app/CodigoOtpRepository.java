package com.villaserena.api.app;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CodigoOtpRepository extends JpaRepository<CodigoOtp, Long> {

    /** Los códigos vigentes del huésped, el más reciente primero. */
    @Query("""
            SELECT c FROM CodigoOtp c
            WHERE c.huespedId = :huespedId AND c.usadoEn IS NULL AND c.expiraEn > :ahora
            ORDER BY c.id DESC""")
    List<CodigoOtp> vigentes(long huespedId, Instant ahora);
}
