package com.villaserena.api.reservas.dto;

import com.villaserena.api.huespedes.Huesped;
import com.villaserena.api.huespedes.TipoDocumento;

/** Huésped principal visible para Recepción (esquema {@code Huesped}). */
public record HuespedVista(Long id, String nombreCompleto, String correo, String telefono, String nacionalidad,
        TipoDocumento tipoDocumento, String numeroDocumento) {

    public static HuespedVista de(Huesped h) {
        return new HuespedVista(h.getId(), h.getNombreCompleto(), h.getCorreo(), h.getTelefono(), h.getNacionalidad(),
                h.getTipoDocumento(), h.getNumeroDocumento());
    }
}
