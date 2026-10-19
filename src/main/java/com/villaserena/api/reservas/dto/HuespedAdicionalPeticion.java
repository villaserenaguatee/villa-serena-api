package com.villaserena.api.reservas.dto;

import com.villaserena.api.huespedes.TipoDocumento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos de un huésped adicional (esquema {@code HuespedAdicionalDatos}). */
public record HuespedAdicionalPeticion(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 150) String nombreCompleto,
        @NotNull(message = "El tipo de documento es obligatorio.") TipoDocumento tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio.") @Size(max = 30) String numeroDocumento,
        @Size(max = 60) String nacionalidad) {
    public HuespedAdicionalPeticion {
        nacionalidad = nacionalidad == null || nacionalidad.isBlank() ? null : nacionalidad.trim();
    }

    @jakarta.validation.constraints.AssertTrue(message="El documento no tiene un formato v\u00e1lido: DPI de 13 d\u00edgitos o pasaporte de 3 a 30 letras o n\u00fameros.")
    public boolean isDocumentoValido() { return tipoDocumento == null || numeroDocumento == null || numeroDocumento.trim().matches(tipoDocumento == TipoDocumento.DPI ? "[0-9]{13}" : "[A-Za-z0-9]{3,30}"); }
}
