package com.villaserena.api.notificaciones;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
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
 private final JdbcTemplate jdbc;private final TransactionTemplate transaccion;private final JavaMailSender correo;private final TemplateEngine plantillas;private final String remitente,destinatario;
 public ContactoPublicoController(JdbcTemplate jdbc,PlatformTransactionManager tm,JavaMailSender correo,TemplateEngine plantillas,PropiedadesVillaSerena propiedades,@Value("${villa-serena.contacto.destinatario:hotelvillaserena.gt@gmail.com}")String destinatario){this.jdbc=jdbc;transaccion=new TransactionTemplate(tm);this.correo=correo;this.plantillas=plantillas;remitente=propiedades.notificaciones().remitente();this.destinatario=destinatario;}
 public record Peticion(@NotBlank @Size(max=150) @Pattern(regexp="[^\\r\\n]+")String nombre,@NotBlank @Email @Size(max=150) @Pattern(regexp="[^\\r\\n]+")String correo,@NotBlank @Size(max=5000)String mensaje){}
 public record Resultado(UUID id,String estado){}
 private String huella(Peticion p){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((p.nombre()+"\u0000"+p.correo()+"\u0000"+p.mensaje()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @PostMapping public Resultado enviar(@Valid @RequestBody Peticion entrada,@RequestHeader("Idempotency-Key")UUID id){
  var p=new Peticion(entrada.nombre().trim(),entrada.correo().trim(),entrada.mensaje().trim());String hash=huella(p);
  boolean enviar=Boolean.TRUE.equals(transaccion.execute(status->{
   int nuevo=jdbc.update("INSERT INTO contacto_web(id,huella,nombre,correo,mensaje,estado) VALUES (?,?,?,?,?,'ENVIANDO') ON CONFLICT DO NOTHING",id,hash,p.nombre(),p.correo(),p.mensaje());
   var r=jdbc.queryForMap("SELECT huella,estado FROM contacto_web WHERE id=? FOR UPDATE",id);
   if(!hash.equals(r.get("huella")))throw ApiException.conflicto("CONTACTO_DATOS_DISTINTOS","El intento anterior corresponde a otro mensaje.");
   if(nuevo==1)return true;
   if("FALLIDO".equals(r.get("estado"))){jdbc.update("UPDATE contacto_web SET estado='ENVIANDO',intentos=intentos+1,error_codigo=NULL,actualizado_en=now() WHERE id=?",id);return true;}
   return false;
  }));
  // El intento ya está confirmado en disco antes de iniciar SMTP. No se reenvía un resultado incierto.
  if(enviar){boolean inicioSmtp=false;try{
   var mensaje=correo.createMimeMessage();var ayuda=new MimeMessageHelper(mensaje,true,StandardCharsets.UTF_8.name());
   ayuda.setFrom(remitente);ayuda.setTo(destinatario);var reply=new InternetAddress(p.correo(),p.nombre(),StandardCharsets.UTF_8.name());reply.validate();ayuda.setReplyTo(reply);
   ayuda.setSubject("Nuevo mensaje desde la web de Villa Serena");var contexto=new Context();contexto.setVariable("nombre",p.nombre());contexto.setVariable("correo",p.correo());contexto.setVariable("mensaje",p.mensaje());contexto.setVariable("referencia",id.toString());
   ayuda.setText(plantillas.process("correo/contacto",contexto),true);ayuda.addInline("villa-serena-logo",new ClassPathResource("correo/villa-serena-logo.png"),"image/png");
   inicioSmtp=true;correo.send(mensaje);
   jdbc.update("UPDATE contacto_web SET estado='ENVIADO',enviado_en=now(),actualizado_en=now(),error_codigo=NULL WHERE id=?",id);
  }catch(Exception e){boolean previo=!inicioSmtp;for(Throwable causa=e;causa!=null;causa=causa.getCause())if(causa instanceof MailAuthenticationException||causa.getClass().getSimpleName().equals("MailConnectException")||causa instanceof java.net.UnknownHostException)previo=true;
   jdbc.update("UPDATE contacto_web SET estado=?,error_codigo=?,actualizado_en=now() WHERE id=?",previo?"FALLIDO":"INCIERTO",previo?"SMTP_NO_CONECTADO":"SMTP_RESULTADO_INCIERTO",id);
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Contacto {}: {} ({})",id,previo?"FALLIDO":"INCIERTO",e.getClass().getSimpleName());
  }}
  return estado(id);
 }
 @GetMapping("/{id}") public Resultado estado(@PathVariable UUID id){
  jdbc.update("UPDATE contacto_web SET estado='INCIERTO',error_codigo='RESULTADO_PENDIENTE',actualizado_en=now() WHERE id=? AND estado='ENVIANDO' AND actualizado_en<now()-interval '2 minutes'",id);
  var rows=jdbc.queryForList("SELECT estado FROM contacto_web WHERE id=?",String.class,id);if(rows.isEmpty())throw ApiException.noEncontrado();return new Resultado(id,rows.getFirst());
 }
}
