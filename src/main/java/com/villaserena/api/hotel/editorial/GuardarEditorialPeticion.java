package com.villaserena.api.hotel.editorial;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GuardarEditorialPeticion(@NotNull @Min(0) Long revision,
        @NotNull @Size(min = 1, max = 64) List<@Valid @NotNull ElementoEditorial> elementos) {
    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo editorial no permitido.");
    }
}
