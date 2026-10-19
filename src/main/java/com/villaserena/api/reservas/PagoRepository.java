package com.villaserena.api.reservas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByCuentaIdOrderByCreadoEn(Long cuentaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.checkoutId = :sesionId")
    Optional<Pago> bloquearPorCheckout(String sesionId);

    Optional<Pago> findFirstByCuentaIdAndMetodoAndEstado(Long cuentaId, MetodoPago metodo, EstadoPago estado);

    Optional<Pago> findFirstByCuentaIdAndMetodoOrderByCreadoEnDesc(Long cuentaId, MetodoPago metodo);
}
