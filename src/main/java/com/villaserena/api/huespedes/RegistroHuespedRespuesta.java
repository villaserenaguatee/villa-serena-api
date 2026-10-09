package com.villaserena.api.huespedes;

import com.villaserena.api.reservas.dto.HuespedVista;

/** Resultado de registrar un huésped (esquema {@code RegistroHuespedRespuesta}). */
public record RegistroHuespedRespuesta(boolean yaExistia, HuespedVista huesped) {
}
