package com.villaserena.api.roomservice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Cola de Room Service: solo los activos, el más antiguo primero (HU-RS-01). */
    @Query("SELECT p FROM Pedido p WHERE p.estado IN :estados ORDER BY p.creadoEn, p.id")
    List<Pedido> enEstados(List<EstadoPedido> estados);

    List<Pedido> findByReservaIdOrderByCreadoEnDescIdDesc(Long reservaId);

    /**
     * Bloquea la fila del pedido hasta el final de la transacción. Sustituye a la
     * versión optimista, que necesitaría una columna {@code version} que la tabla
     * no tiene: con el bloqueo, dos empleados que avanzan el mismo pedido a la vez
     * se atienden uno después del otro, y el segundo ve el estado ya cambiado y
     * recibe 409 (RN-RS-004).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> bloquear(long id);

    /** Pedidos activos de una reserva: el check-out los tiene que resolver (RN-RS-009). */
    @Query("SELECT p FROM Pedido p WHERE p.reservaId = :reservaId AND p.estado IN :estados ORDER BY p.id")
    List<Pedido> activosDeReserva(long reservaId, List<EstadoPedido> estados);
}
