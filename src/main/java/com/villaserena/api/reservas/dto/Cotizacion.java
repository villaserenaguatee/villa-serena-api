package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

/** Total de la estadía con su desglose por noche (esquema {@code CotizacionEstadia}). */
public record Cotizacion(int noches, BigDecimal total, List<PrecioNoche> desglose) {
}
