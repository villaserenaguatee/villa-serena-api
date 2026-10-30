package com.villaserena.api.piso;

import java.util.*;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;

@RestController
@RequestMapping("/api/v1/estancias/{codigo}/chat")
@PreAuthorize("hasRole('RECEPCION')")
public class ChatRecepcionController {
 private final JdbcTemplate db;
 public ChatRecepcionController(JdbcTemplate db){this.db=db;}
 public record Editar(@NotBlank @Size(max=2000) String texto,@NotNull @Min(0) Long version){}
 public record Eliminar(@NotNull @Min(0) Long version){}
 private Map<String,Object> stay(String code,boolean lock){var rows=db.queryForList("SELECT id,status FROM reservations WHERE code=?"+(lock?" FOR UPDATE":""),code);if(rows.isEmpty())throw ApiException.noEncontrado();return rows.getFirst();}
 private long number(Map<String,Object> row,String field){return ((Number)row.get(field)).longValue();}
 private boolean own(Map<String,Object> row){return "RECEPCION".equals(row.get("author"))&&number(row,"author_id")==UsuarioActual.id();}
 private boolean editable(Map<String,Object> row,Map<String,Object> stay){return own(row)&&"EN_ESTADIA".equals(stay.get("status"))&&row.get("deleted_at")==null;}
 private Map<String,Object> view(Map<String,Object> row,Map<String,Object> stay){var out=new LinkedHashMap<String,Object>();out.put("id",row.get("id"));out.put("autor",row.get("author"));out.put("texto",row.get("deleted_at")==null?row.get("text"):"");out.put("creadoEn",((Timestamp)row.get("created_at")).toInstant());out.put("version",row.get("version"));out.put("puedeModificar",editable(row,stay));out.put("eliminado",row.get("deleted_at")!=null);if(row.get("edited_at") instanceof Timestamp edited)out.put("editadoEn",edited.toInstant());return out;}
 @GetMapping @Operation(operationId="mensajesChatRecepcion")
 public List<Map<String,Object>> list(@PathVariable String codigo){var stay=stay(codigo,false);return db.queryForList("SELECT * FROM stay_messages WHERE reservation_id=? ORDER BY id",stay.get("id")).stream().map(row->view(row,stay)).toList();}
 private Map<String,Object> message(Map<String,Object> stay,long id){var rows=db.queryForList("SELECT * FROM stay_messages WHERE id=? AND reservation_id=? FOR UPDATE",id,stay.get("id"));if(rows.isEmpty())throw ApiException.noEncontrado();var row=rows.getFirst();if(!own(row))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"ACCESO_DENEGADO","Solo puede modificar sus propios mensajes.");if(!"EN_ESTADIA".equals(stay.get("status")))throw ApiException.conflicto("CHAT_CERRADO","La estancia terminó. El historial es de solo lectura.");return row;}
 private void revision(Map<String,Object> row,long version){if(number(row,"version")!=version)throw ApiException.conflicto("MENSAJE_ACTUALIZADO","El mensaje cambió. Actualice la conversación antes de continuar.");}
 @PutMapping("/{mensajeId}") @Transactional @Operation(operationId="editarMensajeChatRecepcion")
 public Map<String,Object> edit(@PathVariable String codigo,@PathVariable long mensajeId,@Valid @RequestBody Editar p){var stay=stay(codigo,true);var row=message(stay,mensajeId);if(row.get("deleted_at")!=null)throw ApiException.conflicto("MENSAJE_ELIMINADO","El mensaje fue eliminado.");revision(row,p.version());db.update("UPDATE stay_messages SET text=?,edited_at=now(),version=version+1 WHERE id=?",p.texto().trim(),mensajeId);return view(db.queryForMap("SELECT * FROM stay_messages WHERE id=?",mensajeId),stay);}
 @DeleteMapping("/{mensajeId}") @Transactional @Operation(operationId="eliminarMensajeChatRecepcion")
 public Map<String,Object> delete(@PathVariable String codigo,@PathVariable long mensajeId,@Valid @RequestBody Eliminar p){var stay=stay(codigo,true);var row=message(stay,mensajeId);if(row.get("deleted_at")!=null)return view(row,stay);revision(row,p.version());db.update("UPDATE stay_messages SET deleted_at=now(),version=version+1 WHERE id=?",mensajeId);return view(db.queryForMap("SELECT * FROM stay_messages WHERE id=?",mensajeId),stay);}
}
