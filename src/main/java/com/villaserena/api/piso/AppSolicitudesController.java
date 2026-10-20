package com.villaserena.api.piso;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
@RestController
@RequestMapping("/api/v1/app/reservas/{codigo}/solicitudes-limpieza")
@PreAuthorize("hasRole('HUESPED')")
public class AppSolicitudesController {
 private final SolicitudesEventos eventos;private final JdbcTemplate jdbc;private final HistorialEstadoService historial;
 public AppSolicitudesController(JdbcTemplate jdbc,HistorialEstadoService historial,SolicitudesEventos eventos){this.eventos=eventos;this.jdbc=jdbc;this.historial=historial;}
 public record Peticion(@Size(max=500) String comentario){}
 private Map<String,Object> reserva(String codigo,boolean lock){var rows=jdbc.queryForList("SELECT id,room_id AS \"habitacion_id\",status AS \"estado\" FROM reservations WHERE code=? AND guest_id=?"+(lock?" FOR UPDATE":""),codigo,UsuarioActual.id());if(rows.isEmpty())throw ApiException.noEncontrado();return rows.getFirst();}
 @GetMapping public List<Map<String,Object>> lista(@PathVariable String codigo){var r=reserva(codigo,false);return jdbc.queryForList("SELECT id,type AS \"tipo\",status AS \"estado\",comment AS \"comentario\",created_at AS \"creadaEn\"  FROM requests WHERE reservation_id=? ORDER BY id DESC",r.get("id"));}
 @PostMapping @Transactional public Map<String,Object> crear(@PathVariable String codigo,@Valid @RequestBody Peticion p){
  var r=reserva(codigo,true);if(!"EN_ESTADIA".equals(r.get("estado"))||r.get("habitacion_id")==null)throw ApiException.conflicto("ESTADIA_REQUERIDA","Puedes solicitar limpieza durante tu estancia, después del check-in.");
  jdbc.queryForObject("SELECT id  FROM rooms WHERE id=? FOR UPDATE",Long.class,r.get("habitacion_id"));
  var active=jdbc.queryForList("SELECT id  FROM requests WHERE room_id=? AND type='LIMPIEZA' AND status IN ('PENDIENTE','EN_PROCESO')",Long.class,r.get("habitacion_id"));
  if(!active.isEmpty())throw ApiException.conflicto("SOLICITUD_ACTIVA","Ya hay una solicitud de limpieza en curso. Consulta su estado antes de solicitar otra.");
  long id=jdbc.queryForObject("INSERT INTO requests(reservation_id,room_id,type,comment) VALUES (?,?,'LIMPIEZA',?) RETURNING id",Long.class,r.get("id"),r.get("habitacion_id"),p.comentario()==null?null:p.comentario().trim());
  historial.registrar(TipoEntidadHistorial.SOLICITUD,id,null,"PENDIENTE",Responsable.HUESPED,null);
  eventos.publicar(id);return Map.of("id",id,"estado","PENDIENTE");
 }
}
