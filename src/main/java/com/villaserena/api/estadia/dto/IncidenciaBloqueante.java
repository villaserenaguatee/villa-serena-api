package com.villaserena.api.estadia.dto;

import java.time.OffsetDateTime;

/** Incidencia que deja la habitación FUERA_DE_SERVICIO, en solo lectura (esquema {@code IncidenciaBloqueante}). */
public record IncidenciaBloqueante(Long id, String descripcion, String estado, OffsetDateTime reportadaEn) {
}
