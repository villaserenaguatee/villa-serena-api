package com.villaserena.api.hotel.editorial;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/hotel/editorial/{idioma}")
@PreAuthorize("hasRole('ADMIN')")
public class EditorialAdminController {
    private final EditorialService editorial;
    public EditorialAdminController(EditorialService editorial) { this.editorial = editorial; }

    @GetMapping
    @Operation(operationId = "consultarBorradorEditorial", summary = "Consultar borrador editorial")
    public EditorialSitio consultar(@PathVariable String idioma) { return editorial.borrador(idioma); }

    @PutMapping
    @Operation(operationId = "guardarBorradorEditorial", summary = "Guardar borrador editorial")
    public EditorialSitio guardar(@PathVariable String idioma, @Valid @RequestBody GuardarEditorialPeticion peticion) {
        return editorial.guardar(idioma, peticion);
    }

    @PostMapping("/publicacion")
    @Operation(operationId = "publicarEditorial", summary = "Publicar borrador editorial")
    public EditorialSitio publicar(@PathVariable String idioma, @Valid @RequestBody PublicarEditorialPeticion peticion) {
        return editorial.publicar(idioma, peticion.revision());
    }
}
