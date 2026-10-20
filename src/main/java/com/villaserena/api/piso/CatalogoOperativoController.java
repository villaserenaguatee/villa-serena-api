package com.villaserena.api.piso;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class CatalogoOperativoController {
 private final JdbcTemplate jdbc;
 @GetMapping("/api/v1/admin/reportes")
 @PreAuthorize("hasRole('ADMIN')")
 public Map<String,Object> reportes(@RequestParam java.time.LocalDate desde,@RequestParam java.time.LocalDate hasta){
  if(hasta.isBefore(desde)||java.time.temporal.ChronoUnit.DAYS.between(desde,hasta)>366)throw new com.villaserena.api.comun.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"PERIODO_INVALIDO","Selecciona un período de hasta 367 días.",List.of());
  var inicio=desde.atStartOfDay(java.time.ZoneId.of("America/Guatemala")).toOffsetDateTime();var fin=hasta.plusDays(1).atStartOfDay(java.time.ZoneId.of("America/Guatemala")).toOffsetDateTime();
  return Map.of("desde",desde.toString(),"hasta",hasta.toString(),"reservas",jdbc.queryForList("SELECT status AS \"estado\",channel AS \"canal\",count(*) AS cantidad,sum(total) AS importe  FROM reservations WHERE created_at>=? AND created_at<? GROUP BY status,channel ORDER BY status,channel",inicio,fin),"pagos",jdbc.queryForList("SELECT status AS \"estado\",method AS \"metodo\",count(*) AS cantidad,sum(amount) AS importe  FROM payments WHERE created_at>=? AND created_at<? GROUP BY status,method ORDER BY status,method",inicio,fin),"ocupacionActual",jdbc.queryForList("SELECT occupancy AS \"ocupacion\",condition AS \"condicion\",count(*) AS cantidad  FROM rooms WHERE status='ACTIVO' GROUP BY occupancy,condition ORDER BY occupancy,condition"));
 }
 public CatalogoOperativoController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/api/v1/incidencias/habitaciones")
 @PreAuthorize("hasAnyRole('RECEPCION','MANTENIMIENTO_LIMPIEZA')")
 public List<Map<String,Object>> habitaciones(){return jdbc.queryForList("SELECT id,number AS \"numero\",floor AS \"piso\" FROM rooms WHERE status='ACTIVO' ORDER BY floor,number");}
 @GetMapping("/api/v1/limpieza/mapa")
 @PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
 public List<Map<String,Object>> mapa(){return jdbc.queryForList("SELECT h.id,h.number AS \"numero\",h.floor AS \"piso\",t.name AS tipo,h.occupancy AS \"ocupacion\",h.condition AS \"condicion\",h.cleaning_employee_id AS \"empleadoId\",e.full_name AS responsable  FROM rooms h JOIN room_types t ON t.id=h.room_type_id LEFT JOIN employees e ON e.id=h.cleaning_employee_id WHERE h.status='ACTIVO' ORDER BY h.floor,h.number");}
 @GetMapping("/api/v1/admin/consulta/{seccion}")
 @PreAuthorize("hasRole('ADMIN')")
 public List<Map<String,Object>> admin(@PathVariable String seccion){return switch(seccion){
  case "panel"->jdbc.queryForList("SELECT h.id,h.number AS \"numero\",h.floor AS \"piso\",t.name AS tipo,h.occupancy AS \"ocupacion\",h.condition AS \"condicion\",h.status AS \"estado\" FROM rooms h JOIN room_types t ON t.id=h.room_type_id ORDER BY h.floor,h.number");
  case "tarifas"->jdbc.queryForList("SELECT id,name AS \"nombre\",capacity AS \"capacidad\",base_price AS \"precio_base\",weekend_adjustment_pct AS \"ajuste_fin_semana_pct\",status AS \"estado\" FROM room_types ORDER BY id");
  case "personal"->jdbc.queryForList("SELECT id,full_name AS \"nombre_completo\",email AS \"correo\",phone AS \"telefono\",role AS \"rol\",area,status AS \"estado\" FROM employees ORDER BY id");
  case "reportes"->jdbc.queryForList("SELECT status AS \"estado\",count(*) AS reservas,sum(total) AS total_reservas  FROM reservations GROUP BY status ORDER BY status");
  case "finanzas"->jdbc.queryForList("SELECT status AS \"estado\",count(*) AS pagos,sum(amount) AS monto  FROM payments GROUP BY status ORDER BY status");
  default->throw com.villaserena.api.comun.ApiException.noEncontrado();};}
}
