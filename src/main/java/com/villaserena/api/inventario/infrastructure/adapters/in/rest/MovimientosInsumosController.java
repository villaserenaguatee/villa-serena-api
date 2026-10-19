package com.villaserena.api.inventario.infrastructure.adapters.in.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.inventario.application.MovimientosInsumosService;
import com.villaserena.api.inventario.domain.port.out.RegistroMovimientosInsumos.Registro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.ObjectMapper;

/** Conserva el JSON público; la adaptación tipada y el negocio quedan en el caso de uso. */
@RestController
@RequestMapping("/api/v1/gestion/movimientos")
@PreAuthorize("hasRole('ADMIN') or (hasRole('MANTENIMIENTO_LIMPIEZA') and @areas.mantenimiento())")
public class MovimientosInsumosController {
    private final MovimientosInsumosService movimientos;
    private final ObjectMapper json;

    public MovimientosInsumosController(MovimientosInsumosService movimientos, ObjectMapper json) {
        this.movimientos = movimientos;
        this.json = json;
    }

    public record GuardarMovimiento(@NotNull UUID id, @Min(0) long version, @NotNull Map<String, Object> datos) { }

    @GetMapping
    public List<Map<String, Object>> listar() {
        exigirAdministracion();
        return movimientos.listar().stream().map(MovimientosInsumosController::respuesta).toList();
    }

    @PostMapping
    public Map<String, Object> registrar(@Valid @RequestBody GuardarMovimiento peticion) {
        exigirAdministracion();
        if (json.writeValueAsString(peticion.datos()).length() > 64000) {
            throw ApiException.datosInvalidos("El registro es demasiado grande.", List.of());
        }
        return respuesta(movimientos.registrar(peticion.id(), peticion.version(), peticion.datos(), UsuarioActual.id()));
    }

    private static void exigirAdministracion() {
        if (!"ADMIN".equals(UsuarioActual.rol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Esta operación requiere Administración.");
        }
    }

    private static Map<String, Object> respuesta(Registro registro) {
        var datos = new LinkedHashMap<>(registro.datos());
        datos.put("id", registro.id().toString());
        datos.put("version", registro.version());
        return datos;
    }
}
