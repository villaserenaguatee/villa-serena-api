package com.villaserena.api.inventario.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.inventario.domain.MovimientoInsumo;
import com.villaserena.api.inventario.domain.port.out.RegistroMovimientosInsumos;
import com.villaserena.api.inventario.domain.port.out.RegistroMovimientosInsumos.Registro;

/** Orquesta movimiento, existencias y auditoría en una sola transacción local. */
@Service
public class MovimientosInsumosService {
    private final RegistroMovimientosInsumos registros;
    private final Clock reloj;

    public MovimientosInsumosService(RegistroMovimientosInsumos registros, Clock reloj) {
        this.registros = registros;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<Registro> listar() {
        return registros.listar();
    }

    @Transactional
    public Registro registrar(UUID id, long version, Map<String, Object> originales, long empleadoId) {
        registros.bloquearIdentificador(id);
        var anterior = registros.buscarBloqueado(id);
        if (version == 0 && anterior.isPresent() && anterior.get().creacion().equals(originales)) {
            return anterior.get();
        }
        long actual = anterior.map(Registro::version).orElse(0L);
        if (version != actual) {
            throw ApiException.conflicto("VERSION_CAMBIADA", "El registro cambió. Actualiza y revisa antes de guardar.");
        }
        if (anterior.isPresent()) {
            throw ApiException.datosInvalidos("Los movimientos son inmutables.", List.of());
        }

        // Conserva el orden de validación del flujo anterior y los campos de extensión.
        var datos = new LinkedHashMap<>(originales);
        datos.remove("id");
        datos.remove("version");
        MovimientoInsumoPeticion.tipo(datos);
        MovimientoInsumoPeticion.motivo(datos);
        UUID insumoId = MovimientoInsumoPeticion.insumoId(datos);
        var existencias = registros.bloquearExistencias(insumoId);
        MovimientoInsumo movimiento = MovimientoInsumoPeticion.desde(datos).movimiento();
        java.math.BigDecimal nuevasExistencias;
        try {
            nuevasExistencias = movimiento.aplicar(existencias);
        } catch (MovimientoInsumo.StockInsuficiente e) {
            throw ApiException.conflicto("STOCK_INSUFICIENTE", e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiException.datosInvalidos("Revisa stock.", List.of());
        }
        registros.actualizarExistencias(insumoId, nuevasExistencias, empleadoId);
        datos.put("fecha", reloj.instant().toString());
        return registros.crear(id, datos, originales, empleadoId);
    }
}
