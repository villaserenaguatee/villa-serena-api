package com.villaserena.api.objetos;

import java.util.*;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

@Service
public class ObjetosService {
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final TemplateEngine templates;
 public ObjetosService(JdbcTemplate jdbc,ObjectMapper json,TemplateEngine templates){this.jdbc=jdbc;this.json=json;this.templates=templates;}
 private static final String QUERY="""
 SELECT o.id,o.description AS "descripcion",o.room_number AS "habitacionNumero",o.found_at AS "halladoEn",
 o.internal_note AS "notaInterna",o.photo IS NOT NULL AS "tieneFoto",o.notified_at AS "notificadoEn",
 o.physical_status AS "estadoFisico",o.returned_at AS "devueltoEn",
 a.status AS "estadoEnvio",a.attempts AS "intentos",a.last_error AS "ultimoError",a.recipient AS "destinatario",a.review::text AS revision,
 a.sent_at AS "enviadoEn",a.message_id AS "messageId",
 EXISTS(SELECT 1 FROM lost_item_delivery_events e WHERE e.notification_id=a.id AND e.type='REINTENTO_CONTROLADO') AS "reintentoControladoUsado"
  FROM lost_items o LEFT JOIN lost_item_notifications a ON a.item_id=o.id
 """;
 public List<Map<String,Object>> listar(){return jdbc.queryForList(QUERY+" ORDER BY o.id DESC");}
 public Map<String,Object> detalle(long id){var rows=jdbc.queryForList(QUERY+" WHERE o.id=?",id);if(rows.isEmpty())throw ApiException.noEncontrado();return rows.getFirst();}
 public record Foto(byte[] bytes,String mime){}
 public Foto foto(long id){return jdbc.query("SELECT photo AS \"foto\",photo_mime AS \"foto_mime\" FROM lost_items WHERE id=? AND photo IS NOT NULL",(rs,n)->new Foto(rs.getBytes(1),rs.getString(2)),id).stream().findFirst().orElseThrow(ApiException::noEncontrado);}
 static ApiException invalid(String message){return ApiException.datosInvalidos(message,List.of());}
 public static Foto validarFoto(String encoded){
  if(encoded==null||encoded.isBlank())return null;
  if(encoded.length()>7000000)throw invalid("La fotografía debe pesar como máximo 5 MiB.");
  try {
   // Solo JPEG/PNG por contenido, sin SVG, URLs remotas ni metadatos incrustados.
   byte[] bytes=Base64.getDecoder().decode(encoded.contains(",")?encoded.substring(encoded.indexOf(',')+1):encoded);
   if(bytes.length>5242880)throw invalid("La fotografía debe pesar como máximo 5 MiB.");
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid("Selecciona una fotografía JPEG o PNG válida.");
    ImageReader reader=readers.next();
    try{
     reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
     if(!Set.of("jpeg","jpg","png").contains(format)||reader.getWidth(0)<1||reader.getHeight(0)<1
       ||(long)reader.getWidth(0)*reader.getHeight(0)>16000000)throw invalid("Formato o dimensiones de fotografía no permitidos.");
     var image=reader.read(0);var output=new ByteArrayOutputStream();
     String target=format.equals("png")?"png":"jpeg";
     if(!ImageIO.write(image,target,output)||output.size()>5242880)throw invalid("La fotografía debe pesar como máximo 5 MiB.");
     return new Foto(output.toByteArray(),"image/"+target);
    }finally{reader.dispose();}
   }
  }catch(IllegalArgumentException|IOException e){throw invalid("No se pudo leer la fotografía. Usa JPEG o PNG.");}
 }
 @Transactional public Map<String,Object> crear(ObjetosController.Nuevo n){
  if(n.referenciaLocal()!=null){var old=jdbc.queryForList("SELECT id  FROM lost_items WHERE local_reference=?",n.referenciaLocal());if(!old.isEmpty())return detalle(((Number)old.getFirst().get("id")).longValue());}
  if(jdbc.queryForObject("SELECT count(*)  FROM rooms WHERE number=?",Integer.class,n.habitacionNumero())!=1)throw invalid("Selecciona una habitación existente.");
  Foto photo=validarFoto(n.fotoBase64());
  Long id=jdbc.queryForObject("""
    INSERT INTO lost_items(description,room_number,found_at,internal_note,local_reference,photo,photo_mime,created_by)
    VALUES(?,?,?,?,?,?,?,?) RETURNING id
    """,Long.class,n.descripcion().trim(),n.habitacionNumero(),n.halladoEn(),Objects.requireNonNullElse(n.notaInterna(),""),
    n.referenciaLocal(),photo==null?null:photo.bytes(),photo==null?null:photo.mime(),UsuarioActual.id());
  return detalle(id);
 }
 private void lock(long id){if(jdbc.queryForList("SELECT id  FROM lost_items WHERE id=? FOR UPDATE",id).isEmpty())throw ApiException.noEncontrado();}
 private void enResguardo(long id){if(!"EN_RESGUARDO".equals(jdbc.queryForObject("SELECT physical_status AS \"estado_fisico\" FROM lost_items WHERE id=?",String.class,id)))throw ApiException.conflicto("OBJETO_DEVUELTO","El objeto ya fue devuelto; no se enviará un aviso de hallazgo.");}
 private void editable(long id){enResguardo(id);if(jdbc.queryForObject("SELECT count(*)  FROM lost_item_notifications WHERE item_id=?",Integer.class,id)>0)throw ApiException.conflicto("AVISO_EXISTENTE","Este objeto ya tiene un aviso. Consulta su resultado o reintenta el mismo envío si falló.");}
 public Map<String,Object> leerDatos(String value){return json.readValue(value,new TypeReference<Map<String,Object>>(){});}
 public String html(Map<String,Object> data){var context=new Context();data.forEach(context::setVariable);return templates.process("correo/objeto-encontrado",context);}
 @Transactional public Map<String,Object> previsualizar(long id,ObjetosController.Revision r){
  lock(id);editable(id);
  var rows=jdbc.queryForList("""
   SELECT r.id AS reserva_id,h.id AS huesped_id,h.email AS "correo",h.full_name AS "nombre_completo" FROM reservations r JOIN guests h ON h.id=r.guest_id WHERE r.code=? AND h.id=?
   """,r.codigoReserva(),r.huespedId());
  if(rows.isEmpty())throw invalid("La reserva no corresponde al huésped seleccionado.");var recipient=rows.getFirst();
  String correo=r.correo().trim().toLowerCase(Locale.ROOT);
  if(!correo.equals(recipient.get("correo").toString().trim().toLowerCase(Locale.ROOT)))throw invalid("El correo debe coincidir con el registrado para el huésped confirmado.");
  var o=jdbc.queryForMap("SELECT description AS \"descripcion\",room_number AS \"habitacion_numero\",found_at AS \"hallado_en\",photo IS NOT NULL AS tiene_foto  FROM lost_items WHERE id=?",id);
  if(r.incluirFoto()&&!Boolean.TRUE.equals(o.get("tiene_foto")))throw invalid("Este objeto no tiene fotografía.");
  var data=new LinkedHashMap<String,Object>();
  data.put("nombreHuesped",recipient.get("nombre_completo"));data.put("descripcion",o.get("descripcion"));data.put("habitacionNumero",o.get("habitacion_numero"));
  data.put("fechaHallazgo",DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm",Locale.of("es","GT")).format(((Timestamp)o.get("hallado_en")).toInstant().atZone(ZoneId.of("America/Guatemala"))));
  data.put("fotoUrl",r.incluirFoto()?"cid:objeto-foto":null);data.put("mensaje",Objects.requireNonNullElse(r.mensaje(),"").trim());
  data.put("reservaId",recipient.get("reserva_id"));data.put("huespedId",r.huespedId());data.put("correo",correo);data.put("codigoReserva",r.codigoReserva());
  UUID revision=UUID.randomUUID();
  jdbc.update("UPDATE lost_items SET review=?,data_revision=?::jsonb,reviewed_by=? WHERE id=?",revision,json.writeValueAsString(data),UsuarioActual.id(),id);
  return Map.of("revision",revision.toString(),"asunto","Objeto encontrado en Villa Serena","correo",correo,
   "nombreHuesped",recipient.get("nombre_completo"),"codigoReserva",r.codigoReserva(),"html",html(data).replace("cid:villa-serena-logo","/villa-serena-logo.png").replace("cid:objeto-foto","/api/objetos/"+id+"/foto"));
 }
 @Transactional public Map<String,Object> enviar(long id,ObjetosController.Confirmacion c){
  lock(id);var old=jdbc.queryForList("SELECT review::text AS revision  FROM lost_item_notifications WHERE item_id=?",id);
  if(!old.isEmpty()){if(!old.getFirst().get("revision").equals(c.revision().toString()))throw ApiException.conflicto("AVISO_EXISTENTE","Ya existe un aviso para este objeto.");return detalle(id);}
  enResguardo(id);
  var o=jdbc.queryForMap("SELECT review AS \"revision\",data_revision::text AS datos,reviewed_by AS \"revisado_por\",photo AS \"foto\",photo_mime AS \"foto_mime\" FROM lost_items WHERE id=?",id);
  if(!c.revision().equals(o.get("revision"))||!Objects.equals(((Number)o.get("revisado_por")).longValue(),UsuarioActual.id()))throw ApiException.conflicto("REVISION_VENCIDA","Revisa de nuevo el mensaje y confirma el destinatario.");
  var data=leerDatos(o.get("datos").toString());
  // Si cambió la identidad desde la vista previa, no enviamos una aprobación antigua.
  if(jdbc.queryForObject("SELECT count(*)  FROM reservations r JOIN guests h ON h.id=r.guest_id WHERE r.id=? AND h.id=? AND lower(h.email)=? AND h.full_name=?",Integer.class,data.get("reservaId"),data.get("huespedId"),data.get("correo"),data.get("nombreHuesped"))!=1)throw ApiException.conflicto("DESTINATARIO_CAMBIADO","Los datos del huésped cambiaron. Revisa de nuevo antes de enviar.");
  boolean photo=data.get("fotoUrl")!=null;
  jdbc.update("""
   INSERT INTO lost_item_notifications(item_id,review,reservation_id,guest_id,recipient,data,photo,photo_mime,confirmed_by,message_id)
   VALUES(?,?,?,?,?,?::jsonb,?,?,?,?)
   """,id,c.revision(),data.get("reservaId"),data.get("huespedId"),data.get("correo"),o.get("datos"),photo?o.get("foto"):null,photo?o.get("foto_mime"):null,UsuarioActual.id(),"<objeto-"+UUID.randomUUID()+"@villaserena.local>");
  return detalle(id);
 }
 @Transactional public Map<String,Object> reintentar(long id,ObjetosController.Reintento r){
  lock(id);var rows=jdbc.queryForList("SELECT status AS \"estado\",review::text AS revision,attempts AS \"intentos\" FROM lost_item_notifications WHERE item_id=? FOR UPDATE",id);
  if(rows.isEmpty())throw ApiException.noEncontrado();var a=rows.getFirst();
  if(!a.get("revision").equals(r.revision().toString()))throw ApiException.conflicto("REVISION_VENCIDA","El aviso no corresponde a la revisión indicada.");
  if(Set.of("PENDIENTE","ENVIANDO","ENVIADO").contains(a.get("estado")))return detalle(id);
  enResguardo(id);
  if(Boolean.TRUE.equals(detalle(id).get("reintentoControladoUsado")))throw ApiException.conflicto("REINTENTO_CONSUMIDO","Ya se utilizó el único reintento controlado; no se enviará otro aviso.");
  if(!"FALLIDO".equals(a.get("estado"))||((Number)a.get("intentos")).intValue()!=r.intentos())throw ApiException.conflicto("NO_REINTENTABLE","El resultado no permite reenvío automático. Verifica el último intento antes de continuar.");
  jdbc.update("UPDATE lost_item_notifications SET status='PENDIENTE',last_error=NULL WHERE item_id=?",id);return detalle(id);
 }
 @Transactional public Map<String,Object> devolver(long id){
  lock(id);
  if(jdbc.queryForObject("SELECT count(*)  FROM lost_item_notifications WHERE item_id=? AND status IN ('PENDIENTE','ENVIANDO')",Integer.class,id)>0)throw ApiException.conflicto("ENVIO_EN_CURSO","Espera a que termine el envío antes de registrar la devolución.");
  jdbc.update("UPDATE lost_items SET physical_status='DEVUELTO',returned_at=now(),returned_by=? WHERE id=? AND physical_status<>'DEVUELTO'",UsuarioActual.id(),id);
  return detalle(id);
 }
 public Map<String,Object> vistaAviso(long id){
  var rows=jdbc.queryForList("SELECT data::text AS datos,review::text AS revision,recipient AS \"destinatario\" FROM lost_item_notifications WHERE item_id=?",id);
  if(rows.isEmpty())throw ApiException.noEncontrado();var row=rows.getFirst();var data=leerDatos(row.get("datos").toString());
  var result=new LinkedHashMap<String,Object>();result.putAll(data);result.put("revision",row.get("revision"));result.put("correo",row.get("destinatario"));
  result.put("asunto","Objeto encontrado en Villa Serena");
  result.put("html",html(data).replace("cid:villa-serena-logo","/villa-serena-logo.png").replace("cid:objeto-foto","/api/objetos/"+id+"/aviso/foto"));
  return result;
 }
 public Foto fotoAviso(long id){return jdbc.query("SELECT photo AS \"foto\",photo_mime AS \"foto_mime\" FROM lost_item_notifications WHERE item_id=? AND photo IS NOT NULL",(rs,n)->new Foto(rs.getBytes(1),rs.getString(2)),id).stream().findFirst().orElseThrow(ApiException::noEncontrado);}
 @Transactional public Map<String,Object> reintentarIncierto(long id,ObjetosController.ReintentoIncierto r){
  if(!r.aceptoPosibleDuplicado()||!r.destinatarioConfirmado()||!r.mensajeConfirmado())throw invalid("Debes revisar el destinatario, el mensaje y aceptar el posible duplicado.");
  lock(id);var rows=jdbc.queryForList("SELECT id,item_id AS \"objeto_id\",review AS \"revision\",reservation_id AS \"reserva_id\",guest_id AS \"huesped_id\",recipient AS \"destinatario\",data AS \"datos\",photo AS \"foto\",photo_mime AS \"foto_mime\",status AS \"estado\",attempts AS \"intentos\",last_error AS \"ultimo_error\",confirmed_by AS \"confirmado_por\",created_at AS \"creado_en\",started_at AS \"iniciado_en\",sent_at AS \"enviado_en\",message_id FROM lost_item_notifications WHERE item_id=? FOR UPDATE",id);
  if(rows.isEmpty())throw ApiException.noEncontrado();var row=rows.getFirst();var data=leerDatos(row.get("datos").toString());
  if(!r.revision().equals(row.get("revision"))||!r.codigoReserva().equals(data.get("codigoReserva"))
    ||r.huespedId()!=((Number)data.get("huespedId")).longValue()||!r.correo().trim().equalsIgnoreCase(row.get("destinatario").toString()))throw invalid("Selecciona y confirma al mismo destinatario y reserva del aviso guardado.");
  // La repetición de la misma confirmación nunca genera un segundo reintento.
  if(jdbc.queryForObject("SELECT count(*)  FROM lost_item_delivery_events WHERE notification_id=? AND type='REINTENTO_CONTROLADO'",Integer.class,row.get("id"))>0)return detalle(id);
  enResguardo(id);
  if(!"INCIERTO".equals(row.get("estado"))||((Number)row.get("intentos")).intValue()!=r.intentos())throw ApiException.conflicto("RESULTADO_CAMBIADO","El resultado cambió. Consulta y revisa de nuevo el aviso antes de continuar.");
  if(jdbc.queryForObject("SELECT count(*)  FROM reservations r JOIN guests h ON h.id=r.guest_id WHERE r.id=? AND h.id=? AND lower(h.email)=lower(?) AND h.full_name=?",Integer.class,row.get("reserva_id"),row.get("huesped_id"),row.get("destinatario"),data.get("nombreHuesped"))!=1)throw ApiException.conflicto("DESTINATARIO_CAMBIADO","Los datos de la reserva o del huésped cambiaron; no se puede reenviar esta revisión.");
  jdbc.update("""
    INSERT INTO lost_item_delivery_events(notification_id,type,attempt,previous_status,error,employee_id,detail)
    SELECT id,'REINTENTO_CONTROLADO',attempts+1,status,last_error,?,
      jsonb_build_object('revision',review::text,'destinatario',recipient,'iniciadoAnterior',started_at,'intentosAnteriores',attempts,'riesgoAceptado',true,'messageId',message_id)
    FROM lost_item_notifications WHERE id=?
    """,UsuarioActual.id(),row.get("id"));
  jdbc.update("UPDATE lost_item_notifications SET status='PENDIENTE',last_error=NULL WHERE id=?",row.get("id"));
  return detalle(id);
 }
}
