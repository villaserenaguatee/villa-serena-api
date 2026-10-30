package com.villaserena.api.experiencias;
import java.util.*;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import static com.villaserena.api.experiencias.ExperienciasDto.*;
@RestController @RequestMapping("/api/v1/experiencias") @PreAuthorize("hasRole('RECEPCION')")
public class ExperienciasRecepcionController {
 private final ExperienciasService service;
 public ExperienciasRecepcionController(ExperienciasService service){this.service=service;}
 @GetMapping("/reservas") @Operation(operationId="reservasExperienciasRecepcion") public List<Map<String,Object>> list(@RequestParam(required=false) LocalDate fecha,@RequestParam(required=false) Long experienciaId,@RequestParam(required=false) String estado){return service.bookings(null,fecha,experienciaId,estado);}
 @PostMapping("/reservas/{id}/decision") @Operation(operationId="decidirReservaExperiencia") public Map<String,Object> decide(@PathVariable long id,@Valid @RequestBody Decision p){return service.decide(id,p);}
 @GetMapping("/consultas") @Operation(operationId="consultasExperienciasRecepcion") public List<Map<String,Object>> inquiries(){return service.inquiries(null);}
 @PostMapping("/consultas/{id}/respuesta") @Operation(operationId="responderConsultaExperiencia") public Map<String,Object> answer(@PathVariable long id,@Valid @RequestBody Answer p){return service.answer(id,p);}
}
