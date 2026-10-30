package com.villaserena.api.experiencias;
import java.util.*;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import static com.villaserena.api.experiencias.ExperienciasDto.*;
@RestController @RequestMapping("/api/v1/app/reservas/{codigo}/experiencias") @PreAuthorize("hasRole('HUESPED')")
public class ExperienciasHuespedController {
 private final ExperienciasService service;
 public ExperienciasHuespedController(ExperienciasService service){this.service=service;}
 @GetMapping @Operation(operationId="experienciasHuesped") public List<Map<String,Object>> catalog(@PathVariable String codigo){return service.catalog(codigo);}
 @GetMapping("/{id}/disponibilidad") @Operation(operationId="disponibilidadExperiencia") public List<Map<String,Object>> slots(@PathVariable String codigo,@PathVariable long id,@RequestParam(required=false) LocalDate fecha){return service.slots(id,codigo,fecha);}
 @GetMapping("/reservas") @Operation(operationId="misReservasExperiencias") public List<Map<String,Object>> bookings(@PathVariable String codigo){return service.bookings(codigo,null,null,null);}
 @PostMapping("/reservas") @Operation(operationId="reservarExperiencia") public Map<String,Object> book(@PathVariable String codigo,@Valid @RequestBody Booking p){return service.book(codigo,p);}
 @PostMapping("/reservas/{id}/cancelar") @Operation(operationId="cancelarExperiencia") public Map<String,Object> cancel(@PathVariable String codigo,@PathVariable long id,@Valid @RequestBody Cancel p){return service.cancel(codigo,id,p);}
 @GetMapping("/consultas") @Operation(operationId="misConsultasExperiencias") public List<Map<String,Object>> inquiries(@PathVariable String codigo){return service.inquiries(codigo);}
 @PostMapping("/{id}/consultas") @Operation(operationId="consultarExperiencia") public Map<String,Object> ask(@PathVariable String codigo,@PathVariable long id,@Valid @RequestBody Inquiry p){return service.ask(codigo,id,p);}
}
