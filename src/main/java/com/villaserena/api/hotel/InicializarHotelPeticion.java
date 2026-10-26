package com.villaserena.api.hotel;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de inicialización; no admite fotos ni parámetros de facturación. */
public record InicializarHotelPeticion(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 4000) String descripcion,
        @NotBlank @Size(max = 255) String direccion,
        @NotBlank @Size(max = 30) String telefono,
        @NotBlank @Email @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+") @Size(max = 150) String correo,
        @NotBlank @Size(max = 150) String nombreComercial,
        @NotBlank @Size(max = 150) String razonSocial,
        @NotBlank @Size(max = 20) String nit,
        @NotBlank @Size(max = 255) String direccionFiscal) {
    public InicializarHotelPeticion {
        nombre = recortar(nombre);
        descripcion = recortar(descripcion);
        direccion = recortar(direccion);
        telefono = recortar(telefono);
        correo = recortar(correo);
        nombreComercial = recortar(nombreComercial);
        razonSocial = recortar(razonSocial);
        nit = recortar(nit);
        direccionFiscal = recortar(direccionFiscal);
    }

    private static String recortar(String value) { return value == null ? null : value.trim(); }

    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
