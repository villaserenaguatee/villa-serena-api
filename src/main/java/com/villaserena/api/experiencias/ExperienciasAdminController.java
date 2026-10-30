package com.villaserena.api.experiencias;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import static com.villaserena.api.experiencias.ExperienciasDto.*;
@RestController @RequestMapping("/api/v1/admin/experiencias") @PreAuthorize("hasRole('ADMIN')")
public class ExperienciasAdminController {
 private final ExperienciasService service;
 public ExperienciasAdminController(ExperienciasService service){this.service=service;}
 @GetMapping @Operation(operationId="experienciasAdmin") public List<Map<String,Object>> list(){return service.catalog(null);}
 @PostMapping @Operation(operationId="crearExperiencia") public Map<String,Object> create(@Valid @RequestBody Config p){return service.configure(null,p);}
 @PutMapping("/{id}") @Operation(operationId="editarExperiencia") public Map<String,Object> edit(@PathVariable long id,@Valid @RequestBody Config p){return service.configure(id,p);}
 @GetMapping("/{id}/horarios") @Operation(operationId="horariosExperienciaAdmin") public List<Map<String,Object>> slots(@PathVariable long id){return service.slots(id,null,null);}
 @PostMapping("/{id}/horarios") @Operation(operationId="crearHorarioExperiencia") public Map<String,Object> createSlot(@PathVariable long id,@Valid @RequestBody Slot p){return service.schedule(id,null,p);}
 @PutMapping("/{id}/horarios/{horarioId}") @Operation(operationId="editarHorarioExperiencia") public Map<String,Object> editSlot(@PathVariable long id,@PathVariable long horarioId,@Valid @RequestBody Slot p){return service.schedule(id,horarioId,p);}
}
