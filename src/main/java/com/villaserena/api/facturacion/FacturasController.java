package com.villaserena.api.facturacion;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.villaserena.api.auth.TipoUsuario;
import com.villaserena.api.auth.UsuarioActual;
import com.villaserena.api.reservas.ReservaService;

/**
 * Consulta de facturas (HU-REC-15, HU-REC-16 y HU-HUE-16): Recepción cualquiera y el
 * huésped solo las suyas (una ajena responde 404). No hay endpoints para emitir,
 * modificar ni anular: la factura nace en el check-out y es inmutable.
 */
@RestController
public class FacturasController {

    private final FacturaConsultaService facturas;

    public FacturasController(FacturaConsultaService facturas) {
        this.facturas = facturas;
    }

    @GetMapping("/api/v1/facturas/{id}")
    @PreAuthorize("hasAnyRole('RECEPCION', 'HUESPED')")
    public FacturaDetalle detalle(@PathVariable long id) {
        comprobarAcceso(id);
        return facturas.detalle(id);
    }

    /** Enlace firmado de corta duración al PDF del bucket privado. */
    @GetMapping("/api/v1/facturas/{id}/pdf")
    @PreAuthorize("hasAnyRole('RECEPCION', 'HUESPED')")
    public DescargaFactura pdf(@PathVariable long id) {
        comprobarAcceso(id);
        return facturas.descarga(id);
    }

    @GetMapping("/api/v1/app/reservas/{codigo}/factura")
    @PreAuthorize("hasRole('HUESPED')")
    public FacturaDetalle deMiReserva(@PathVariable String codigo) {
        ReservaService.validarCodigo(codigo);
        return facturas.deReservaDelHuesped(codigo, UsuarioActual.id());
    }

    private void comprobarAcceso(long facturaId) {
        if (UsuarioActual.tipo() == TipoUsuario.HUESPED) {
            facturas.comprobarDuenoHuesped(facturaId, UsuarioActual.id());
        }
    }
}
