package com.villaserena.api.reservas;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/v1/publico/correo")
public class VerificacionCorreoReservaController {
    private final VerificacionCorreoReserva service;
    public VerificacionCorreoReservaController(VerificacionCorreoReserva service) { this.service=service; }
    public record Solicitud(@NotBlank @Email @Size(max=150) String correo,@NotNull UUID borrador,@Size(max=150) String nombre) { public Solicitud { correo=correo==null?null:correo.trim(); } }
    public record Verificar(@NotBlank @Email @Size(max=150) String correo,@NotNull UUID borrador,@NotBlank @Pattern(regexp="[0-9]{6}") String codigo) { public Verificar { correo=correo==null?null:correo.trim(); } }
    public record Google(@NotBlank @Email @Size(max=150) String correo,@NotNull UUID borrador,@NotBlank @Size(max=8192) String idToken) {}
    public record Actualizar(@NotBlank @Email @Size(max=150) String correo,@NotNull UUID borrador,@NotBlank @Email @Size(max=150) String anterior) {}
    @PostMapping("/actualizar") public Map<String,Object> actualizar(@Valid @RequestBody Actualizar p) { return service.actualizarCorreo(p.correo(),p.borrador(),p.anterior()); }
    @PostMapping("/solicitar") public Map<String,Object> solicitar(@Valid @RequestBody Solicitud p) { return service.solicitar(p.correo(),p.borrador(),p.nombre()); }
    @PostMapping("/verificar") public Map<String,Object> verificar(@Valid @RequestBody Verificar p) { return service.verificar(p.correo(),p.borrador(),p.codigo()); }
    @PostMapping("/estado") public Map<String,Object> estado(@Valid @RequestBody Solicitud p,@RequestHeader(name="X-Booking-Email-Verification",required=false) String receipt) { return service.informacion(p.correo(),p.borrador(),receipt); }
    @PostMapping("/google") public Map<String,Object> google(@Valid @RequestBody Google p) { return service.verificarGoogle(p.correo(),p.borrador(),p.idToken()); }
}
