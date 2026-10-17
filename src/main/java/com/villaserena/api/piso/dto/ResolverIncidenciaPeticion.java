package com.villaserena.api.piso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solución de la incidencia, obligatoria (esquema {@code ResolverIncidenciaPeticion}; RN-MAN-010). */
public record ResolverIncidenciaPeticion(
        @NotBlank(message = "Describe la solución.") @Size(max = 2000) String solucion) {
}
