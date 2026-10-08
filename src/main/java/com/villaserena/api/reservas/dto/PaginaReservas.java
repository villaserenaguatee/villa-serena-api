package com.villaserena.api.reservas.dto;

import java.util.List;

/** Página de la búsqueda de reservas, la única lista paginada del API (esquema {@code PaginaReservas}). */
public record PaginaReservas(List<ReservaResumen> contenido, int page, int size, long totalElementos,
        int totalPaginas) {
}
