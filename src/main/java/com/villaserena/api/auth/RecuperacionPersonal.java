package com.villaserena.api.auth;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.notificaciones.*;

@Service
public class RecuperacionPersonal {
 private static final SecureRandom RANDOM=new SecureRandom();
 private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final TokenService tokens;
 private final Clock clock; private final OutboxService outbox; private final String web;
 public RecuperacionPersonal(JdbcTemplate jdbc,PasswordEncoder encoder,TokenService tokens,Clock clock,OutboxService outbox,PropiedadesVillaSerena config){
  this.jdbc=jdbc;this.encoder=encoder;this.tokens=tokens;this.clock=clock;this.outbox=outbox;this.web=config.webUrl().replaceAll("/+$","");
 }
 static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"ENLACE_INVALIDO","El enlace no es válido o ha vencido. Solicita uno nuevo.");}
 @Transactional public void solicitar(String address,UUID request){
  String email=address.trim().toLowerCase(Locale.ROOT),fingerprint=hash(email);Instant now=clock.instant();
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","recuperacion:"+fingerprint);
  // Repeating the same browser request never queues another message, even after a network interruption.
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_recoveries WHERE id=?)",Boolean.class,request)))return;
  int recent=jdbc.queryForObject("SELECT count(*)  FROM staff_recoveries WHERE email_hash=? AND created_at>?",Integer.class,fingerprint,Timestamp.from(now.minusSeconds(900)));
  if(recent>=3)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RECUPERACION_LIMITE","Espera antes de solicitar otro enlace.");
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_recoveries WHERE email_hash=? AND created_at>?)",Boolean.class,fingerprint,Timestamp.from(now.minusSeconds(60)))))return;
  var employee=jdbc.queryForList("SELECT id,full_name AS \"nombre_completo\",email AS \"correo\" FROM employees WHERE lower(email)=? AND status='ACTIVO'",email);
  Long employeeId=null,outboxId=null;String digest=null;
  if(!employee.isEmpty()){
   var e=employee.getFirst();employeeId=((Number)e.get("id")).longValue();
   // A new request invalidates older unused links for this employee.
   jdbc.update("UPDATE staff_recoveries SET used_at=? WHERE employee_id=? AND used_at IS NULL",Timestamp.from(now),employeeId);
   byte[] random=new byte[32];RANDOM.nextBytes(random);String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(random);digest=hash(secret);
   // Fragment keeps the bearer secret out of HTTP URLs, referrers and server access logs.
   String url=web+"/restablecer-contrasena#token="+secret;
   outboxId=outbox.encolar(TipoNotificacion.CORREO_OTP,e.get("correo").toString(),Map.of("proposito","RECUPERAR_PERSONAL","nombreHuesped",e.get("nombre_completo"),"recuperacionUrl",url)).getId();
  }
  jdbc.update("INSERT INTO staff_recoveries(id,email_hash,employee_id,token_hash,created_at,expires_at,outbox_id) VALUES(?,?,?,?,?,?,?)",request,fingerprint,employeeId,digest,Timestamp.from(now),Timestamp.from(now.plusSeconds(900)),outboxId);
 }
 @Transactional public void confirmar(String token,String password,String confirmation){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
  if(password==null||password.getBytes(StandardCharsets.UTF_8).length>72||password.length()<8||password.chars().noneMatch(Character::isLetter)||password.chars().noneMatch(Character::isDigit)||!password.equals(confirmation))
   throw ApiException.datosInvalidos("Usa al menos 8 caracteres, una letra y un número; máximo 72 bytes. Ambas contraseñas deben coincidir.",List.of());
  var rows=jdbc.queryForList("SELECT id,email_hash AS \"correo_hash\",employee_id AS \"empleado_id\",token_hash,created_at AS \"creado_en\",expires_at AS \"expira_en\",used_at AS \"usado_en\",outbox_id FROM staff_recoveries WHERE token_hash=? FOR UPDATE",hash(token));Instant now=clock.instant();
  if(rows.isEmpty())throw invalid();var r=rows.getFirst();
  if(r.get("usado_en")!=null||!((Timestamp)r.get("expira_en")).toInstant().isAfter(now)||r.get("empleado_id")==null)throw invalid();
  long id=((Number)r.get("empleado_id")).longValue();
  var employees=jdbc.queryForList("SELECT password_hash AS \"contrasena_hash\",status AS \"estado\" FROM employees WHERE id=? FOR UPDATE",id);
  if(employees.isEmpty()||!"ACTIVO".equals(employees.getFirst().get("estado")))throw invalid();
  if(encoder.matches(password,employees.getFirst().get("contrasena_hash").toString()))throw ApiException.datosInvalidos("La nueva contraseña debe ser distinta de la actual.",List.of());
  jdbc.update("UPDATE employees SET password_hash=?,must_change_password=false,failed_attempts=0,locked_until=NULL,access_revoked_at=? WHERE id=?",encoder.encode(password),Timestamp.from(now),id);
  jdbc.update("UPDATE staff_recoveries SET used_at=? WHERE employee_id=? AND used_at IS NULL",Timestamp.from(now),id);
  jdbc.update("UPDATE unified_access_control SET attempts=0,locked_until=NULL WHERE email=(SELECT lower(email) FROM employees WHERE id=?)",id);
  tokens.revocarTodosDelEmpleado(id);
 }
}
