package com.villaserena.api.inventario.infrastructure.adapters.out.persistence;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.villaserena.api.comun.ApiException;
import com.villaserena.api.inventario.domain.port.out.RegistroMovimientosInsumos;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Adapta las tablas existentes sin cambiar el esquema ni sus registros históricos. */
@Repository
public class RegistroMovimientosInsumosJdbc implements RegistroMovimientosInsumos {
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RegistroMovimientosInsumosJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Registro> listar() {
        return jdbc.query("""
                SELECT id,datos::text,creacion::text,version FROM gestion_registros
                WHERE tipo='movimientos' ORDER BY creado_en,id""",
                (rs, n) -> new Registro(rs.getObject("id", UUID.class), rs.getLong("version"),
                        leer(rs.getString("datos")), leer(rs.getString("creacion"))));
    }

    @Override
    public void bloquearIdentificador(UUID id) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "gestion:movimientos:" + id);
    }

    @Override
    public Optional<Registro> buscarBloqueado(UUID id) {
        return jdbc.query("""
                SELECT id,datos::text,creacion::text,version FROM gestion_registros
                WHERE tipo='movimientos' AND id=? FOR UPDATE""",
                (rs, n) -> new Registro(rs.getObject("id", UUID.class), rs.getLong("version"),
                        leer(rs.getString("datos")), leer(rs.getString("creacion"))), id).stream().findFirst();
    }

    @Override
    public BigDecimal bloquearExistencias(UUID insumoId) {
        var filas = jdbc.queryForList("""
                SELECT datos::text AS datos FROM gestion_registros
                WHERE tipo='insumos' AND id=? FOR UPDATE""", insumoId);
        if (filas.isEmpty()) {
            throw ApiException.datosInvalidos("Selecciona un registro existente de insumos.", List.of());
        }
        try {
            return new BigDecimal(String.valueOf(leer((String) filas.getFirst().get("datos")).get("stock")));
        } catch (NumberFormatException e) {
            throw ApiException.datosInvalidos("Revisa stock.", List.of());
        }
    }

    @Override
    public void actualizarExistencias(UUID insumoId, BigDecimal existencias, long empleadoId) {
        var fila = jdbc.queryForMap("""
                SELECT datos::text AS datos,version FROM gestion_registros
                WHERE tipo='insumos' AND id=?""", insumoId);
        String anterior = (String) fila.get("datos");
        var datos = leer(anterior);
        datos.put("stock", existencias);
        long version = ((Number) fila.get("version")).longValue() + 1;
        String nuevo = json.writeValueAsString(datos);
        jdbc.update("""
                UPDATE gestion_registros SET datos=?::jsonb,version=?,actualizado_en=now()
                WHERE tipo='insumos' AND id=?""", nuevo, version, insumoId);
        auditar("insumos", insumoId, version, empleadoId, anterior, nuevo);
    }

    @Override
    public Registro crear(UUID id, Map<String, Object> datos, Map<String, Object> creacion, long empleadoId) {
        String nuevo = json.writeValueAsString(datos);
        jdbc.update("""
                INSERT INTO gestion_registros(tipo,id,datos,creacion,creado_por)
                VALUES ('movimientos',?,?::jsonb,?::jsonb,?)""", id, nuevo, json.writeValueAsString(creacion), empleadoId);
        auditar("movimientos", id, 1, empleadoId, null, nuevo);
        return new Registro(id, 1, datos, creacion);
    }

    private void auditar(String tipo, UUID id, long version, long empleadoId, String anterior, String nuevo) {
        jdbc.update("""
                INSERT INTO gestion_auditoria(tipo,registro_id,version,empleado_id,anterior,nuevo)
                VALUES (?,?,?,?,?::jsonb,?::jsonb)""", tipo, id, version, empleadoId, anterior, nuevo);
    }

    private Map<String, Object> leer(String texto) {
        return new LinkedHashMap<>(json.readValue(texto, MAPA));
    }
}
