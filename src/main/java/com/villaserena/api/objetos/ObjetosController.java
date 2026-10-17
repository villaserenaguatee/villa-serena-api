package com.villaserena.api.objetos;

import java.time.OffsetDateTime;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/objetos")
@PreAuthorize("hasRole('RECEPCION')")
public class ObjetosController {
 private final ObjetosService service;
 public ObjetosController(ObjetosService service){this.service=service;}
 public record Nuevo(@NotBlank @Size(max=300) String descripcion,
   @NotBlank @Size(max=20) String habitacionNumero, @NotNull OffsetDateTime halladoEn,
   @Size(max=500) String notaInterna, @Size(max=200) String referenciaLocal,
   @Size(max=7000000) String fotoBase64){}
 public record Revision(@NotBlank @Pattern(regexp="VS-[A-Z0-9]{6}") String codigoReserva,
   @Positive long huespedId, @NotBlank @Email @Size(max=255) String correo,
   boolean incluirFoto, @Size(max=1000) String mensaje){}
 public record Confirmacion(@NotNull UUID revision, @AssertTrue boolean destinatarioConfirmado,
   @AssertTrue boolean mensajeConfirmado){}
 public record Reintento(@NotNull UUID revision, @Min(1) int intentos){}
 public record ReintentoIncierto(@NotNull UUID revision,@Min(1) int intentos,
   @NotBlank String codigoReserva,@Positive long huespedId,@NotBlank @Email String correo,
   @AssertTrue boolean destinatarioConfirmado,@AssertTrue boolean mensajeConfirmado,
   @AssertTrue boolean aceptoPosibleDuplicado){}
 @GetMapping public List<Map<String,Object>> listar(){return service.listar();}
 @PostMapping public ResponseEntity<Map<String,Object>> crear(@Valid @RequestBody Nuevo body){return ResponseEntity.status(201).body(service.crear(body));}
 @GetMapping("/{id}") public Map<String,Object> detalle(@PathVariable("id") long id){return service.detalle(id);}
 @GetMapping("/{id}/foto") public ResponseEntity<byte[]> foto(@PathVariable("id") long id){
  var photo=service.foto(id);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mime()))
   .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
   .header("Content-Security-Policy","default-src 'none'").body(photo.bytes());
 }
 @PostMapping("/{id}/previsualizar") public Map<String,Object> preview(@PathVariable("id") long id,@Valid @RequestBody Revision body){return service.previsualizar(id,body);}
 @PostMapping("/{id}/enviar") public ResponseEntity<Map<String,Object>> enviar(@PathVariable("id") long id,@Valid @RequestBody Confirmacion body){return ResponseEntity.accepted().body(service.enviar(id,body));}
 @PostMapping("/{id}/reintentar") public Map<String,Object> retry(@PathVariable("id") long id,@Valid @RequestBody Reintento body){return service.reintentar(id,body);}
 @PostMapping("/{id}/devolver") public Map<String,Object> devolver(@PathVariable("id") long id){return service.devolver(id);}
 @GetMapping("/{id}/aviso") public Map<String,Object> aviso(@PathVariable("id") long id){return service.vistaAviso(id);}
 @GetMapping("/{id}/aviso/foto") public ResponseEntity<byte[]> fotoAviso(@PathVariable("id") long id){
  var photo=service.fotoAviso(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mime()))
   .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(photo.bytes());
 }
 @PostMapping("/{id}/reintentar-incierto") public Map<String,Object> reintentarIncierto(@PathVariable("id") long id,@Valid @RequestBody ReintentoIncierto body){return service.reintentarIncierto(id,body);}
}
