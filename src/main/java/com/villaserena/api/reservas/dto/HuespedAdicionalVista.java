package com.villaserena.api.reservas.dto;

import com.villaserena.api.huespedes.TipoDocumento;

public record HuespedAdicionalVista(Long id, String nombreCompleto, TipoDocumento tipoDocumento,
        String numeroDocumento, String nacionalidad) {
}
