package com.villaserena.api.canal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CanalRepository extends JpaRepository<Canal, Long> {

    Optional<Canal> findByCodigo(String codigo);
}
