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
  return Map.of("desde",desde.toString(),"hasta",hasta.toString(),"reservas",jdbc.queryForList("SELECT estado,canal,count(*) AS cantidad,sum(total) AS importe FROM reservas WHERE creada_en>=? AND creada_en<? GROUP BY estado,canal ORDER BY estado,canal",inicio,fin),"pagos",jdbc.queryForList("SELECT estado,metodo,count(*) AS cantidad,sum(monto) AS importe FROM pagos WHERE creado_en>=? AND creado_en<? GROUP BY estado,metodo ORDER BY estado,metodo",inicio,fin),"ocupacionActual",jdbc.queryForList("SELECT ocupacion,condicion,count(*) AS cantidad FROM habitaciones WHERE estado='ACTIVO' GROUP BY ocupacion,condicion ORDER BY ocupacion,condicion"));
 }
 public CatalogoOperativoController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/api/v1/incidencias/habitaciones")
 @PreAuthorize("hasAnyRole('RECEPCION','MANTENIMIENTO_LIMPIEZA')")
 public List<Map<String,Object>> habitaciones(){return jdbc.queryForList("SELECT id,numero,piso FROM habitaciones WHERE estado='ACTIVO' ORDER BY piso,numero");}
 @GetMapping("/api/v1/limpieza/mapa")
 @PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
 public List<Map<String,Object>> mapa(){return jdbc.queryForList("SELECT h.id,h.numero,h.piso,t.nombre AS tipo,h.ocupacion,h.condicion,h.limpieza_empleado_id AS \"empleadoId\",e.nombre_completo AS responsable FROM habitaciones h JOIN tipos_habitacion t ON t.id=h.tipo_habitacion_id LEFT JOIN empleados e ON e.id=h.limpieza_empleado_id WHERE h.estado='ACTIVO' ORDER BY h.piso,h.numero");}
 @GetMapping("/api/v1/admin/consulta/{seccion}")
 @PreAuthorize("hasRole('ADMIN')")
 public List<Map<String,Object>> admin(@PathVariable String seccion){return switch(seccion){
  case "panel"->jdbc.queryForList("SELECT h.id,h.numero,h.piso,t.nombre AS tipo,h.ocupacion,h.condicion,h.estado FROM habitaciones h JOIN tipos_habitacion t ON t.id=h.tipo_habitacion_id ORDER BY h.piso,h.numero");
  case "tarifas"->jdbc.queryForList("SELECT id,nombre,capacidad,precio_base,ajuste_fin_semana_pct,estado FROM tipos_habitacion ORDER BY id");
  case "personal"->jdbc.queryForList("SELECT id,nombre_completo,correo,telefono,rol,area,estado FROM empleados ORDER BY id");
  case "reportes"->jdbc.queryForList("SELECT estado,count(*) AS reservas,sum(total) AS total_reservas FROM reservas GROUP BY estado ORDER BY estado");
  case "finanzas"->jdbc.queryForList("SELECT estado,count(*) AS pagos,sum(monto) AS monto FROM pagos GROUP BY estado ORDER BY estado");
  default->throw com.villaserena.api.comun.ApiException.noEncontrado();};}
}
