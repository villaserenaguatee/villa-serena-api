package com.villaserena.api.hotel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Configuración general y fiscal del hotel (HU-ADM-08). */
@RestController
@RequestMapping("/api/v1/admin/hotel")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class HotelAdminController {

    private static final Logger log = LoggerFactory.getLogger(HotelAdminController.class);

    private final HotelService hotel;

    public HotelAdminController(HotelService hotel) {
        this.hotel = hotel;
    }

    @GetMapping
    @Operation(operationId = "consultarHotelAdmin", summary = "Consultar datos generales y fiscales")
    public ResponseEntity<HotelAdmin> consultar() {
        HotelAdmin actual = hotel.admin();
        return ResponseEntity.ok().eTag(GaleriaHotel.revision(actual.fotos())).body(actual);
    }

    @PostMapping("/inicializacion")
    @Operation(operationId = "inicializarHotelAdmin", summary = "Inicializar hotel sin sobrescribir datos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuración existente idéntica", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelAdmin.class))),
        @ApiResponse(responseCode = "201", description = "Configuración inicial creada", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HotelAdmin.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = com.villaserena.api.comun.ErrorRespuesta.class))),
        @ApiResponse(responseCode = "401", description = "Sesión requerida", content = @Content(schema = @Schema(implementation = com.villaserena.api.comun.ErrorRespuesta.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN", content = @Content(schema = @Schema(implementation = com.villaserena.api.comun.ErrorRespuesta.class))),
        @ApiResponse(responseCode = "409", description = "HOTEL_YA_CONFIGURADO: configuración existente distinta", content = @Content(schema = @Schema(implementation = com.villaserena.api.comun.ErrorRespuesta.class)))
    })
    public ResponseEntity<HotelAdmin> inicializar(@Valid @RequestBody InicializarHotelPeticion peticion) {
        HotelService.Inicializacion resultado = hotel.inicializar(peticion);
        log.info("Inicialización del hotel: creada={}, administradorId={}", resultado.creada(), UsuarioActual.id());
        return ResponseEntity.status(resultado.creada() ? 201 : 200).body(resultado.hotel());
    }

    @PutMapping
    @Operation(operationId = "editarHotelAdmin", summary = "Actualizar datos del hotel")
    public ResponseEntity<HotelAdmin> actualizar(@Valid @RequestBody HotelAdminPeticion peticion,
            @RequestHeader(value = "If-Match", required = false) String revisionGaleria) {
        HotelAdmin actualizado = hotel.actualizar(peticion, revisionGaleria);
        log.info("Configuración del hotel actualizada: administradorId={}", UsuarioActual.id());
        return ResponseEntity.ok().eTag(GaleriaHotel.revision(actualizado.fotos())).body(actualizado);
    }
}
