package com.villaserena.api.reservas;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.reservas.dto.EstadoReservaPublico;
import com.villaserena.api.reservas.pagos.PagoOnlineService;

/** Recupera el código con una prueba vigente del correo y borrador del intento original. */
@RestController
@RequestMapping("/api/v1/publico/reservas/intento")
public class ConsultaIntentoReservaController {
    public enum OrigenIntento { RESERVA, OFERTA }
    private final IdempotenciaReservas intentos;
    private final PagoOnlineService pagos;

    public ConsultaIntentoReservaController(IdempotenciaReservas intentos, PagoOnlineService pagos) {
        this.intentos = intentos;
        this.pagos = pagos;
    }

    @GetMapping
    public EstadoReservaPublico consultar(
            @RequestHeader("Idempotency-Key") String clave,
            @RequestHeader("X-Booking-Draft") String draft,
            @RequestHeader("X-Booking-Email-Verification") String comprobante,
            @RequestParam(defaultValue = "RESERVA") OrigenIntento origen) {
        String alcance = origen == OrigenIntento.OFERTA ? "publico-oferta" : "publico";
        return pagos.estadoPublico(intentos.recuperarCodigo(alcance, clave, draft, comprobante));
    }
}
