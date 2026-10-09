package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

public record TipoHabitacionPublico(Long id, String nombre, String descripcion, int capacidad,
        BigDecimal precioBaseNoche, List<String> fotos) {
}
