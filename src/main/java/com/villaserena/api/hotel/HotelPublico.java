package com.villaserena.api.hotel;

import java.util.List;

/** Información pública del hotel (esquema {@code Hotel}). Las horas de check-in y check-out son fijas. */
public record HotelPublico(String nombre, String descripcion, List<String> fotos, Ubicacion ubicacion,
        Contacto contacto, String horaCheckIn, String horaCheckOut) {

    public record Ubicacion(String direccion) {
    }

    public record Contacto(String telefono, String correo) {
    }
}
