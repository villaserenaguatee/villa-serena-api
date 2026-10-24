package com.villaserena.api.hotel;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Consulta de amenidades activas y Wi-Fi para huéspedes con sesión (HU-HUE-18). */
@RestController
@RequestMapping("/api/v1/app")
@PreAuthorize("hasRole('HUESPED')")
@Tag(name = "app")
public class AmenidadesAppController {

    private final AmenidadesWifiService servicio;

    public AmenidadesAppController(AmenidadesWifiService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/amenidades")
    @Operation(operationId = "amenidadesHuesped", summary = "Consultar amenidades activas")
    public List<AmenidadApp> amenidades() {
        return servicio.listarHuesped();
    }

    @GetMapping("/wifi")
    @Operation(operationId = "wifiHuesped", summary = "Consultar Wi-Fi de huéspedes")
    public Wifi wifi() {
        return servicio.consultarWifi();
    }
}
