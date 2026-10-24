package com.villaserena.api.catalogo.admin;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.catalogo.dto.Indicadores;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/indicadores")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin")
public class IndicadoresAdminController {

    private final IndicadoresAdminService servicio;

    public IndicadoresAdminController(IndicadoresAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(operationId = "indicadoresAdmin", summary = "Consultar indicadores básicos")
    public Indicadores consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return servicio.consultar(inicio, fin);
    }
}
