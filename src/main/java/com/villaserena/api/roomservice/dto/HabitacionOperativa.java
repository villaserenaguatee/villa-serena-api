package com.villaserena.api.roomservice.dto;

/** Habitación a la que va el pedido (esquema {@code HabitacionOperativa}). */
public record HabitacionOperativa(Long id, String numero, Integer piso) {
}
