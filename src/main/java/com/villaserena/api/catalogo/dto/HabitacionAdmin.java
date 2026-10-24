package com.villaserena.api.catalogo.dto;

public record HabitacionAdmin(
        long id,
        String numero,
        int piso,
        long tipoHabitacionId,
        String estado,
        String ocupacion,
        String condicion
) {}
