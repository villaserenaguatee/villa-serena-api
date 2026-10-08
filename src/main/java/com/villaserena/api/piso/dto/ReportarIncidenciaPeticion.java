package com.villaserena.api.piso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Reporte de un daño (esquema {@code ReportarIncidenciaPeticion}). {@code fotoClave} es
 * la clave que devolvió POST /archivos/imagenes con uso INCIDENCIA.
 */
public record ReportarIncidenciaPeticion(
        @NotNull(message = "La habitación es obligatoria.") @Positive Long habitacionId,
        @NotBlank(message = "La descripción es obligatoria.") @Size(max = 2000) String descripcion,
        @NotNull(message = "Indica si el daño impide el uso de la habitación.") Boolean impideUso,
        @Size(min = 1, max = 255) String fotoClave) {
}
