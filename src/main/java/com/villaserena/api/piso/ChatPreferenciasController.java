package com.villaserena.api.piso;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;

@RestController @RequestMapping("/api/v1/estancias") @PreAuthorize("hasRole('RECEPCION')")
public class ChatPreferenciasController {
 private final JdbcTemplate db;
 public ChatPreferenciasController(JdbcTemplate db){this.db=db;}
 public record Cambio(@NotNull @Pattern(regexp="favorita|archivada|eliminar|leido") String accion,Boolean valor,@Min(0) Long hasta){}
 @GetMapping("/chat-preferencias") @Operation(operationId="preferenciasChatRecepcion")
 public List<Map<String,Object>> list(){return db.queryForList("""
  SELECT r.code AS codigo,coalesce(p.favorite,false) AS favorita,coalesce(p.archived,false) AS archivada,
   coalesce(p.removed,false) AS eliminada,coalesce(max(m.id),0) AS "ultimoId",
   count(m.id) FILTER(WHERE m.author='HUESPED' AND m.deleted_at IS NULL AND m.id>coalesce(p.last_read_id,0)) AS "noLeidos"
  FROM reservations r LEFT JOIN reception_chat_preferences p ON p.reservation_id=r.id AND p.employee_id=?
   LEFT JOIN stay_messages m ON m.reservation_id=r.id
  WHERE r.status IN ('EN_ESTADIA','FINALIZADA') GROUP BY r.id,r.code,p.favorite,p.archived,p.removed,p.last_read_id ORDER BY r.id
  """,UsuarioActual.id());}
 @PostMapping("/{codigo}/chat-preferencias") @Transactional @Operation(operationId="guardarPreferenciasChatRecepcion")
 public Map<String,Object> change(@PathVariable String codigo,@Valid @RequestBody Cambio p){
  if(Set.of("favorita","archivada").contains(p.accion())&&p.valor()==null||"leido".equals(p.accion())&&p.hasta()==null)throw ApiException.datosInvalidos("Indique el valor de la preferencia.",List.of());
  var rows=db.queryForList("SELECT id,status FROM reservations WHERE code=? FOR UPDATE",codigo);if(rows.isEmpty())throw ApiException.noEncontrado();var stay=rows.getFirst();if(!Set.of("EN_ESTADIA","FINALIZADA").contains(stay.get("status")))throw ApiException.conflicto("CHAT_NO_INICIADO","La conversación debe corresponder a una estancia iniciada.");
  db.update("INSERT INTO reception_chat_preferences(employee_id,reservation_id) VALUES(?,?) ON CONFLICT DO NOTHING",UsuarioActual.id(),stay.get("id"));
  switch(p.accion()){
   case "favorita" -> db.update("UPDATE reception_chat_preferences SET favorite=? WHERE employee_id=? AND reservation_id=?",p.valor(),UsuarioActual.id(),stay.get("id"));
   case "archivada" -> db.update("UPDATE reception_chat_preferences SET archived=? WHERE employee_id=? AND reservation_id=?",p.valor(),UsuarioActual.id(),stay.get("id"));
   case "eliminar" -> db.update("UPDATE reception_chat_preferences SET removed=true WHERE employee_id=? AND reservation_id=?",UsuarioActual.id(),stay.get("id"));
   case "leido" -> db.update("UPDATE reception_chat_preferences SET last_read_id=greatest(last_read_id,least(?,coalesce((SELECT max(id) FROM stay_messages WHERE reservation_id=?),0))) WHERE employee_id=? AND reservation_id=?",p.hasta(),stay.get("id"),UsuarioActual.id(),stay.get("id"));
   default -> throw new IllegalArgumentException("Acción no validada");
  }
  return Map.of("codigo",codigo);
 }
}
