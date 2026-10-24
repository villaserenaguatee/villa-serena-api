package com.villaserena.api.catalogo.dto;

import java.time.LocalDate;

public record OcupacionHoy(
        LocalDate fecha,
        int habitacionesOcupadas,
        int habitacionesActivas,
        double porcentaje
) {}
