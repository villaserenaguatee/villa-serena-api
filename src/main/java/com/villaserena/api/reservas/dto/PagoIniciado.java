package com.villaserena.api.reservas.dto;

import java.time.OffsetDateTime;

public record PagoIniciado(String urlPago, OffsetDateTime expiraEn) {
}
