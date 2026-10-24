package com.villaserena.api.hotel;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Foto del hotel en lectura y actualización administrativa (esquema {@code FotoHotelPeticion}). */
public record FotoHotelPeticion(
        @NotBlank @Size(max = 255) String clave,
        @NotNull @Min(0) Integer orden) {

    public FotoHotelPeticion {
        clave = clave == null ? null : clave.trim();
    }

    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
