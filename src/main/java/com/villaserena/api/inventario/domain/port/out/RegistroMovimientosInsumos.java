package com.villaserena.api.inventario.domain.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistencia del flujo; los datos originales conservan el formato JSON histórico. */
public interface RegistroMovimientosInsumos {
    record Registro(UUID id, long version, Map<String, Object> datos, Map<String, Object> creacion) { }

    List<Registro> listar();
    void bloquearIdentificador(UUID id);
    Optional<Registro> buscarBloqueado(UUID id);
    BigDecimal bloquearExistencias(UUID insumoId);
    void actualizarExistencias(UUID insumoId, BigDecimal existencias, long empleadoId);
    Registro crear(UUID id, Map<String, Object> datos, Map<String, Object> creacion, long empleadoId);
}
