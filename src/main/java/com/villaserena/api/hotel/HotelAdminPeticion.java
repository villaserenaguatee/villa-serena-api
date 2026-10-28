package com.villaserena.api.hotel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Petición de actualización del hotel (esquema {@code HotelAdminPeticion}). */
public record HotelAdminPeticion(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 4000) String descripcion,
        @NotBlank @Size(max = 255) String direccion,
        @NotBlank @Size(max = 30) String telefono,
        @NotBlank @Email @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+") @Size(max = 150) String correo,
        @NotBlank @Size(max = 150) String nombreComercial,
        @NotBlank @Size(max = 150) String razonSocial,
        @NotBlank @Size(max = 20) String nit,
        @NotBlank @Size(max = 255) String direccionFiscal,
        @JsonDeserialize(using = FotosHotelDeserializer.class) List<@Valid @NotNull FotoHotelPeticion> fotos) {

    public HotelAdminPeticion {
        nombre = nombre == null ? null : nombre.trim();
        descripcion = descripcion == null ? null : descripcion.trim();
        direccion = direccion == null ? null : direccion.trim();
        telefono = telefono == null ? null : telefono.trim();
        correo = correo == null ? null : correo.trim();
        nombreComercial = nombreComercial == null ? null : nombreComercial.trim();
        razonSocial = razonSocial == null ? null : razonSocial.trim();
        nit = nit == null ? null : nit.trim();
        direccionFiscal = direccionFiscal == null ? null : direccionFiscal.trim();
    }

    @JsonAnySetter
    public void campoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("Campo no permitido.");
    }
}
