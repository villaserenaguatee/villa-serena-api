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
        @NotBlank(message = "La nacionalidad es obligatoria.") @Size(max = 60) String nacionalidad) {
}
