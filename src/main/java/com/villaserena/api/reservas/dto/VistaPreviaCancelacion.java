package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;

/** Qué pasará con el dinero si se cancela (HU-REC-05, criterio 4). */
public record VistaPreviaCancelacion(Resultado resultado, BigDecimal montoReembolso) {

    public enum Resultado {
        REEMBOLSO_TOTAL, SIN_REEMBOLSO, SIN_PAGOS
    }
}
