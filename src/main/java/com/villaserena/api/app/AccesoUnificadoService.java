package com.villaserena.api.app;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.villaserena.api.auth.*;
import com.villaserena.api.auth.dto.*;
import com.villaserena.api.huespedes.*;
import com.villaserena.api.config.PropiedadesVillaSerena;
import com.villaserena.api.comun.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class AccesoUnificadoService {
 private final JdbcTemplate jdbc;private final EmpleadoRepository empleados;private final HuespedRepository huespedes;private final CodigoOtpRepository codigos;
 private final PasswordEncoder encoder;private final Clock reloj;private final AuthService personal;private final AccesoHuespedService acceso;private final int maximos;private final Duration bloqueo;private final String dummy;
 public AccesoUnificadoService(JdbcTemplate jdbc,EmpleadoRepository empleados,HuespedRepository huespedes,CodigoOtpRepository codigos,PasswordEncoder encoder,Clock reloj,AuthService personal,AccesoHuespedService acceso,PropiedadesVillaSerena propiedades){
  this.jdbc=jdbc;this.empleados=empleados;this.huespedes=huespedes;this.codigos=codigos;this.encoder=encoder;this.reloj=reloj;this.personal=personal;this.acceso=acceso;maximos=propiedades.login().intentosMaximos();bloqueo=Duration.ofMinutes(propiedades.login().bloqueoMinutos());dummy=encoder.encode(UUID.randomUUID().toString());
 }
 public record Sesion(String accessToken,String refreshToken,String tipoToken,long expiraEn,EmpleadoSesion empleado){}
 private boolean coincide(String secret,String hash){try{return encoder.matches(secret,hash);}catch(IllegalArgumentException e){return false;}}
 public static ApiException invalido(){return new ApiException(HttpStatus.UNAUTHORIZED,"ACCESO_INVALIDO","No se pudo iniciar sesión. Revisa tus datos o solicita un nuevo código.");}
 @Transactional(noRollbackFor=ApiException.class)
 public Sesion iniciar(String correo,String secreto){
  String email=correo.trim().toLowerCase(Locale.ROOT);Instant ahora=reloj.instant();
  jdbc.update("INSERT INTO unified_access_control(email) VALUES (?) ON CONFLICT DO NOTHING",email);
  var control=jdbc.queryForMap("SELECT attempts AS \"intentos\",locked_until AS \"bloqueado_hasta\" FROM unified_access_control WHERE email=? FOR UPDATE",email);
  java.sql.Timestamp hasta=(java.sql.Timestamp)control.get("bloqueado_hasta");
  if(hasta!=null&&hasta.toInstant().isAfter(ahora))throw invalido();
  // Mismo orden de bloqueo para ambos perfiles. No se hace un login fallido por cada método.
  jdbc.queryForList("SELECT id  FROM employees WHERE lower(email)=? FOR UPDATE",email);
  jdbc.queryForList("SELECT id  FROM guests WHERE lower(email)=? FOR UPDATE",email);
  var empleado=empleados.findByCorreoIgnoreCase(email);var huesped=huespedes.findByCorreoIgnoreCase(email);
  boolean password=coincide(secreto,empleado.map(Empleado::getContrasenaHash).orElse(dummy));
  boolean otp=false;var vigentes=huesped.map(h->codigos.vigentes(h.getId(),ahora)).orElse(List.of());
  if(vigentes.isEmpty())coincide(secreto,dummy);else for(var codigo:vigentes)otp=coincide(secreto,codigo.getCodigoHash())|otp;
  password=password&&empleado.filter(e->e.estaActivo()&&!e.estaBloqueado(ahora)).isPresent();
  otp=otp&&huesped.filter(h->!h.otpBloqueado(ahora)).isPresent();
  // Una coincidencia doble no permite convertir un código de correo en acceso laboral.
  if(password&&otp)throw invalido();
  if(password){var s=personal.login(new LoginPeticion(email,secreto));limpiar(email);return new Sesion(s.accessToken(),s.refreshToken(),s.tipoToken(),s.expiraEn(),s.empleado());}
  if(otp){var s=acceso.verificarCodigo(email,secreto);limpiar(email);return new Sesion(s.accessToken(),s.refreshToken(),s.tipoToken(),s.expiraEn(),null);}
  int intentos=hasta!=null?1:((Number)control.get("intentos")).intValue()+1;
  jdbc.update("UPDATE unified_access_control SET attempts=?,locked_until=? WHERE email=?",intentos>=maximos?0:intentos,intentos>=maximos?java.sql.Timestamp.from(ahora.plus(bloqueo)):null,email);
  throw invalido();
 }
 private void limpiar(String email){jdbc.update("UPDATE unified_access_control SET attempts=0,locked_until=NULL WHERE email=?",email);}
}
