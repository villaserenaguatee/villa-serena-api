package com.villaserena.api.hotel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;

import io.swagger.v3.oas.annotations.Operation;
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
    public HotelAdmin consultar() {
        return hotel.admin();
    }

    @PutMapping
    @Operation(operationId = "editarHotelAdmin", summary = "Actualizar datos del hotel")
    public HotelAdmin actualizar(@Valid @RequestBody HotelAdminPeticion peticion) {
        HotelAdmin actualizado = hotel.actualizar(peticion);
        log.info("Configuración del hotel actualizada: administradorId={}", UsuarioActual.id());
        return actualizado;
    }
}
