package com.villaserena.api.hotel.editorial;

import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/publico/hotel/editorial/{idioma}")
public class EditorialPublicoController {
    private final EditorialService editorial;
    public EditorialPublicoController(EditorialService editorial) { this.editorial = editorial; }

    @GetMapping
    @Operation(operationId = "consultarEditorialPublicado", summary = "Consultar contenido editorial publicado")
    public EditorialSitio consultar(@PathVariable String idioma) { return editorial.publicado(idioma); }
}
