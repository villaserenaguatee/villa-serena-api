package com.villaserena.api.piso;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.*;
import com.villaserena.api.reservas.IdempotenciaReservas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController
@PreAuthorize("hasAnyRole('HUESPED','RECEPCION')")
public class ArticulosSolicitudController {
 private final JdbcTemplate jdbc;private final HistorialEstadoService historial;private final SimpMessagingTemplate mensajes;private final IdempotenciaReservas idempotencia;
 public ArticulosSolicitudController(JdbcTemplate jdbc,HistorialEstadoService historial,SimpMessagingTemplate mensajes,IdempotenciaReservas idempotencia){this.jdbc=jdbc;this.historial=historial;this.mensajes=mensajes;this.idempotencia=idempotencia;}
 public record Item(@Positive long articuloId,@Min(1) int cantidad){}
 public record Peticion(@NotNull UUID clave,@NotEmpty @Size(max=20) List<@Valid Item> articulos,@Size(max=500) String comentario){}
 public record Limpieza(@Size(max=500) String comentario){}
 private boolean guest(){return "HUESPED".equals(UsuarioActual.rol());}
 private Map<String,Object> reserva(String codigo){var rows=jdbc.queryForList("SELECT id,habitacion_id,estado FROM reservas WHERE codigo=?"+(guest()?" AND huesped_id=?":"")+" FOR UPDATE",guest()?new Object[]{codigo,UsuarioActual.id()}:new Object[]{codigo});if(rows.isEmpty())throw ApiException.noEncontrado();var r=rows.getFirst();if(!"EN_ESTADIA".equals(r.get("estado"))||r.get("habitacion_id")==null)throw ApiException.conflicto("ESTADIA_REQUERIDA","La reserva debe estar en estancia y tener habitación.");return r;}
 @GetMapping({"/api/v1/app/reservas/{codigo}/articulos","/api/v1/estancias/{codigo}/articulos"}) @Transactional public List<Map<String,Object>> catalogo(@PathVariable String codigo){reserva(codigo);return jdbc.queryForList("SELECT id,nombre,cantidad_maxima AS \"cantidadMaxima\" FROM articulos ORDER BY nombre");}
 @PostMapping({"/api/v1/app/reservas/{codigo}/solicitudes-articulos","/api/v1/estancias/{codigo}/solicitudes-articulos"}) @Transactional public Map<?,?> crear(@PathVariable String codigo,@Valid @RequestBody Peticion p){
  var r=reserva(codigo);return idempotencia.ejecutar("articulos:"+UsuarioActual.rol()+":"+UsuarioActual.id()+":"+codigo,p.clave().toString(),p,Map.class,()->{
   Set<Long> ids=new HashSet<>();for(var item:p.articulos()){var max=jdbc.queryForList("SELECT cantidad_maxima FROM articulos WHERE id=?",Integer.class,item.articuloId());if(max.isEmpty()||!ids.add(item.articuloId())||item.cantidad()>max.getFirst())throw ApiException.datosInvalidos("Revisa los artículos y sus cantidades máximas.",List.of());}
   long id=jdbc.queryForObject("INSERT INTO solicitudes(reserva_id,habitacion_id,tipo,comentario) VALUES (?,?,'ARTICULOS',?) RETURNING id",Long.class,r.get("id"),r.get("habitacion_id"),p.comentario());for(var item:p.articulos())jdbc.update("INSERT INTO solicitud_items(solicitud_id,articulo_id,cantidad) VALUES(?,?,?)",id,item.articuloId(),item.cantidad());historial.registrar(TipoEntidadHistorial.SOLICITUD,id,null,"PENDIENTE",guest()?Responsable.HUESPED:Responsable.empleado(UsuarioActual.id()),null);evento(id);return Map.of("id",id,"estado","PENDIENTE");
  });
 }
 @PostMapping("/api/v1/estancias/{codigo}/solicitudes-limpieza") @PreAuthorize("hasRole('RECEPCION')") @Transactional public Map<String,Object> limpieza(@PathVariable String codigo,@Valid @RequestBody Limpieza p){var r=reserva(codigo);jdbc.queryForObject("SELECT id FROM habitaciones WHERE id=? FOR UPDATE",Long.class,r.get("habitacion_id"));if(jdbc.queryForObject("SELECT count(*) FROM solicitudes WHERE habitacion_id=? AND tipo='LIMPIEZA' AND estado IN ('PENDIENTE','EN_PROCESO')",Integer.class,r.get("habitacion_id"))>0)throw ApiException.conflicto("SOLICITUD_ACTIVA","Ya existe una solicitud de limpieza activa.");long id=jdbc.queryForObject("INSERT INTO solicitudes(reserva_id,habitacion_id,tipo,comentario) VALUES(?,?,'LIMPIEZA',?) RETURNING id",Long.class,r.get("id"),r.get("habitacion_id"),p.comentario());historial.registrar(TipoEntidadHistorial.SOLICITUD,id,null,"PENDIENTE",Responsable.empleado(UsuarioActual.id()),null);evento(id);return Map.of("id",id,"estado","PENDIENTE");}
 @GetMapping("/api/v1/estancias/{codigo}/solicitudes") @PreAuthorize("hasRole('RECEPCION')") @Transactional public List<Map<String,Object>> lista(@PathVariable String codigo){var r=reserva(codigo);return jdbc.queryForList("SELECT id,tipo,estado,comentario,creado_en AS \"creadaEn\" FROM solicitudes WHERE reserva_id=? ORDER BY id DESC",r.get("id"));}
 private void evento(long id){var d=jdbc.queryForMap("SELECT s.id,s.tipo,h.numero,h.piso FROM solicitudes s JOIN habitaciones h ON h.id=s.habitacion_id WHERE s.id=?",id);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){mensajes.convertAndSend("/topic/solicitudes",(Object)Map.of("id",id,"tipo",d.get("tipo"),"habitacion",Map.of("numero",d.get("numero"),"piso",d.get("piso"))));}});}
}
