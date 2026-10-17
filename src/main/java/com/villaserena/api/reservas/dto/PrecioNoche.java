package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Precio de una noche y sus ajustes (esquema {@code PrecioNoche}). */
public record PrecioNoche(LocalDate fecha, BigDecimal precio, String temporada, boolean finDeSemana) {
}
