package com.villaserena.api.hotel.editorial;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PublicarEditorialPeticion(@NotNull @Min(1) Long revision) {
    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo de publicación no permitido.");
    }
}
