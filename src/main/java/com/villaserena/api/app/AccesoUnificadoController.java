package com.villaserena.api.app;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.*;
import com.villaserena.api.comun.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** La identidad del intento evita contar doble un reenvío HTTP del mismo formulario. */
@RestController
@RequestMapping("/api/v1/app/acceso/iniciar-sesion")
public class AccesoUnificadoController {
 private final AccesoUnificadoService acceso;private final byte[] sal=UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
 private final ConcurrentHashMap<String,Guardado> intentos=new ConcurrentHashMap<>();
 private record Guardado(String huella,Instant expira,AccesoUnificadoService.Sesion sesion,ApiException error){}
 public record Peticion(@NotBlank @Email @Size(max=150)String correo,@NotBlank @Size(max=256)String credencial){}
 public AccesoUnificadoController(AccesoUnificadoService acceso){this.acceso=acceso;}
 private String huella(Peticion p){try{var hash=MessageDigest.getInstance("SHA-256");hash.update(sal);return HexFormat.of().formatHex(hash.digest((p.correo().trim().toLowerCase(Locale.ROOT)+"\u0000"+p.credencial()).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @PostMapping public AccesoUnificadoService.Sesion iniciar(@Valid @RequestBody Peticion p,@RequestHeader("Idempotency-Key")UUID clave){
  Instant ahora=Instant.now();intentos.entrySet().removeIf(e->e.getValue().expira().isBefore(ahora));
  if(intentos.size()>=10000)throw AccesoUnificadoService.invalido();String identidad=huella(p);
  Guardado g=intentos.computeIfAbsent(clave.toString(),k->{try{return new Guardado(identidad,ahora.plusSeconds(300),acceso.iniciar(p.correo(),p.credencial()),null);}catch(ApiException e){return new Guardado(identidad,ahora.plusSeconds(300),null,e);}});
  if(!MessageDigest.isEqual(identidad.getBytes(StandardCharsets.UTF_8),g.huella().getBytes(StandardCharsets.UTF_8)))throw AccesoUnificadoService.invalido();
  if(g.error()!=null)throw g.error();return g.sesion();
 }
}
