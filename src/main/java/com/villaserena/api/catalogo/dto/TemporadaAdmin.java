package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TemporadaAdmin(
        long id,
        String nombre,
        LocalDate inicio,
        LocalDate fin,
        BigDecimal ajustePct,
        List<Long> tipoHabitacionIds
) {}
