package com.villaserena.api.facturacion;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.villaserena.api.notificaciones.ProveedorAdjuntos;
import com.villaserena.api.notificaciones.TipoNotificacion;

/** Adjunta el PDF al correo de la factura; se genera al enviar desde los datos guardados. */
@Component
public class AdjuntoFactura implements ProveedorAdjuntos {

    private final FacturaConsultaService facturas;

    public AdjuntoFactura(FacturaConsultaService facturas) {
        this.facturas = facturas;
    }

    @Override
    public boolean soporta(TipoNotificacion tipo) {
        return tipo == TipoNotificacion.CORREO_FACTURA;
    }

    @Override
    public List<Adjunto> adjuntos(Map<String, Object> datos) {
        FacturaDetalle factura = facturas.detalle(((Number) datos.get("facturaId")).longValue());
        return List.of(new Adjunto(FacturaConsultaService.nombrePdf(factura), facturas.contenidoPdf(factura.id()),
                "application/pdf"));
    }
}
