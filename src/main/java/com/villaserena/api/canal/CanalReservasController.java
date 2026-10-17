package com.villaserena.api.canal;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.canal.dto.ReservaCanalPeticion;
import com.villaserena.api.canal.dto.ReservaCanalRespuesta;
import com.villaserena.api.comun.ApiException;
import com.villaserena.api.comun.DetalleError;
import com.villaserena.api.reservas.CanalReserva;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * API que usan los canales externos (HU-CM-01). El canal simulado de la web
 * (HU-CM-03) llama a este mismo endpoint, con la clave del canal que elija.
 */
@RestController
@RequestMapping("/api/v1/canal")
public class CanalReservasController {

    public static final String ENCABEZADO_CODIGO = "X-Canal-Codigo";
    public static final String ENCABEZADO_CLAVE = "X-Canal-Clave";

    private final CanalAutenticacion autenticacion;
    private final CanalReservasService reservas;
    private final Validator validador;

    public CanalReservasController(CanalAutenticacion autenticacion, CanalReservasService reservas,
            Validator validador) {
        this.autenticacion = autenticacion;
        this.reservas = reservas;
        this.validador = validador;
    }

    /**
     * Recibe una reserva del canal: 201 si se creó, 200 si ese identificador
     * externo ya existía (RN-CM-004), 400 si los datos no cumplen las reglas, 401
     * si la clave no corresponde y 409 si no hay disponibilidad.
     * <p>
     * La petición no lleva {@code @Valid}: las anotaciones se revisan antes de
     * entrar al método, y entonces una clave incorrecta con datos incompletos
     * respondería 400. El contrato exige 401 en cuanto la clave no sirva, así que
     * primero se autentica y después se validan los datos, a mano.
     */
    @PostMapping("/reservas")
    public ResponseEntity<ReservaCanalRespuesta> crear(
            @RequestHeader(name = ENCABEZADO_CODIGO, required = false) String codigoCanal,
            @RequestHeader(name = ENCABEZADO_CLAVE, required = false) String claveCanal,
            @RequestBody ReservaCanalPeticion peticion) {
        CanalReserva canal = autenticacion.autenticar(codigoCanal, claveCanal);
        validar(peticion);
        CanalReservasService.Resultado resultado = reservas.recibir(canal, peticion);
        return ResponseEntity.status(resultado.creada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.reserva());
    }

    private void validar(ReservaCanalPeticion peticion) {
        Set<ConstraintViolation<ReservaCanalPeticion>> fallas = validador.validate(peticion);
        if (fallas.isEmpty()) {
            return;
        }
        List<DetalleError> detalles = fallas.stream()
                .map(f -> new DetalleError(f.getPropertyPath().toString(), f.getMessage()))
                .sorted(Comparator.comparing(DetalleError::campo))
                .toList();
        throw ApiException.datosInvalidos("Los datos enviados no son válidos.", detalles);
    }
}
