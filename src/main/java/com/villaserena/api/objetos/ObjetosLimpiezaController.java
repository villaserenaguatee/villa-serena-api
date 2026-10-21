package com.villaserena.api.objetos;
import java.util.*;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.reservas.IdempotenciaReservas;
@RestController @RequestMapping("/api/v1/limpieza/objetos")
@PreAuthorize("hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.limpieza()")
public class ObjetosLimpiezaController {
 private final JdbcTemplate jdbc;private final ObjetosService service;private final IdempotenciaReservas requests;
 public ObjetosLimpiezaController(JdbcTemplate jdbc,ObjetosService service,IdempotenciaReservas requests){this.jdbc=jdbc;this.service=service;this.requests=requests;}
 public record Nuevo(@NotNull UUID clave,@NotBlank @Size(max=300) String descripcion,@NotBlank @Size(max=20) String habitacionNumero,@NotNull OffsetDateTime halladoEn,@Size(max=500) String notaInterna,@Size(max=7000000) String fotoBase64){}
 private static final String SELECT="SELECT id,description AS \"descripcion\",room_number AS \"habitacionNumero\",found_at AS \"halladoEn\",internal_note AS \"notaInterna\",photo IS NOT NULL AS \"tieneFoto\",physical_status AS \"estadoFisico\",notified_at AS \"notificadoEn\",returned_at AS \"devueltoEn\"  FROM lost_items WHERE created_by=?";
 @GetMapping public List<Map<String,Object>> lista(){return jdbc.queryForList(SELECT+" ORDER BY id DESC",UsuarioActual.id());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<?,?> crear(@Valid @RequestBody Nuevo n){return requests.ejecutar("objeto-limpieza:"+UsuarioActual.id(),n.clave().toString(),n,Map.class,()->{if(jdbc.queryForObject("SELECT count(*)  FROM rooms WHERE number=? AND status='ACTIVO'",Integer.class,n.habitacionNumero())!=1)throw ApiException.datosInvalidos("Selecciona una habitación activa.",List.of());var r=service.crear(new ObjetosController.Nuevo(n.descripcion(),n.habitacionNumero(),n.halladoEn(),n.notaInterna(),"limpieza:"+UsuarioActual.id()+":"+n.clave(),n.fotoBase64()));return jdbc.queryForMap(SELECT+" AND id=?",UsuarioActual.id(),r.get("id"));});}
 @GetMapping("/{id}/foto") public ResponseEntity<byte[]> foto(@PathVariable long id){if(jdbc.queryForObject("SELECT count(*)  FROM lost_items WHERE id=? AND created_by=?",Integer.class,id,UsuarioActual.id())!=1)throw ApiException.noEncontrado();var f=service.foto(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'").body(f.bytes());}
}
