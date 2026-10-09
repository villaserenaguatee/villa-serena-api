package com.villaserena.api.reservas.dto;

import java.math.BigDecimal;
import java.util.List;

import com.villaserena.api.reservas.EstadoCuenta;
import com.villaserena.api.reservas.EstadoReserva;

/** Cuenta para el huésped en la app (HU-HUE-15). */
public record CuentaHuesped(String codigoReserva, EstadoReserva estadoReserva, EstadoCuenta estadoCuenta,
        String nombreHuesped, List<PrecioNoche> detalleNoches, BigDecimal totalCargosVigentes,
        BigDecimal totalPagosAprobados, BigDecimal saldo, Long facturaId, List<CargoCuentaHuesped> cargos,
        List<PagoCuentaHuesped> pagos) {

    public static CuentaHuesped de(CuentaRecepcion c) {
        return new CuentaHuesped(c.codigoReserva(), c.estadoReserva(), c.estadoCuenta(), c.nombreHuesped(),
                c.detalleNoches(), c.totalCargosVigentes(), c.totalPagosAprobados(), c.saldo(), c.facturaId(),
                c.cargos().stream().map(CargoCuentaHuesped::de).toList(),
                c.pagos().stream().map(PagoCuentaHuesped::de).toList());
    }
}
