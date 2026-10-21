package com.villaserena.api.piso;

import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Contenido privado ligado a la reserva, nunca a un ID local ni solo a la habitación. */
@RestController
@PreAuthorize("hasAnyRole('HUESPED','RECEPCION')")
public class EstanciaComunicacionController {
 private final JdbcTemplate jdbc;
 public EstanciaComunicacionController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Mensaje(@NotNull UUID clave,@NotBlank @Size(max=2000) String texto){}
 public record Documento(@NotNull UUID clave,@NotBlank @Size(max=150) String nombre,@NotBlank @Size(max=7000000) String base64){}
 public record Revision(@NotBlank @Pattern(regexp="ACEPTADO|RECHAZADO") String estado,@Size(max=500) String motivo){}
 private boolean guest(){return "HUESPED".equals(UsuarioActual.rol());}
 private Map<String,Object> reserva(String codigo,boolean lock){
  var rows=jdbc.queryForList("SELECT id,guest_id AS \"huesped_id\",status AS \"estado\" FROM reservations WHERE code=?"+(guest()?" AND guest_id=?":"")+(lock?" FOR UPDATE":""),guest()?new Object[]{codigo,UsuarioActual.id()}:new Object[]{codigo});
  if(rows.isEmpty())throw ApiException.noEncontrado();return rows.getFirst();
 }
 private long id(Map<String,Object> r){return ((Number)r.get("id")).longValue();}
 private ApiException invalid(String text){return ApiException.datosInvalidos(text,List.of());}
 private static final String CHAT="/api/v1/app/reservas/{codigo}/chat", STAFFCHAT="/api/v1/estancias/{codigo}/chat";
 @GetMapping({CHAT,STAFFCHAT}) public List<Map<String,Object>> mensajes(@PathVariable String codigo){var r=reserva(codigo,false);return jdbc.queryForList("SELECT id,author AS \"autor\",text AS \"texto\",created_at AS \"creadoEn\"  FROM stay_messages WHERE reservation_id=? ORDER BY id",id(r));}
 @PostMapping({CHAT,STAFFCHAT}) @Transactional public Map<String,Object> enviar(@PathVariable String codigo,@Valid @RequestBody Mensaje p){
  var r=reserva(codigo,true);String autor=guest()?"HUESPED":"RECEPCION";
  var prior=jdbc.queryForList("SELECT id,text AS \"texto\" FROM stay_messages WHERE reservation_id=? AND author=? AND author_id=? AND key=?",id(r),autor,UsuarioActual.id(),p.clave());
  if(!prior.isEmpty()){if(!prior.getFirst().get("texto").equals(p.texto().trim()))throw ApiException.conflicto("CLAVE_REUTILIZADA","El identificador ya corresponde a otro mensaje.");return prior.getFirst();}
  if(!"EN_ESTADIA".equals(r.get("estado")))throw ApiException.conflicto("CHAT_CERRADO","El chat está disponible durante la estancia, después del check-in.");
  long nuevo=jdbc.queryForObject("INSERT INTO stay_messages(reservation_id,author,author_id,key,text) VALUES(?,?,?,?,?) RETURNING id",Long.class,id(r),autor,UsuarioActual.id(),p.clave(),p.texto().trim());return Map.of("id",nuevo);
 }
 private static final String DOCS="/api/v1/app/reservas/{codigo}/documentos", STAFFDOCS="/api/v1/estancias/{codigo}/documentos";
 @GetMapping({DOCS,STAFFDOCS}) public List<Map<String,Object>> documentos(@PathVariable String codigo){var r=reserva(codigo,false);return jdbc.queryForList("SELECT id,name AS \"nombre\",mime,octet_length(content) AS bytes,status AS \"estado\",reason AS \"motivo\",created_at AS \"creadoEn\",reviewed_at AS \"revisadoEn\"  FROM checkin_documents WHERE reservation_id=? ORDER BY id",id(r));}
 @PostMapping(DOCS) @PreAuthorize("hasRole('HUESPED')") @Transactional public Map<String,Object> subir(@PathVariable String codigo,@Valid @RequestBody Documento p){
  var r=reserva(codigo,true);var prior=jdbc.queryForList("SELECT id  FROM checkin_documents WHERE reservation_id=? AND guest_id=? AND key=?",id(r),UsuarioActual.id(),p.clave());if(!prior.isEmpty())return prior.getFirst();
  if(!Set.of("CONFIRMADA","EN_ESTADIA").contains(r.get("estado")))throw ApiException.conflicto("ESTADO_INVALIDO","La reserva debe estar confirmada o en estancia para enviar documentos.");
  if(jdbc.queryForObject("SELECT count(*)  FROM checkin_documents WHERE reservation_id=?",Integer.class,id(r))>=10)throw invalid("Se permiten hasta diez documentos por reserva.");
  byte[] bytes;try{bytes=Base64.getDecoder().decode(p.base64());}catch(IllegalArgumentException e){throw invalid("No se pudo leer el archivo.");}
  if(bytes.length<8||bytes.length>5242880)throw invalid("El archivo debe pesar hasta 5 MiB.");String mime;
  boolean png=bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
  boolean jpg=bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;
  if(png||jpg){try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw invalid("Imagen inválida.");var reader=readers.next();try{reader.setInput(in);if(reader.getWidth(0)<1||reader.getHeight(0)<1||(long)reader.getWidth(0)*reader.getHeight(0)>16000000)throw invalid("La imagen es demasiado grande.");var image=reader.read(0);var out=new ByteArrayOutputStream();ImageIO.write(image,png?"png":"jpeg",out);bytes=out.toByteArray();mime=png?"image/png":"image/jpeg";}finally{reader.dispose();}}catch(IOException e){throw invalid("Imagen inválida.");}}
  else if(new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")&&new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("%%EOF")){DocumentosSeguros.validarPdf(bytes);mime="application/pdf";}else throw invalid("Solo se permiten JPEG, PNG y PDF.");
  if(bytes.length>5242880)throw invalid("El archivo debe pesar hasta 5 MiB.");
  String name=p.nombre().replaceAll("[^\\p{L}\\p{N} ._-]","_");long nuevo=jdbc.queryForObject("INSERT INTO checkin_documents(reservation_id,guest_id,key,name,mime,content) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,id(r),UsuarioActual.id(),p.clave(),name,mime,bytes);return Map.of("id",nuevo,"estado","PENDIENTE");
 }
 @GetMapping({DOCS+"/{documentoId}/archivo",STAFFDOCS+"/{documentoId}/archivo"}) public ResponseEntity<byte[]> archivo(@PathVariable String codigo,@PathVariable long documentoId){var r=reserva(codigo,false);var rows=jdbc.query("SELECT mime,content AS \"contenido\" FROM checkin_documents WHERE id=? AND reservation_id=?",(rs,n)->Map.of("mime",rs.getString(1),"bytes",rs.getBytes(2)),documentoId,id(r));if(rows.isEmpty())throw ApiException.noEncontrado();var d=rows.getFirst();return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=\"documento-"+documentoId+(d.get("mime").equals("application/pdf")?".pdf":d.get("mime").equals("image/png")?".png":".jpg")+"\"").header("Content-Security-Policy","sandbox; default-src 'none'").contentType(MediaType.parseMediaType((String)d.get("mime"))).body((byte[])d.get("bytes"));}
 @PostMapping(STAFFDOCS+"/{documentoId}/revisar") @PreAuthorize("hasRole('RECEPCION')") @Transactional public Map<String,Object> revisar(@PathVariable String codigo,@PathVariable long documentoId,@Valid @RequestBody Revision p){var r=reserva(codigo,true);if(p.estado().equals("RECHAZADO")&&(p.motivo()==null||p.motivo().isBlank()))throw invalid("Indica el motivo del rechazo.");int n=jdbc.update("UPDATE checkin_documents SET status=?,reason=?,reviewed_by=?,reviewed_at=now() WHERE id=? AND reservation_id=? AND status='PENDIENTE'",p.estado(),p.motivo(),UsuarioActual.id(),documentoId,id(r));if(n==0)throw ApiException.conflicto("DOCUMENTO_REVISADO","El documento ya fue revisado o no pertenece a esta reserva.");return Map.of("id",documentoId,"estado",p.estado());}
}
