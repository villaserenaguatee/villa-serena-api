package com.villaserena.api.catalogo.admin;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.catalogo.dto.CaracteristicasHabitacion;
import com.villaserena.api.catalogo.dto.GuardarOfertaPeticion;
import com.villaserena.api.comun.ApiException;
import tools.jackson.databind.ObjectMapper;

/** Mantiene contenido, vínculos y precio de oferta en una sola transacción SQL. */
@Service
public class OfertasAdminService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TiposHabitacionAdminService tipos;

    public OfertasAdminService(JdbcTemplate jdbc, ObjectMapper json, TiposHabitacionAdminService tipos) {
        this.jdbc = jdbc; this.json = json; this.tipos = tipos;
    }

    @Transactional(readOnly=true)
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("SELECT slug FROM catalog_offers ORDER BY sort_order,slug", String.class)
                .stream().map(this::obtener).toList();
    }

    public Map<String, Object> obtener(String slug) {
        var filas = jdbc.queryForList("SELECT room_type_id,base_price,content::text,requirements::text AS requisitos,sort_order FROM catalog_offers WHERE slug=?", slug);
        if (filas.isEmpty()) throw ApiException.noEncontrado();
        var fila = filas.getFirst();
        long tipo = ((Number) fila.get("room_type_id")).longValue();
        Map<String, Object> contenido = contenido(fila.get("content"));
        var ids = jdbc.queryForList("SELECT room_id FROM catalog_offer_rooms WHERE slug=? ORDER BY room_id", Long.class, slug);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("slug", slug); resultado.put("tipoHabitacionId", tipo);
        resultado.put("nombre", contenido.get("name")); resultado.put("descripcion", contenido.get("description"));
        resultado.put("piso", contenido.get("floor")); resultado.put("capacidad", contenido.get("capacity"));
        resultado.put("superficieM2", contenido.get("size"));
        resultado.put("camas", contenido.getOrDefault("bedCounts", contenido(fila.get("requisitos")).getOrDefault("camas", Map.of())));
        resultado.put("caracteristicas", contenido.getOrDefault("features", List.of()));
        resultado.put("preferencias", contenido.getOrDefault("preferences", List.of()));
        resultado.put("precioPorNoche", fila.get("base_price"));
        resultado.put("publicada", !Boolean.FALSE.equals(contenido.get("published")));
        resultado.put("orden", fila.get("sort_order")); resultado.put("habitacionIds", ids);
        resultado.put("cantidadHabitaciones", ids.size());
        resultado.put("cantidadHabitacionesTipo", jdbc.queryForObject("SELECT count(*) FROM rooms WHERE room_type_id=?", Integer.class, tipo));
        resultado.put("fotos", tipos.obtener(tipo).fotos());
        return resultado;
    }

    @Transactional
    public Map<String, Object> guardar(String slug, GuardarOfertaPeticion p, boolean crear) {
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 80) throw invalid("Slug inválido.");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "admin-oferta:" + slug);
        var anterior = jdbc.queryForList("SELECT room_type_id FROM catalog_offers WHERE slug=?", Long.class, slug);
        if (crear && !anterior.isEmpty()) throw ApiException.conflicto("OFERTA_EXISTENTE", "Ya existe esa oferta.");
        if (!crear && anterior.isEmpty()) throw ApiException.noEncontrado();
        var idsTipos = new TreeSet<Long>(); idsTipos.add(p.tipoHabitacionId()); idsTipos.addAll(anterior);
        for (long tipo : idsTipos) jdbc.queryForList("SELECT id FROM room_types WHERE id=? FOR UPDATE", Long.class, tipo);
        var tipo = tipos.obtener(p.tipoHabitacionId());
        Map<String,Object> previa = crear ? null : obtener(slug);
        if (p.capacidad() > tipo.capacidad()) throw invalid("La capacidad de la oferta supera la del tipo.");
        if (p.publicada() && (!"ACTIVO".equals(tipo.estado()) || tipo.fotos().isEmpty() || p.habitacionIds().isEmpty())) {
            throw invalid("Para publicar se requiere tipo activo, galería y habitaciones acreditadas.");
        }
        if (new HashSet<>(p.habitacionIds()).size() != p.habitacionIds().size()) throw invalid("No repitas habitaciones.");
        Map<String, Object> requisitos = Map.of("piso", p.piso(), "camas", p.camas(),
                "superficieM2", p.superficieM2(), "features", p.caracteristicas());
        for (long id : new TreeSet<>(p.habitacionIds())) {
            var habitaciones = jdbc.queryForList("""
                    SELECT h.room_type_id,h.floor,h.status,coalesce(c.content @> ?::jsonb,false) AS coincide,
                      coalesce((c.content->>'capacidad')::integer,0) AS capacidad
                    FROM rooms h LEFT JOIN room_features c ON c.room_id=h.id WHERE h.id=? FOR UPDATE OF h""",
                    json.writeValueAsString(requisitos), id);
            if (habitaciones.isEmpty()) throw invalid("Una habitación vinculada no existe.");
            var h = habitaciones.getFirst();
            if (((Number) h.get("room_type_id")).longValue() != p.tipoHabitacionId()
                    || ((Number) h.get("floor")).intValue() != p.piso()
                    || !Boolean.TRUE.equals(h.get("coincide"))
                    || ((Number) h.get("capacidad")).intValue() < p.capacidad()) {
                throw invalid("Las habitaciones deben acreditar el tipo, piso, capacidad y características de la oferta.");
            }
            if (p.publicada() && !"ACTIVO".equals(h.get("status"))) throw invalid("Las habitaciones publicadas deben estar activas.");
        }
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("slug", slug); contenido.put("name", p.nombre().trim()); contenido.put("type", tipo.nombre());
        contenido.put("description", p.descripcion().trim()); contenido.put("floor", p.piso());
        contenido.put("capacity", p.capacidad()); contenido.put("size", p.superficieM2());
        contenido.put("beds", p.camas().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue() + " " + e.getKey()).collect(java.util.stream.Collectors.joining(" · ")));
        contenido.put("bedCounts", p.camas());
        contenido.put("features", p.caracteristicas()); contenido.put("preferences", p.preferencias());
        contenido.put("published", p.publicada());
        if (crear) {
            jdbc.update("INSERT INTO catalog_offers(slug,room_type_id,base_price,content,sort_order,requirements) VALUES(?,?,?,?::jsonb,?,?::jsonb)",
                    slug, p.tipoHabitacionId(), p.precioPorNoche(), json.writeValueAsString(contenido), p.orden(), json.writeValueAsString(requisitos));
        } else {
            jdbc.update("UPDATE catalog_offers SET room_type_id=?,base_price=?,content=?::jsonb,sort_order=?,requirements=?::jsonb WHERE slug=?",
                    p.tipoHabitacionId(), p.precioPorNoche(), json.writeValueAsString(contenido), p.orden(), json.writeValueAsString(requisitos), slug);
        }
        jdbc.update("DELETE FROM catalog_offer_rooms WHERE slug=?", slug);
        for (long id : p.habitacionIds()) jdbc.update("INSERT INTO catalog_offer_rooms(slug,room_id,evidence) VALUES(?,?,?)",
                slug, id, "Contenido registrado por ADMIN " + UsuarioActual.id());
        var resultado=obtener(slug);
        auditar("ofertas",p.tipoHabitacionId(),previa,resultado);
        return resultado;
    }

    @Transactional
    public Map<String,Object> publicar(String slug,boolean publicada) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "admin-oferta:"+slug);
        var previa=obtener(slug);
        long tipo=((Number)previa.get("tipoHabitacionId")).longValue();
        jdbc.queryForList("SELECT id FROM room_types WHERE id=? FOR UPDATE",Long.class,tipo);
        var t=tipos.obtener(tipo);
        if(publicada) {
            boolean elegible=Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM catalog_offer_rooms e JOIN catalog_offers o ON o.slug=e.slug
                    JOIN rooms h ON h.id=e.room_id JOIN room_features c ON c.room_id=h.id
                    WHERE e.slug=? AND h.room_type_id=o.room_type_id AND h.status='ACTIVO'
                    AND h.floor=(o.content->>'floor')::integer AND h.condition<>'FUERA_DE_SERVICIO'
                    AND c.content @> o.requirements AND (c.content->>'capacidad')::integer >= (o.content->>'capacity')::integer)
                    """,Boolean.class,slug));
            if(!"ACTIVO".equals(t.estado()) || t.fotos().isEmpty() || !elegible
                    || t.capacidad()<((Number)previa.get("capacidad")).intValue()) throw invalid("Para publicar se requiere tipo activo, galería y habitaciones acreditadas.");
        }
        jdbc.update("UPDATE catalog_offers SET content=jsonb_set(content,'{published}',?::jsonb,true) WHERE slug=?",
                Boolean.toString(publicada),slug);
        var resultado=obtener(slug);auditar("ofertas",tipo,previa,resultado);return resultado;
    }

    public Map<String, Object> caracteristicas(long id) {
        if (jdbc.queryForObject("SELECT count(*) FROM rooms WHERE id=?", Integer.class, id) == 0) throw ApiException.noEncontrado();
        var filas = jdbc.queryForList("SELECT content::text FROM room_features WHERE room_id=?", String.class, id);
        if(filas.isEmpty()) return Map.of();
        var original=contenido(filas.getFirst());
        Map<String,Object> datos=new LinkedHashMap<>();
        for(String campo:List.of("piso","capacidad","superficieM2","camas")) {
            if(original.containsKey(campo)) datos.put(campo,original.get(campo));
        }
        if(original.containsKey("features")) datos.put("caracteristicas",original.get("features"));
        return datos;
    }

    @Transactional
    public Map<String, Object> guardarCaracteristicas(long id, CaracteristicasHabitacion p) {
        var filas = jdbc.queryForList("SELECT room_type_id FROM rooms WHERE id=?", Long.class, id);
        if (filas.isEmpty()) throw ApiException.noEncontrado();
        jdbc.queryForList("SELECT id FROM room_types WHERE id=? FOR UPDATE", Long.class, filas.getFirst());
        var h = jdbc.queryForMap("SELECT floor,room_type_id FROM rooms WHERE id=? FOR UPDATE", id);
        if(((Number)h.get("room_type_id")).longValue()!=filas.getFirst()) {
            throw ApiException.conflicto("HABITACION_CAMBIADA","La habitación cambió de tipo. Consulta nuevamente.");
        }
        if (p.capacidad() > tipos.obtener(((Number) h.get("room_type_id")).longValue()).capacidad()) throw invalid("La capacidad supera la del tipo.");
        if (jdbc.queryForObject("SELECT count(*) FROM reservations WHERE room_id=? AND status IN ('PENDIENTE_PAGO','CONFIRMADA','EN_ESTADIA')", Integer.class, id) > 0) {
            throw ApiException.conflicto("RESERVAS_ACTIVAS", "No se pueden cambiar características comprometidas en reservas activas.");
        }
        Map<String,Object> previa=caracteristicas(id);
        Map<String, Object> contenido = Map.of("piso", h.get("floor"), "capacidad", p.capacidad(),
                "superficieM2", p.superficieM2(), "camas", p.camas(), "features", p.caracteristicas());
        jdbc.update("""
                INSERT INTO room_features(room_id,content,provenance) VALUES(?,?::jsonb,?)
                ON CONFLICT(room_id) DO UPDATE SET content=excluded.content,provenance=excluded.provenance,updated_at=now()""",
                id, json.writeValueAsString(contenido), "Contenido registrado por ADMIN " + UsuarioActual.id());
        auditar("caracteristicas_habitacion",id,previa,contenido);
        return caracteristicas(id);
    }

    private void auditar(String recurso,long id,Map<String,Object> previa,Map<String,Object> nueva) {
        jdbc.update("INSERT INTO administration_audit(resource,record_id,employee_id,previous,new) VALUES(?,?,?,?::jsonb,?::jsonb)",
                recurso,id,UsuarioActual.id(),previa==null?null:json.writeValueAsString(previa),json.writeValueAsString(nueva));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contenido(Object valor) { return json.readValue(valor.toString(), Map.class); }
    private ApiException invalid(String mensaje) { return ApiException.datosInvalidos(mensaje, List.of()); }
}
