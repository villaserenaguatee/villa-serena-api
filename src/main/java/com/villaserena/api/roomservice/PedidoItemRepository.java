package com.villaserena.api.roomservice;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    List<PedidoItem> findByPedidoIdOrderById(Long pedidoId);
}
