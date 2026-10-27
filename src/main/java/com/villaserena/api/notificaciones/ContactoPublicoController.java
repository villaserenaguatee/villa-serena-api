package com.villaserena.api.notificaciones;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.core.io.ClassPathResource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;

@RestController
@RequestMapping("/api/v1/publico/contacto")
public class ContactoPublicoController {
 private final JdbcTemplate jdbc;private final TransactionTemplate transaccion;private final JavaMailSender correo;private final TemplateEngine plantillas;private final String remitente;private final ContactoCorreoConfig configuracion;
 public ContactoPublicoController(JdbcTemplate jdbc,PlatformTransactionManager tm,JavaMailSender correo,TemplateEngine plantillas,PropiedadesVillaSerena propiedades,ContactoCorreoConfig configuracion){this.jdbc=jdbc;transaccion=new TransactionTemplate(tm);this.correo=correo;this.plantillas=plantillas;remitente=propiedades.notificaciones().remitente();this.configuracion=configuracion;}
 public record Peticion(@NotBlank @Size(max=150) @Pattern(regexp="[^\\r\\n]+")String nombre,@NotBlank @Email @Size(max=150) @Pattern(regexp="[^\\r\\n]+")String correo,@NotBlank @Size(max=5000)String mensaje){}
 public record Resultado(UUID id,String estado){}
 private String huella(Peticion p){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((p.nombre()+"\u0000"+p.correo()+"\u0000"+p.mensaje()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @PostMapping public Resultado enviar(@Valid @RequestBody Peticion entrada,@RequestHeader("Idempotency-Key")UUID id){
  var p=new Peticion(entrada.nombre().trim(),entrada.correo().trim(),entrada.mensaje().trim());String hash=huella(p);
  boolean enviar=Boolean.TRUE.equals(transaccion.execute(status->{
   int nuevo=jdbc.update("INSERT INTO web_contact(id,fingerprint,name,email,message,status) VALUES (?,?,?,?,?,'ENVIANDO') ON CONFLICT DO NOTHING",id,hash,p.nombre(),p.correo(),p.mensaje());
   var r=jdbc.queryForMap("SELECT fingerprint AS \"huella\",status AS \"estado\" FROM web_contact WHERE id=? FOR UPDATE",id);
   if(!hash.equals(r.get("huella")))throw ApiException.conflicto("CONTACTO_DATOS_DISTINTOS","El intento anterior corresponde a otro mensaje.");
   if(nuevo==1){configuracion.exigirDestinatario();return true;}
   if("FALLIDO".equals(r.get("estado"))){configuracion.exigirDestinatario();jdbc.update("UPDATE web_contact SET status='ENVIANDO',attempts=attempts+1,error_code=NULL,updated_at=now() WHERE id=?",id);return true;}
   return false;
  }));
  // El intento ya está confirmado en disco antes de iniciar SMTP. No se reenvía un resultado incierto.
  if(enviar){boolean inicioSmtp=false;try{
   var mensaje=correo.createMimeMessage();var ayuda=new MimeMessageHelper(mensaje,true,StandardCharsets.UTF_8.name());
   ayuda.setFrom(remitente);ayuda.setTo(configuracion.exigirDestinatario());var reply=new InternetAddress(p.correo(),p.nombre(),StandardCharsets.UTF_8.name());reply.validate();ayuda.setReplyTo(reply);
   ayuda.setSubject("Nuevo mensaje desde la web de Villa Serena");var contexto=new Context();contexto.setVariable("nombre",p.nombre());contexto.setVariable("correo",p.correo());contexto.setVariable("mensaje",p.mensaje());contexto.setVariable("referencia",id.toString());
   ayuda.setText(plantillas.process("correo/contacto",contexto),true);ayuda.addInline("villa-serena-logo",new ClassPathResource("correo/villa-serena-logo.png"),"image/png");
   inicioSmtp=true;correo.send(mensaje);
   jdbc.update("UPDATE web_contact SET status='ENVIADO',sent_at=now(),updated_at=now(),error_code=NULL WHERE id=?",id);
  }catch(Exception e){boolean previo=!inicioSmtp;for(Throwable causa=e;causa!=null;causa=causa.getCause())if(causa instanceof MailAuthenticationException||causa.getClass().getSimpleName().equals("MailConnectException")||causa instanceof java.net.UnknownHostException||causa instanceof java.net.ConnectException)previo=true;
   jdbc.update("UPDATE web_contact SET status=?,error_code=?,updated_at=now() WHERE id=?",previo?"FALLIDO":"INCIERTO",previo?"SMTP_NO_CONECTADO":"SMTP_RESULTADO_INCIERTO",id);
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Contacto {}: {} ({})",id,previo?"FALLIDO":"INCIERTO",e.getClass().getSimpleName());
  }}
  return estado(id);
 }
 @GetMapping("/{id}") public Resultado estado(@PathVariable UUID id){
  jdbc.update("UPDATE web_contact SET status='INCIERTO',error_code='RESULTADO_PENDIENTE',updated_at=now() WHERE id=? AND status='ENVIANDO' AND updated_at<now()-interval '2 minutes'",id);
  var rows=jdbc.queryForList("SELECT status AS \"estado\" FROM web_contact WHERE id=?",String.class,id);if(rows.isEmpty())throw ApiException.noEncontrado();return new Resultado(id,rows.getFirst());
 }
}
