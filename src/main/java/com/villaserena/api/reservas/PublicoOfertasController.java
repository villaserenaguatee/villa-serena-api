package com.villaserena.api.reservas;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import com.villaserena.api.reservas.dto.*;
import com.villaserena.api.reservas.pagos.PagoOnlineService;

@RestController
@RequestMapping("/api/v1/publico/ofertas")
public class PublicoOfertasController {
    private final CatalogoOfertas ofertas;private final IdempotenciaReservas idempotencia;private final PagoOnlineService pagos;private final Clock reloj; private final VerificacionCorreoReserva verificacion;
    public PublicoOfertasController(CatalogoOfertas ofertas,IdempotenciaReservas idempotencia,PagoOnlineService pagos,Clock reloj,VerificacionCorreoReserva verificacion){this.ofertas=ofertas;this.idempotencia=idempotencia;this.pagos=pagos;this.reloj=reloj;this.verificacion=verificacion;}
    @GetMapping public List<Map<String,Object>> catalogo(){return ofertas.catalogo();}
    @GetMapping("/disponibilidad") public List<Map<String,Object>> disponibilidad(@RequestParam LocalDate entrada,@RequestParam LocalDate salida,@RequestParam int huespedes){return ofertas.disponibles(entrada,salida,huespedes);}
    @PostMapping("/{slug}/reservas") @ResponseStatus(HttpStatus.CREATED)
    public ReservaWebCreada crear(@PathVariable String slug,@Valid @RequestBody ReservaWebPeticion p,@RequestHeader(name="Idempotency-Key",required=false)String clave,@RequestHeader(name="X-Booking-Draft",required=false)String draft,@RequestHeader(name="X-Booking-Email-Verification",required=false)String receipt){
        if(clave==null) throw com.villaserena.api.comun.ApiException.datosInvalidos("Identificador de solicitud obligatorio.",List.of());
        return idempotencia.ejecutar("publico-oferta",clave,Map.of("slug",slug,"reserva",p),ReservaWebCreada.class,()->{
            verificacion.consumir(p.huesped().correo(),draft,receipt);
            Reserva r=ofertas.crear(slug,p);
            return new ReservaWebCreada(r.getCodigo(),r.getEstado(),new TipoHabitacionReferencia(r.getTipoHabitacionId(),ofertas.catalogo().stream().filter(o->slug.equals(o.get("slug"))).findFirst().map(o->(String)o.get("name")).orElse("")),r.getFechaEntrada(),r.getFechaSalida(),r.noches(),r.getNumeroHuespedes(),r.getTotal(),OffsetDateTime.ofInstant(pagos.vencimientoPago(r),reloj.getZone()));
        });
    }
}
