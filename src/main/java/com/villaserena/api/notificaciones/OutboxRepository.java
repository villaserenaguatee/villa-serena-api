package com.villaserena.api.notificaciones;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * Pendientes cuyo momento de reintento ya pasó, en orden de llegada y con la
     * fila bloqueada: si hubiera dos instancias del API, cada correo sale una sola
     * vez. {@code SKIP LOCKED} evita que una instancia espere a la otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT o FROM Outbox o
            WHERE o.estado = com.villaserena.api.notificaciones.EstadoNotificacion.PENDIENTE
              AND o.proximoIntentoEn <= :ahora
            ORDER BY o.id""")
    List<Outbox> tomarPendientes(Instant ahora, Limit limite);

    List<Outbox> findByTipoAndDestinatarioOrderByIdDesc(TipoNotificacion tipo, String destinatario);
}
