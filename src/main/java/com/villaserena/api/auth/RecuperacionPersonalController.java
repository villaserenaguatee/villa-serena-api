package com.villaserena.api.auth;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/api/v1/app/acceso/recuperacion")
public class RecuperacionPersonalController {
 private final RecuperacionPersonal service;
 public RecuperacionPersonalController(RecuperacionPersonal service){this.service=service;}
 public record Solicitud(@NotBlank @Email @Size(max=254) String correo,@NotNull UUID solicitud){}
 public record Confirmacion(@NotBlank @Size(max=100) String token,@NotBlank @Size(max=72) String contrasenaNueva,@NotBlank @Size(max=72) String confirmacion){}
 @PostMapping("/solicitar") public ResponseEntity<Void> solicitar(@Valid @RequestBody Solicitud r){service.solicitar(r.correo(),r.solicitud());return ResponseEntity.accepted().header("Cache-Control","no-store").build();}
 @PostMapping("/confirmar") public ResponseEntity<Void> confirmar(@Valid @RequestBody Confirmacion r){service.confirmar(r.token(),r.contrasenaNueva(),r.confirmacion());return ResponseEntity.noContent().header("Cache-Control","no-store").build();}
}
