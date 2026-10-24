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

/** Administración de la red Wi-Fi para huéspedes (HU-ADM-11). */
@RestController
@RequestMapping("/api/v1/admin/wifi")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class WifiAdminController {

    private static final Logger log = LoggerFactory.getLogger(WifiAdminController.class);

    private final AmenidadesWifiService servicio;

    public WifiAdminController(AmenidadesWifiService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "consultarWifiAdmin", summary = "Consultar configuración Wi-Fi")
    public Wifi consultar() {
        return servicio.consultarWifi();
    }

    @PutMapping
    @Operation(operationId = "configurarWifiAdmin", summary = "Configurar Wi-Fi para huéspedes")
    public Wifi configurar(@Valid @RequestBody Wifi peticion) {
        Wifi configurado = servicio.actualizarWifi(peticion);
        log.info("Configuración Wi-Fi actualizada: administradorId={}", UsuarioActual.id());
        return configurado;
    }
}
