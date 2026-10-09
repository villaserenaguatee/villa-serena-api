package com.villaserena.api.huespedes;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.reservas.dto.HuespedVista;

import jakarta.validation.Valid;

/** Huéspedes de Recepción (HU-REC-01): registrar y buscar para asociarlos a una reserva. */
@RestController
@RequestMapping("/api/v1/huespedes")
@PreAuthorize("hasRole('RECEPCION')")
public class HuespedesController {

    private final HuespedService huespedes;

    public HuespedesController(HuespedService huespedes) {
        this.huespedes = huespedes;
    }

    /** 201 si es nuevo; 200 con el perfil existente si el correo ya estaba registrado. */
    @PostMapping
    public ResponseEntity<RegistroHuespedRespuesta> registrar(@Valid @RequestBody HuespedDatos datos) {
        RegistroHuespedRespuesta registro = huespedes.registrar(datos);
        return ResponseEntity.status(registro.yaExistia() ? HttpStatus.OK : HttpStatus.CREATED).body(registro);
    }

    @GetMapping
    public List<HuespedVista> buscar(@RequestParam String q) {
        return huespedes.buscar(q);
    }
}
