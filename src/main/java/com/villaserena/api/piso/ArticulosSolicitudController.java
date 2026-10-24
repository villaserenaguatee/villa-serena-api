package com.villaserena.api.piso;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.villaserena.api.piso.SolicitudesHuespedService.Item;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.*;
import com.villaserena.api.reservas.IdempotenciaReservas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@RestController
@PreAuthorize("hasAnyRole('HUESPED','RECEPCION')")
public class ArticulosSolicitudController {
 private final JdbcTemplate jdbc;private final SolicitudesHuespedService solicitudes;private final IdempotenciaReservas idempotencia;
 public ArticulosSolicitudController(JdbcTemplate jdbc,SolicitudesHuespedService solicitudes,IdempotenciaReservas idempotencia){this.jdbc=jdbc;this.solicitudes=solicitudes;this.idempotencia=idempotencia;}
 public record Peticion(@NotNull UUID clave,@NotEmpty @Size(max=20) List<@NotNull @Valid Item> articulos,@Size(max=500) String comentario){}
 public record Limpieza(@Size(max=500) String comentario){}
 private boolean guest(){return "HUESPED".equals(UsuarioActual.rol());}
 private Map<String,Object> reserva(String codigo){var rows=jdbc.queryForList("SELECT id,room_id AS \"habitacion_id\",status AS \"estado\" FROM reservations WHERE code=?"+(guest()?" AND guest_id=?":"")+" FOR UPDATE",guest()?new Object[]{codigo,UsuarioActual.id()}:new Object[]{codigo});if(rows.isEmpty())throw ApiException.noEncontrado();var r=rows.getFirst();if(!"EN_ESTADIA".equals(r.get("estado"))||r.get("habitacion_id")==null)throw ApiException.conflicto("ESTADIA_REQUERIDA","La reserva debe estar en estancia y tener habitación.");return r;}
 @GetMapping({"/api/v1/app/reservas/{codigo}/articulos","/api/v1/estancias/{codigo}/articulos"}) @Transactional public List<Map<String,Object>> catalogo(@PathVariable String codigo){reserva(codigo);return jdbc.queryForList("SELECT id,name AS \"nombre\",maximum_quantity AS \"cantidadMaxima\"  FROM articles ORDER BY name");}
 @PostMapping({"/api/v1/app/reservas/{codigo}/solicitudes-articulos","/api/v1/estancias/{codigo}/solicitudes-articulos"}) @Transactional public Map<?,?> crear(@PathVariable String codigo,@Valid @RequestBody Peticion p){
  reserva(codigo);return idempotencia.ejecutar("articulos:"+UsuarioActual.rol()+":"+UsuarioActual.id()+":"+codigo,p.clave().toString(),p,Map.class,()->{
   var solicitud=solicitudes.articulos(codigo,p.articulos(),p.comentario());
   return Map.of("id",solicitud.id(),"estado",solicitud.estado());
  });
 }
 @PostMapping("/api/v1/estancias/{codigo}/solicitudes-limpieza") @PreAuthorize("hasRole('RECEPCION')") @Transactional public Map<String,Object> limpieza(@PathVariable String codigo,@Valid @RequestBody Limpieza p){var solicitud=solicitudes.limpieza(codigo,p.comentario());return Map.of("id",solicitud.id(),"estado",solicitud.estado());}
 @GetMapping("/api/v1/estancias/{codigo}/solicitudes") @PreAuthorize("hasRole('RECEPCION')") @Transactional public List<Map<String,Object>> lista(@PathVariable String codigo){var r=reserva(codigo);return jdbc.queryForList("SELECT id,type AS \"tipo\",status AS \"estado\",comment AS \"comentario\",created_at AS \"creadaEn\"  FROM requests WHERE reservation_id=? ORDER BY id DESC",r.get("id"));}
}
