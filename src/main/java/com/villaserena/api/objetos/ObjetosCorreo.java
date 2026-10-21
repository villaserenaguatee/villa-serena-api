package com.villaserena.api.objetos;

import java.net.*;
import java.util.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.villaserena.api.config.PropiedadesVillaSerena;

/** Cola persistente específica. No altera los eventos ni reintentos de otros correos. */
@Component
public class ObjetosCorreo {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ObjetosCorreo.class);
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final JavaMailSender sender;
 private final ObjetosService objects;private final String from;
 public ObjetosCorreo(JdbcTemplate jdbc,PlatformTransactionManager manager,JavaMailSender sender,ObjetosService objects,PropiedadesVillaSerena properties){
  this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.sender=sender;this.objects=objects;this.from=properties.notificaciones().remitente();
 }
 /** Tras un envío interrumpido nunca asumimos que SMTP no lo recibió. */
 @Scheduled(fixedDelayString="PT10S",initialDelayString="PT15S")
 public void procesar(){
  jdbc.update("UPDATE lost_item_notifications SET status='INCIERTO',last_error='No se pudo confirmar el resultado. Revisa el envío antes de volver a contactar al huésped.' WHERE status='ENVIANDO' AND started_at<now()-interval '10 minutes'");
  for(int i=0;i<5;i++){
   Map<String,Object> aviso=tx.execute(status->{
    var rows=jdbc.queryForList("SELECT id,item_id AS \"objeto_id\",review AS \"revision\",reservation_id AS \"reserva_id\",guest_id AS \"huesped_id\",recipient AS \"destinatario\",data AS \"datos\",photo AS \"foto\",photo_mime AS \"foto_mime\",status AS \"estado\",attempts AS \"intentos\",last_error AS \"ultimo_error\",confirmed_by AS \"confirmado_por\",created_at AS \"creado_en\",started_at AS \"iniciado_en\",sent_at AS \"enviado_en\",message_id FROM lost_item_notifications WHERE status='PENDIENTE' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED");
    if(rows.isEmpty())return null;var row=rows.getFirst();
    jdbc.update("UPDATE lost_item_notifications SET status='ENVIANDO',attempts=attempts+1,started_at=now() WHERE id=?",row.get("id"));return row;
   });
   if(aviso==null)return;
   String state="ENVIADO",error=null;boolean smtpStarted=false;
   try{MimeMessage mail=crearMensaje(aviso);smtpStarted=true;sender.send(mail);}catch(Exception e){
    state=!smtpStarted||rechazoConfirmado(e)?"FALLIDO":"INCIERTO";
    error=!smtpStarted?"No se pudo preparar el correo. No se ha enviado; vuelve a intentarlo después de corregir el problema.":state.equals("FALLIDO")?"No se pudo conectar o el servidor de correo no aceptó el envío. Comprueba la red y configuración antes de reintentar.":"No se pudo confirmar si el servidor de correo aceptó el mensaje. Revisa el resultado; no se reenviará automáticamente.";
    // Solo nombres de excepciones: nunca contraseña, contenido, foto o trazas de protocolo.
    var types=new StringJoiner(" -> ");Set<Throwable> seen=new HashSet<>();for(Throwable t=e;t!=null&&seen.add(t);t=t.getCause())types.add(t.getClass().getSimpleName());
    LOG.warn("Aviso objeto {}: {}; fase {}; causas {}",aviso.get("id"),state,smtpStarted?"SMTP":"PREPARACION",types);
   }
   final String result=state,message=error;
   // ENVIANDO se confirmó antes de SMTP: una caída aquí no vuelve a ponerlo en cola.
   tx.executeWithoutResult(status->{
    jdbc.queryForList("SELECT id  FROM lost_items WHERE id=? FOR UPDATE",aviso.get("objeto_id"));
    jdbc.update("UPDATE lost_item_notifications SET status=?,last_error=?,sent_at=CASE WHEN ?='ENVIADO' THEN now() ELSE NULL END WHERE id=?",result,message,result,aviso.get("id"));
    jdbc.update("INSERT INTO lost_item_delivery_events(notification_id,type,attempt,result,error) VALUES(?,'RESULTADO_SMTP',?,?,?)",aviso.get("id"),((Number)aviso.get("intentos")).intValue()+1,result,message);
    if(result.equals("ENVIADO"))jdbc.update("UPDATE lost_items SET notified_at=(SELECT sent_at FROM lost_item_notifications WHERE id=?) WHERE id=?",aviso.get("id"),aviso.get("objeto_id"));
   });
  }
 }
 public MimeMessage crearMensaje(Map<String,Object> aviso) throws MessagingException {
  var data=objects.leerDatos(aviso.get("datos").toString());
  MimeMessage mail=new MimeMessage(sender.createMimeMessage().getSession()){
   @Override protected void updateMessageID() throws MessagingException {setHeader("Message-ID",aviso.get("message_id").toString());}
  };
  var helper=new MimeMessageHelper(mail,true,"UTF-8");
  helper.setFrom(from);helper.setReplyTo(new jakarta.mail.internet.InternetAddress(from).getAddress());helper.setTo(aviso.get("destinatario").toString());
  helper.setSubject("Objeto encontrado en Villa Serena");helper.setText(objects.html(data),true);
  helper.addInline("villa-serena-logo",new ClassPathResource("correo/villa-serena-logo.png"),"image/png");
  if(aviso.get("foto") instanceof byte[] foto){helper.addInline("objeto-foto",new ByteArrayResource(foto),aviso.get("foto_mime").toString());}
  return mail;
 }
 public static boolean rechazoConfirmado(Throwable error){
  Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
  var pending=new ArrayDeque<Throwable>();pending.add(error);boolean rejected=false;
  while(!pending.isEmpty()){
   var e=pending.remove();if(!seen.add(e))continue;
   if(e instanceof SendFailedException failed&&failed.getValidSentAddresses()!=null&&failed.getValidSentAddresses().length>0)return false;
   if(e instanceof MailAuthenticationException||e instanceof ConnectException||e instanceof UnknownHostException)rejected=true;
   // En esta versión de Spring esta excepción se construye antes de MAIL/DATA,
   // al fallar connectTransport. Un timeout durante DATA sigue siendo incierto.
   if(e instanceof MailSendException m&&m.getMessage()!=null&&m.getMessage().startsWith("Mail server connection failed"))rejected=true;
   if(e.getClass().getSimpleName().equals("MailConnectException"))rejected=true;
   // Códigos SMTP negativos explícitos, sin depender de una biblioteca SMTP concreta.
   try{Object code=e.getClass().getMethod("getReturnCode").invoke(e);if(code instanceof Integer c&&c>=400&&c<600)rejected=true;}catch(ReflectiveOperationException ignored){}
   if(e.getCause()!=null)pending.add(e.getCause());
   if(e instanceof MessagingException m&&m.getNextException()!=null)pending.add(m.getNextException());
   if(e instanceof MailSendException m)for(Exception nested:m.getFailedMessages().values())pending.add(nested);
  }
  return rejected;
 }
}
