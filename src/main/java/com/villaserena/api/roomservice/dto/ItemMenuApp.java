package com.villaserena.api.roomservice.dto;

import java.math.BigDecimal;

import com.villaserena.api.roomservice.DisponibilidadMenu;

/** Ítem del menú como lo ven la app y Room Service (esquema {@code ItemMenuApp}). */
public record ItemMenuApp(long id, long categoriaId, String nombre, String descripcion, BigDecimal precio,
        String fotoUrl, DisponibilidadMenu disponibilidad) {
}
