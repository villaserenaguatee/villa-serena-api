package com.villaserena.api.hotel;

import java.util.List;

/** Datos generales y fiscales del hotel (esquema {@code HotelAdmin}). */
public record HotelAdmin(
        String nombre,
        String descripcion,
        String direccion,
        String telefono,
        String correo,
        String nombreComercial,
        String razonSocial,
        String nit,
        String direccionFiscal,
        List<FotoHotelPeticion> fotos,
        String horaCheckIn,
        String horaCheckOut,
        String politicaCancelacion) {
    @com.fasterxml.jackson.annotation.JsonProperty("revisionGaleria")
    public String revisionGaleria() { return GaleriaHotel.revision(fotos); }
    @com.fasterxml.jackson.annotation.JsonProperty("editorFotografico")
    public boolean editorFotografico() { return true; }
}
