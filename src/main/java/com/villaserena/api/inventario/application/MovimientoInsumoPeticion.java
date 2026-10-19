package com.villaserena.api.inventario.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.inventario.domain.MovimientoInsumo;

/** Adaptación explícita del payload existente a los campos tipados del movimiento. */
public record MovimientoInsumoPeticion(UUID insumoId, MovimientoInsumo.Tipo tipo,
        BigDecimal cantidad, String motivo) {

    public static MovimientoInsumoPeticion desde(Map<String, Object> datos) {
        MovimientoInsumo.Tipo tipo = tipo(datos);
        String motivo = motivo(datos);
        UUID insumo = insumoId(datos);
        BigDecimal cantidad;
        try {
            cantidad = new BigDecimal(String.valueOf(datos.get("cantidad")));
        } catch (NumberFormatException e) {
            throw invalido("cantidad");
        }
        return new MovimientoInsumoPeticion(insumo, tipo, cantidad, motivo);
    }

    public MovimientoInsumo movimiento() {
        try {
            return new MovimientoInsumo(insumoId, tipo, cantidad, motivo);
        } catch (IllegalArgumentException e) {
            throw invalido(e.getMessage());
        }
    }

    public static MovimientoInsumo.Tipo tipo(Map<String, Object> datos) {
        Object valor = datos.get("tipo");
        if ("entrada".equals(valor)) return MovimientoInsumo.Tipo.ENTRADA;
        if ("salida".equals(valor)) return MovimientoInsumo.Tipo.SALIDA;
        if ("merma".equals(valor)) return MovimientoInsumo.Tipo.MERMA;
        throw invalido("tipo");
    }

    public static String motivo(Map<String, Object> datos) {
        if (!(datos.get("motivo") instanceof String motivo) || motivo.isBlank() || motivo.length() > 500) {
            throw invalido("motivo");
        }
        return motivo.trim();
    }

    public static UUID insumoId(Map<String, Object> datos) {
        try {
            return UUID.fromString(String.valueOf(datos.get("insumoId")));
        } catch (IllegalArgumentException e) {
            throw ApiException.datosInvalidos("Selecciona un registro existente de insumos.", java.util.List.of());
        }
    }

    private static ApiException invalido(String campo) {
        return ApiException.datosInvalidos("Revisa " + campo + ".", java.util.List.of());
    }
}
