package com.villaserena.api.piso;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.*;

/** Solicitudes operativas persistidas; no modifican la condición de la habitación. */
@RestController
@RequestMapping("/api/v1/limpieza/solicitudes")
@PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
public class SolicitudesController {
 private final SolicitudesEventos eventos;private final JdbcTemplate jdbc;
 private final HistorialEstadoService historial;
 public SolicitudesController(JdbcTemplate jdbc, HistorialEstadoService historial,SolicitudesEventos eventos){this.eventos=eventos;this.jdbc=jdbc;this.historial=historial;}
 private Map<String,Object> detalle(long id){
  var rows=jdbc.query("SELECT s.*,h.numero,h.piso,e.nombre_completo FROM solicitudes s JOIN habitaciones h ON h.id=s.habitacion_id LEFT JOIN empleados e ON e.id=s.empleado_id WHERE s.id=?",(r,n)->{
   Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getLong("id"));m.put("tipo",r.getString("tipo"));m.put("estado",r.getString("estado"));m.put("creadaEn",r.getTimestamp("creado_en").toInstant().toString());m.put("comentario",r.getString("comentario"));m.put("habitacion",Map.of("id",r.getLong("habitacion_id"),"numero",r.getString("numero"),"piso",r.getInt("piso")));m.put("empleadoACargo",r.getObject("empleado_id")==null?null:Map.of("id",r.getLong("empleado_id"),"nombre",r.getString("nombre_completo")));return m;
  },id);if(rows.isEmpty())throw ApiException.noEncontrado();var m=rows.getFirst();
  m.put("articulos",jdbc.queryForList("SELECT a.id AS \"articuloId\",a.nombre,si.cantidad FROM solicitud_items si JOIN articulos a ON a.id=si.articulo_id WHERE si.solicitud_id=? ORDER BY si.id",id));return m;
 }
 @GetMapping public List<Map<String,Object>> lista(@RequestParam(defaultValue="false") boolean historial){return jdbc.queryForList(historial?"SELECT id FROM solicitudes ORDER BY creado_en DESC,id DESC LIMIT 500":"SELECT id FROM solicitudes WHERE estado IN ('PENDIENTE','EN_PROCESO') ORDER BY creado_en,id",Long.class).stream().map(this::detalle).toList();}
 @GetMapping("/{id}") public Map<String,Object> una(@PathVariable long id){return detalle(id);}
 @PostMapping("/{id}/tomar") @Transactional public Map<String,Object> tomar(@PathVariable long id){return cambiar(id,false);}
 @PostMapping("/{id}/atender") @Transactional public Map<String,Object> atender(@PathVariable long id){return cambiar(id,true);}
 private Map<String,Object> cambiar(long id,boolean atender){
  var rows=jdbc.queryForList("SELECT estado,empleado_id FROM solicitudes WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw ApiException.noEncontrado();var s=rows.getFirst();String anterior=(String)s.get("estado");long empleado=UsuarioActual.id();
  if(atender?!("EN_PROCESO".equals(anterior)&&s.get("empleado_id") instanceof Number n&&n.longValue()==empleado):!"PENDIENTE".equals(anterior))throw ApiException.conflicto("SOLICITUD_CAMBIADA","La solicitud cambió o está a cargo de otro empleado. Actualiza la lista.");
  String nuevo=atender?"ATENDIDA":"EN_PROCESO";jdbc.update("UPDATE solicitudes SET estado=?,empleado_id=? WHERE id=?",nuevo,empleado,id);historial.registrar(TipoEntidadHistorial.SOLICITUD,id,anterior,nuevo,Responsable.empleado(empleado),null);eventos.publicar(id);return detalle(id);
 }
}
