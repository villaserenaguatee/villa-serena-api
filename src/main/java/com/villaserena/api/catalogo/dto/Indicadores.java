package com.villaserena.api.catalogo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Indicadores(
        LocalDate inicio,
        LocalDate fin,
        OcupacionHoy ocupacionHoy,
        BigDecimal ingresos,
        List<ReservasPorCanal> reservasPorCanal
) {}
