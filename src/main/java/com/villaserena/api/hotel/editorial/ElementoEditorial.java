package com.villaserena.api.hotel.editorial;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElementoEditorial(@NotBlank @Size(max = 80) String id,
        @Size(max = 4000) String texto, @Size(max = 255) String fotoClave, @Size(max = 100) String destino,
        @Size(max = 30) List<@NotBlank @Size(max = 255) String> fotoClaves) {
    public ElementoEditorial(String id, String texto, String fotoClave, String destino) {
        this(id, texto, fotoClave, destino, null);
    }
    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo editorial no permitido.");
    }
}
