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
  jdbc.update("UPDATE objetos_avisos SET estado='INCIERTO',ultimo_error='No se pudo confirmar el resultado. Revisa el envío antes de volver a contactar al huésped.' WHERE estado='ENVIANDO' AND iniciado_en<now()-interval '10 minutes'");
  for(int i=0;i<5;i++){
   Map<String,Object> aviso=tx.execute(status->{
    var rows=jdbc.queryForList("SELECT * FROM objetos_avisos WHERE estado='PENDIENTE' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED");
    if(rows.isEmpty())return null;var row=rows.getFirst();
    jdbc.update("UPDATE objetos_avisos SET estado='ENVIANDO',intentos=intentos+1,iniciado_en=now() WHERE id=?",row.get("id"));return row;
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
    jdbc.queryForList("SELECT id FROM objetos_encontrados WHERE id=? FOR UPDATE",aviso.get("objeto_id"));
    jdbc.update("UPDATE objetos_avisos SET estado=?,ultimo_error=?,enviado_en=CASE WHEN ?='ENVIADO' THEN now() ELSE NULL END WHERE id=?",result,message,result,aviso.get("id"));
    jdbc.update("INSERT INTO objetos_envio_eventos(aviso_id,tipo,intento,resultado,error) VALUES(?,'RESULTADO_SMTP',?,?,?)",aviso.get("id"),((Number)aviso.get("intentos")).intValue()+1,result,message);
    if(result.equals("ENVIADO"))jdbc.update("UPDATE objetos_encontrados SET notificado_en=(SELECT enviado_en FROM objetos_avisos WHERE id=?) WHERE id=?",aviso.get("id"),aviso.get("objeto_id"));
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
