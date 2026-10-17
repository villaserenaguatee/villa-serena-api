package com.villaserena.api.app;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoPushRepository extends JpaRepository<DispositivoPush, Long> {

    Optional<DispositivoPush> findByTokenExpo(String tokenExpo);

    List<DispositivoPush> findByHuespedId(Long huespedId);

    void deleteByHuespedId(Long huespedId);
}
