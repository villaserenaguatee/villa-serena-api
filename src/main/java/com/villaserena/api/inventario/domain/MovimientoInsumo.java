package com.villaserena.api.inventario.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Reglas de un movimiento manual, independientes de HTTP, Spring y persistencia. */
public record MovimientoInsumo(UUID insumoId, Tipo tipo, BigDecimal cantidad, String motivo) {
    private static final BigDecimal MAXIMO = new BigDecimal("99999999");

    public enum Tipo { ENTRADA, SALIDA, MERMA }

    public MovimientoInsumo {
        Objects.requireNonNull(insumoId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(motivo);
        if (motivo.isBlank() || motivo.length() > 500) throw new IllegalArgumentException("motivo");
        if (cantidad == null || cantidad.signum() <= 0 || cantidad.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException("cantidad");
        }
    }

    public BigDecimal aplicar(BigDecimal existencias) {
        if (existencias == null || existencias.signum() < 0 || existencias.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException("stock");
        }
        BigDecimal resultado = tipo == Tipo.ENTRADA ? existencias.add(cantidad) : existencias.subtract(cantidad);
        if (resultado.signum() < 0) throw new StockInsuficiente();
        return resultado;
    }

    public static final class StockInsuficiente extends RuntimeException {
        public StockInsuficiente() { super("No hay existencias suficientes."); }
    }
}
