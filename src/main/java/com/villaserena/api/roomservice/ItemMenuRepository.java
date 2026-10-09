package com.villaserena.api.roomservice;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {

    /** El menú, en el orden en que la base guarda las categorías. */
    @Query("SELECT i FROM ItemMenu i WHERE i.estado = 'ACTIVO' ORDER BY i.categoria, i.id")
    List<ItemMenu> activos();

    List<ItemMenu> findByIdIn(List<Long> ids);
}
